import { useEffect } from "react";

export interface DocumentTitleOptions {
  brand?: boolean;
}

export function useDocumentTitle(
  title: string | null | undefined,
  { brand = true }: DocumentTitleOptions = {},
) {
  useEffect(() => {
    const trimmed = title?.trim() ?? "";
    if (trimmed.length === 0) {
      return;
    }
    const previous = document.title;
    document.title = brand ? `${trimmed} · acbooks` : trimmed;
    return () => {
      document.title = previous;
    };
  }, [brand, title]);
}
