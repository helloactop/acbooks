from pathlib import Path
from urllib.parse import quote

import pytest
from fastapi.testclient import TestClient
from test_book_management import JPEG, management
from test_setup import challenge

from app.modules.metadata import douban

__all__ = ["management"]

COVER = "https://img9.doubanio.com/view/subject/l/public/s29053580.jpg"
BOOK = f"""
<span property="v:itemreviewed">三体</span>
<a class="nbg" href="{COVER}">封面</a>
<div id="info">
  <span class="pl">作者:</span> <a>刘慈欣</a>
  <span class="pl">译者:</span> 译者甲 / 译者乙<br>
  <span class="pl">出版社:</span> 重庆出版社<br>
  <span class="pl">出版年:</span> 2008-01<br>
  <span class="pl">ISBN:</span> 9787536692930<br>
</div>
<strong property="v:average">9.3</strong>
<div id="link-report">
  <div class="intro"><p>短版</p></div>
  <div class="intro"><p>完整版 &amp; 原文<br>第二行<script>drop()</script></p></div>
</div>
<script>var criteria = '7:科幻|2:忽略|7:小说';</script>
"""


def test_extracts_book_fields_without_crossing_person_labels() -> None:
    result = douban.parse_book(BOOK, "2567698")
    assert result is not None
    assert result.title == "三体"
    assert result.authors == ["刘慈欣"]
    assert result.translators == ["译者甲", "译者乙"]
    assert result.publisher == "重庆出版社"
    assert result.publication_date == "2008-01"
    assert result.isbn == "9787536692930"
    assert result.description == "完整版 & 原文\n第二行"
    assert result.rating == 9.3
    assert result.tags == ["科幻", "小说"]
    assert result.douban_cover_url == COVER
    assert douban.parse_book("<html>无书名</html>", "1") is None


@pytest.mark.parametrize(
    ("label", "value", "expected"),
    [
        ("出版年", "2008", "2008"),
        ("出版日期", "2008-1", "2008-1"),
        ("出版时间", "2008年1月", "2008年1月"),
        ("出版年", "2008-01-12T00:00:00.000+08:00", "2008-01-12"),
    ],
)
def test_publication_date_preserves_douban_precision(label: str, value: str, expected: str) -> None:
    html = BOOK.replace("出版年:</span> 2008-01", f"{label}:</span> {value}")
    result = douban.parse_book(html, "2567698")
    assert result is not None
    assert result.publication_date == expected


def test_search_page_unwraps_links_deduplicates_and_excludes_other_hosts() -> None:
    direct = f"{douban.BOOK_ORIGIN}/subject/2567698/"
    wrapped = f"/link2/?url={quote(direct, safe='')}"
    html = f"""
      <a class="nbg" href="{wrapped}"></a>
      <a class="nbg" data-href="{direct}"></a>
      <a class="nbg" href="https://movie.douban.com/subject/1/"></a>
      <a class="nbg" href="https://fake.example/book.douban.com/subject/2/"></a>
    """
    assert douban.parse_search(html) == ["2567698"]


def test_isbn_search_prefers_exact_edition(monkeypatch: pytest.MonkeyPatch) -> None:
    def read(url: str, limit: int, *, cover: bool = False) -> bytes | str:
        assert limit > 0 and not cover
        if "/isbn/" in url:
            return "/subject/100/"
        if "/search?" in url:
            subjects = (100, 2567698)
            return "".join(
                f'<a class="nbg" href="{douban.BOOK_ORIGIN}/subject/{subject}/"></a>'
                for subject in subjects
            ).encode()
        if "/subject/100/" in url:
            return BOOK.replace("9787536692930", "9780306406157").encode()
        assert url == f"{douban.BOOK_ORIGIN}/subject/2567698/"
        return BOOK.encode()

    monkeypatch.setattr(douban, "_read", read)
    assert [book.douban_id for book in douban.search("7536692935")] == ["2567698"]
    assert douban.search("9780140328721") == []


def test_douban_candidate_api_and_fill_preserve_existing_fields(
    management: tuple[TestClient, Path, Path],
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client, _, _ = management
    candidate = douban.parse_book(BOOK, "2567698")
    assert candidate is not None
    monkeypatch.setattr(douban, "search", lambda q: [candidate] if q == "三体" else [])

    def read_cover(url: str, limit: int, *, cover: bool = False) -> bytes:
        assert url == COVER and cover and limit > len(JPEG)
        return JPEG

    monkeypatch.setattr(douban, "_read", read_cover)
    found = client.get("/api/v1/metadata/candidates", params={"q": "三体", "source": "douban"})
    assert found.status_code == 200
    result = found.json()[0]
    assert result["doubanId"] == "2567698"
    assert result["publicationDate"] == "2008-01"
    book = client.post(
        "/api/v1/books",
        json={"title": "手动书名", "publicationDate": "2008-01-01T00:00:00Z"},
        headers=challenge(client),
    )
    assert book.status_code == 201
    path = f"/api/v1/books/{book.json()['id']}"
    filled = client.post(f"{path}/metadata", json=result, headers=challenge(client))
    assert filled.json() == {
        "fields": [
            "translators", "authors", "description", "isbn", "cover", "tags", "publicationDate"
        ],
    }
    detail = client.get(path).json()
    assert detail["title"] == "手动书名"
    assert detail["publicationDate"] == "2008-01"
    assert detail["isbn"] == "9787536692930"
    assert set(detail["translators"]) == {"译者甲", "译者乙"}
    assert set(detail["tags"]) == {"科幻", "小说"}
    second = client.post(
        f"{path}/metadata", json={**result, "tags": ["其它标签"]}, headers=challenge(client)
    )
    assert second.json() == {"fields": []}
    assert set(client.get(path).json()["tags"]) == {"科幻", "小说"}
    assert client.get(f"{path}/cover").content == JPEG
    assert douban.download_cover("https://img9.doubanio.com.fake.example/s.jpg", 100) is None
