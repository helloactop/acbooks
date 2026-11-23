import re
from typing import cast
from urllib.error import HTTPError
from urllib.parse import parse_qs, quote, urljoin, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

from bs4 import BeautifulSoup
from bs4.element import NavigableString, Tag
from bs4.filter import SoupStrainer

from app.core.errors import BusinessError, BusinessErrorCode
from app.modules.books.normalization import normalize_isbn, publication_date_only
from app.modules.metadata.extract import _authors, _text
from app.modules.metadata.schemas import Candidate

SEARCH_ORIGIN = "https://www.douban.com"
BOOK_ORIGIN = "https://book.douban.com"
SUBJECT_PATH = re.compile(r"/subject/([0-9]{1,20})/?")
COVER_URL = re.compile(
    r"https://img[0-9]?\.doubanio\.com/view/(?:subject|photo)/(?:s|m|l)/public/"
    r"[A-Za-z0-9_.-]+\.(?:jpe?g|png|webp)",
)
MAX_HTML_BYTES = 2 * 1024 * 1024


def _subject_id(url: str) -> str | None:
    try:
        parsed = urlsplit(url)
    except ValueError:
        return None
    if parsed.scheme in {"http", "https"} and parsed.netloc.lower() == "book.douban.com":
        if match := SUBJECT_PATH.fullmatch(parsed.path):
            return match[1]
    return None


def parse_search(html: str) -> list[str]:
    subjects: dict[str, None] = {}
    for link in BeautifulSoup(html, "html.parser").select("a.nbg"):
        href = link.get("data-href") or link.get("href")
        if not isinstance(href, str):
            continue
        try:
            parsed = urlsplit(urljoin(SEARCH_ORIGIN, href))
            if parsed.netloc.lower() == "www.douban.com" and parsed.path == "/link2/":
                href = parse_qs(parsed.query).get("url", [""])[0]
        except ValueError:
            continue
        if subject := _subject_id(href):
            subjects[subject] = None
            if len(subjects) == 5:
                break
    return list(subjects)


def _info_values(soup: BeautifulSoup) -> tuple[dict[str, str], dict[str, list[str]]]:
    values: dict[str, str] = {}
    people: dict[str, list[str]] = {"作者": [], "译者": []}
    for label in soup.select("#info span.pl"):
        name = label.get_text(strip=True).rstrip(":：").strip()
        pieces: list[str] = []
        names: list[str] = []
        for node in label.next_siblings:
            if isinstance(node, Tag):
                if (
                    node.name == "br"
                    or "pl" in (node.get("class") or [])
                    or node.select_one("span.pl")
                ):
                    break
                links = [node] if node.name == "a" else node.find_all("a")
                names.extend(link.get_text(" ", strip=True) for link in links)
                pieces.append(node.get_text(" ", strip=True))
            elif isinstance(node, NavigableString):
                pieces.append(str(node))
        value = "".join(pieces).lstrip(" :：\t\r\n").strip()
        if name in people:
            people[name].extend(names or re.split(r"[、/;；&]+", value))
        elif value:
            values[name] = value
    return values, people


def _summary(soup: BeautifulSoup) -> str | None:
    intros = soup.select("#link-report .intro")
    if not intros:
        return None
    intro = intros[-1]
    for unwanted in intro.select("script, style"):
        unwanted.decompose()
    for line in intro.select("br"):
        line.replace_with("\n")
    for paragraph in intro.find_all("p"):
        paragraph.append("\n")
    return _text(re.sub(r"\n{3,}", "\n\n", intro.get_text()), 1000, multiline=True)


