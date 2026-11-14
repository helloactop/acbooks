from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from unicodedata import category
from urllib.parse import unquote, urlsplit
from xml.etree.ElementTree import Element
from zipfile import ZipFile

from defusedxml.ElementTree import fromstring
from pypdf import PdfReader
from pypdf.generic import StreamObject

from app.modules.books.normalization import normalize_isbn
from app.modules.books.schemas import BookCreate
from app.modules.books.service import MAX_COVER_BYTES

DC = "{http://purl.org/dc/elements/1.1/}"
XML_LIMIT = 1024 * 1024


@dataclass(frozen=True)
class EmbeddedMetadata:
    title: str | None = None
    authors: tuple[str, ...] = ()
    translators: tuple[str, ...] = ()
    series: str | None = None
    description: str | None = None
    isbn: str | None = None
    cover: bytes | None = None

    def book(self, filename: str) -> BookCreate:
        return BookCreate(
            title=self.title or _text(Path(filename).stem, 500) or "Untitled",
            authors=list(self.authors),
            translators=list(self.translators),
            series=self.series,
            description=self.description,
            isbn=self.isbn,
        )


def _text(value: object, limit: int, *, multiline: bool = False) -> str | None:
    if not isinstance(value, str):
        return None
    value = "".join(
        char for char in value if char in "\n\r\t" or not category(char).startswith("C")
    )
    if not multiline:
        value = " ".join(value.split())
    return value.strip()[:limit].strip() or None


def _isbn(value: object) -> str | None:
    if not isinstance(value, str):
        return None
    value = value.strip()
    for prefix in ("urn:isbn:", "isbn:", "isbn "):
        if value.lower().startswith(prefix):
            value = value[len(prefix) :]
            break
    return normalize_isbn(value)


def _authors(values: list[str]) -> tuple[str, ...]:
    return tuple(name for value in values if (name := _text(value, 200)))[:20]


def _member(archive: ZipFile, name: str, limit: int) -> bytes:
    info = archive.getinfo(name)
    if info.file_size > limit:
        raise ValueError("Archive member exceeds the metadata limit")
    with archive.open(info) as stream:
        data = stream.read(limit + 1)
    if len(data) > limit:
        raise ValueError("Archive member exceeds the metadata limit")
    return data


def _local_member(base: PurePosixPath, href: str) -> str:
    url = urlsplit(href)
    path = PurePosixPath(unquote(url.path))
    if url.scheme or url.netloc or path.is_absolute() or "\\" in str(path):
        raise ValueError("Only local EPUB resources are supported")
    parts: list[str] = []
    for part in (base / path).parts:
        if part == "..":
            if not parts:
                raise ValueError("Resource escapes EPUB archive")
            parts.pop()
        else:
            parts.append(part)
    return "/".join(parts)


def _values(node: Element, name: str) -> list[str]:
    return ["".join(item.itertext()) for item in node.findall(DC + name)]


def _epub_cover(archive: ZipFile, package: Element, opf: str) -> bytes | None:
    legacy = package.find("{*}metadata/{*}meta[@name='cover']")
    legacy_id = legacy.get("content") if legacy is not None else None
    for item in package.findall("{*}manifest/{*}item"):
        if "cover-image" not in item.get("properties", "").split() and (
            legacy_id is None or item.get("id") != legacy_id
        ):
            continue
        try:
            member = _local_member(PurePosixPath(opf).parent, item.get("href", ""))
            return _member(archive, member, MAX_COVER_BYTES)
        except Exception:  # noqa: BLE001 - optional cover cannot invalidate other metadata
            continue
    return None


def _epub(path: Path) -> EmbeddedMetadata:
    with ZipFile(path) as archive:
        container = fromstring(_member(archive, "META-INF/container.xml", XML_LIMIT))
        rootfile = container.find("{*}rootfiles/{*}rootfile")
        if rootfile is None:
            return EmbeddedMetadata()
        opf = _local_member(PurePosixPath("."), rootfile.get("full-path", ""))
        package = fromstring(_member(archive, opf, XML_LIMIT))
        metadata = package.find("{*}metadata")
        if metadata is None:
            return EmbeddedMetadata()
        return EmbeddedMetadata(
            title=next(
                (title for value in _values(metadata, "title") if (title := _text(value, 500))),
                None,
            ),
            authors=_authors(_values(metadata, "creator")),
            translators=_authors(
                [
                    "".join(item.itertext())
                    for item in metadata.findall(DC + "contributor")
                    if item.get("{http://www.idpf.org/2007/opf}role") == "trl"
                    or item.get("id")
                    in {
                        element.get("refines", "")[1:]
                        for element in metadata.findall("{*}meta[@property='role']")
                        if element.text == "trl"
                    }
                ]
            ),
            series=_text(
                metadata.findtext("{*}meta[@property='belongs-to-collection']")
                or next(
                    (
                        item.get("content")
                        for item in metadata.findall("{*}meta[@name='calibre:series']")
                    ),
                    None,
                ),
                200,
            ),
            description=next(
                (
                    description
                    for value in _values(metadata, "description")
                    if (description := _text(value, 20000, multiline=True))
                ),
                None,
            ),
            isbn=next(
                (isbn for value in _values(metadata, "identifier") if (isbn := _isbn(value))), None
            ),
            cover=_epub_cover(archive, package, opf),
        )


def _preferred(values: dict[str, str]) -> str | None:
    return values.get("x-default") or next(iter(values.values()), None)


def _pdf_thumbnail(reader: PdfReader) -> bytes | None:
    try:
        thumb = reader.pages[0].get("/Thumb")
        stream = thumb.get_object() if thumb is not None else None
        if isinstance(stream, StreamObject) and stream.get("/Filter") == "/DCTDecode":
            data = stream.get_data()
            if len(data) <= MAX_COVER_BYTES:
                return data
    except Exception:  # noqa: BLE001 - optional thumbnail
        pass
    return None


def _pdf(path: Path) -> EmbeddedMetadata:
    with path.open("rb") as stream:
        reader = PdfReader(stream)
        if reader.is_encrypted:
            return EmbeddedMetadata()
        info: Mapping[str, object] = reader.metadata or {}
        try:
            xmp = reader.xmp_metadata
            title = _preferred(xmp.dc_title or {}) if xmp else None
            authors = _authors(xmp.dc_creator or []) if xmp else ()
            description = _preferred(xmp.dc_description or {}) if xmp else None
            isbn = _isbn(xmp.dc_identifier) if xmp else None
        except Exception:  # noqa: BLE001 - malformed XMP can coexist with valid Info
            title, authors, description, isbn = None, (), None, None
        author = _text(info.get("/Author"), 200)
        return EmbeddedMetadata(
            title=_text(title, 500) or _text(info.get("/Title"), 500),
            authors=authors or ((author,) if author else ()),
            description=_text(description, 20000, multiline=True)
            or _text(info.get("/Subject"), 20000, multiline=True),
            isbn=isbn or _isbn(info.get("/ISBN")),
            cover=_pdf_thumbnail(reader),
        )


def read_metadata(path: Path, file_format: str) -> EmbeddedMetadata:
    try:
        return _epub(path) if file_format == "epub" else _pdf(path)
    except Exception:  # noqa: BLE001 - parser failures fall back to the filename
        return EmbeddedMetadata()
