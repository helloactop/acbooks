from typing import Literal

ReadingStatus = Literal["unread", "reading", "done"]
BookSort = Literal["title", "author", "createdAt", "updatedAt"]
BookOrder = Literal["asc", "desc"]
MediaType = Literal["physical", "digital"]
