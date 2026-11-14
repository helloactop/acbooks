from uuid import UUID

from app.modules.books.schemas import BookFile, LaxApiModel


class FileMatch(BookFile):
    book_id: UUID
    sha256: str


class FileMatchPage(LaxApiModel):
    items: list[FileMatch]
    next_offset: int | None
