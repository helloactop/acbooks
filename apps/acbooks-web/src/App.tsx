import { AuthPage } from "./auth/AuthPage";
import { BookDetailPage } from "./library/BookDetailPage";
import { LibraryPage } from "./library/LibraryPage";
import { PreviewPage } from "./preview/PreviewPage";

function isPreviewPath(pathname: string): boolean {
  return pathname === "/preview" || pathname.startsWith("/preview/");
}

function isLibraryPath(pathname: string): boolean {
  return pathname === "/library" || pathname.startsWith("/library/");
}

function bookIdFromPath(pathname: string): string | null {
  const match = /^\/library\/books\/([^/]+)\/?$/.exec(pathname);
  if (match?.[1] === undefined) {
    return null;
  }
  try {
    return decodeURIComponent(match[1]);
  } catch {
    return null;
  }
}

export default function App() {
  const pathname = window.location.pathname;
  const bookId = bookIdFromPath(pathname);

  if (isPreviewPath(pathname)) {
    return <PreviewPage />;
  }
  if (bookId !== null) {
    return <BookDetailPage bookId={bookId} />;
  }
  if (isLibraryPath(pathname)) {
    return <LibraryPage />;
  }
  return <AuthPage />;
}
