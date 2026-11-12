import { AuthPage } from "./auth/AuthPage";
import { DashboardPage } from "./dashboard/DashboardPage";
import { AuthenticatedLayout } from "./layout/AuthenticatedLayout";
import { BookDetailPage } from "./library/BookDetailPage";
import { LibraryPage } from "./library/LibraryPage";
import { NotFoundPage } from "./NotFoundPage";
import { PreviewPage } from "./preview/PreviewPage";
import { SettingsPage } from "./settings/SettingsPage";

function isPreviewPath(pathname: string): boolean {
  return pathname === "/preview" || pathname.startsWith("/preview/");
}

function isHomePath(pathname: string): boolean {
  return pathname === "/home" || pathname === "/home/";
}

function isLibraryPath(pathname: string): boolean {
  return pathname === "/library" || pathname === "/library/";
}

function isSettingsPath(pathname: string): boolean {
  return pathname === "/settings" || pathname === "/settings/";
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

function authenticatedContent(pathname: string) {
  if (isHomePath(pathname)) {
    return <DashboardPage />;
  }
  if (isSettingsPath(pathname)) {
    return <SettingsPage />;
  }
  const bookId = bookIdFromPath(pathname);
  if (bookId !== null) {
    return <BookDetailPage bookId={bookId} />;
  }
  if (isLibraryPath(pathname)) {
    return <LibraryPage />;
  }
  return <NotFoundPage />;
}

export default function App() {
  const pathname = window.location.pathname;
  const currentPath = `${pathname}${window.location.search}`;

  if (isPreviewPath(pathname)) {
    return <PreviewPage />;
  }
  if (pathname === "/") {
    return <AuthPage />;
  }
  if (
    isHomePath(pathname) ||
    isLibraryPath(pathname) ||
    isSettingsPath(pathname) ||
    bookIdFromPath(pathname) !== null
  ) {
    return (
      <AuthenticatedLayout currentPath={currentPath}>
        {authenticatedContent(pathname)}
      </AuthenticatedLayout>
    );
  }
  return <NotFoundPage />;
}
