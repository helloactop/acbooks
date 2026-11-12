import { AuthPage } from "./auth/AuthPage";
import { LibraryPage } from "./library/LibraryPage";
import { PreviewPage } from "./preview/PreviewPage";

function isPreviewPath(pathname: string): boolean {
  return pathname === "/preview" || pathname.startsWith("/preview/");
}

function isLibraryPath(pathname: string): boolean {
  return pathname === "/library" || pathname.startsWith("/library/");
}

export default function App() {
  const pathname = window.location.pathname;

  if (isPreviewPath(pathname)) {
    return <PreviewPage />;
  }
  if (isLibraryPath(pathname)) {
    return <LibraryPage />;
  }
  return <AuthPage />;
}
