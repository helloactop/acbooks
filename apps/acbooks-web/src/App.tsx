import { AuthPage } from "./auth/AuthPage";
import { PreviewPage } from "./preview/PreviewPage";

function isPreviewPath(pathname: string): boolean {
  return pathname === "/preview" || pathname.startsWith("/preview/");
}

export default function App() {
  return isPreviewPath(window.location.pathname) ? (
    <PreviewPage />
  ) : (
    <AuthPage />
  );
}