def parse_book(html: str, subject: str) -> Candidate | None:
    soup = BeautifulSoup(html, "html.parser")
    heading = soup.select_one('span[property="v:itemreviewed"]')
    title = _text(heading.get_text() if heading else None, 500)
    if title is None:
        return None
    values, people = _info_values(soup)
    publication_date = _text(
        values.get("出版年") or values.get("出版日期") or values.get("出版时间"), 80
    )
    if publication_date is None:
        date_tag = soup.select_one(
            '#info [property="v:datePublished"], #info [itemprop="datePublished"]'
        )
        if date_tag is not None:
            publication_date = _text(date_tag.get_text(strip=True) or date_tag.get("content"), 80)
    subtitle = _text(values.get("副标题"), 500)
    if subtitle and subtitle != title:
        title = _text(f"{title}: {subtitle}", 500) or title
    cover = soup.select_one("a.nbg")
    cover_url = cover.get("href") if cover else None
    average = soup.select_one('strong[property="v:average"]')
    rating_text = average.get_text(strip=True) if average else ""
    rating = float(rating_text) if re.fullmatch(r"[0-9]{1,2}(?:\.[0-9]{1,3})?", rating_text) else 0
    criteria = re.search(r"""\bcriteria\s*=\s*(['"])(.*?)\1""", html)
    tags = (
        list(_authors([tag[2:] for tag in criteria[2].split("|") if tag.startswith("7:")]))[:8]
        if criteria
        else []
    )
    return Candidate(
        title=title,
        authors=list(_authors(people["作者"])),
        translators=list(_authors(people["译者"])),
        series=_text(values.get("丛书"), 200),
        description=_summary(soup),
        isbn=normalize_isbn(values.get("ISBN", "")),
        tags=tags,
        douban_id=subject,
        douban_cover_url=(
            cover_url if isinstance(cover_url, str) and COVER_URL.fullmatch(cover_url) else None
        ),
        publisher=_text(values.get("出版社"), 200),
        publication_date=publication_date_only(publication_date) if publication_date else None,
        rating=round(rating, 1) if 0 < rating <= 10 else None,
    )


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args: object, **kwargs: object) -> None:
        return None


def _read(url: str, limit: int, *, cover: bool = False) -> bytes | str | None:
    headers = {"User-Agent": "acbooks"}
    if cover:
        headers["Referer"] = BOOK_ORIGIN + "/"
    try:
        with build_opener(_NoRedirect()).open(Request(url, headers=headers), timeout=5) as response:
            body = cast(bytes, response.read(limit + 1))
    except HTTPError as error:
        try:
            if error.code in {301, 302, 303, 307, 308} and not cover:
                return error.headers.get("Location")
            if error.code == 404:
                return None
        finally:
            error.close()
        raise BusinessError(BusinessErrorCode.METADATA_UNAVAILABLE) from error
    except OSError as error:
        raise BusinessError(BusinessErrorCode.METADATA_UNAVAILABLE) from error
    if len(body) > limit:
        raise BusinessError(BusinessErrorCode.METADATA_UNAVAILABLE)
    return body


def download_cover(url: str, limit: int) -> bytes | None:
    if not COVER_URL.fullmatch(url):
        return None
    result = _read(url, limit, cover=True)
    return result if isinstance(result, bytes) else None


def _page(url: str) -> str:
    response = _read(url, MAX_HTML_BYTES)
    if response is None:
        return ""
    if isinstance(response, str):
        raise BusinessError(BusinessErrorCode.METADATA_UNAVAILABLE)
    html = response.decode("utf-8", errors="replace")
    title = BeautifulSoup(html, "html.parser", parse_only=SoupStrainer("title")).title
    if title and "禁止访问" in title.get_text():
        raise BusinessError(BusinessErrorCode.METADATA_UNAVAILABLE)
    return html


def _book(subject: str) -> Candidate | None:
    return parse_book(_page(f"{BOOK_ORIGIN}/subject/{subject}/"), subject)


def _isbn_key(value: str | None) -> str | None:
    if value and len(value) == 13 and value.startswith("978"):
        return value[3:-1]
    if value and len(value) == 10:
        return value[:-1]
    return value


def search(query: str) -> list[Candidate]:
    isbn = normalize_isbn(query)
    if isbn:
        path = f"{BOOK_ORIGIN}/isbn/{quote(isbn, safe='')}/"
        redirected = _read(path, MAX_HTML_BYTES)
        if isinstance(redirected, str) and (subject := _subject_id(urljoin(path, redirected))):
            if (candidate := _book(subject)) and (
                candidate.isbn is None or _isbn_key(candidate.isbn) == _isbn_key(isbn)
            ):
                return [candidate]
    html = _page(f"{SEARCH_ORIGIN}/search?cat=1001&q={quote(query, safe='')}")
    candidates = []
    failure = None
    for subject in parse_search(html):
        try:
            if candidate := _book(subject):
                candidates.append(candidate)
        except BusinessError as error:
            failure = error
    if not candidates and failure is not None:
        raise failure
    if isbn:
        exact = [book for book in candidates if _isbn_key(book.isbn) == _isbn_key(isbn)]
        if exact:
            return exact[:1]
        return candidates if len(candidates) == 1 and candidates[0].isbn is None else []
    return candidates
