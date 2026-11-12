import type { BookTag } from "./sample-books";

export const COVER_FONT =
  "'Songti SC', 'Noto Serif SC', 'Source Han Serif SC', serif";

export const ROW_GRID =
  "grid grid-cols-[48px_minmax(0,2fr)_minmax(0,1fr)_180px_36px] items-center gap-4 max-md:grid-cols-[48px_minmax(0,1fr)_36px]";

export const TAG_LABEL_KEYS: Record<BookTag, string> = {
  epub: "tags.epub",
  pdf: "tags.pdf",
  physical: "tags.physical",
};
