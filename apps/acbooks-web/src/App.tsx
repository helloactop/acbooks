import { Route, Routes, useParams } from "react-router-dom";

import { AuthPage } from "./auth/AuthPage";
import { DashboardPage } from "./dashboard/DashboardPage";
import { AuthenticatedLayout } from "./layout/AuthenticatedLayout";
import { BookDetailPage } from "./library/BookDetailPage";
import { LibraryPage } from "./library/LibraryPage";
import { LocationsPage } from "./locations/LocationsPage";
import { NotFoundPage } from "./NotFoundPage";
import { PreviewPage } from "./preview/PreviewPage";
import { SettingsPage } from "./settings/SettingsPage";

function BookDetailRoute() {
  const { bookId } = useParams();
  return bookId === undefined ? (
    <NotFoundPage />
  ) : (
    <BookDetailPage bookId={bookId} key={bookId} />
  );
}

export default function App() {
  return (
    <Routes>
      <Route element={<AuthPage />} path="/" />
      <Route element={<PreviewPage />} path="/preview/*" />
      <Route element={<AuthenticatedLayout />}>
        <Route element={<DashboardPage />} path="home" />
        <Route element={<LibraryPage />} path="library" />
        <Route element={<BookDetailRoute />} path="library/books/:bookId" />
        <Route element={<LocationsPage />} path="locations" />
        <Route element={<SettingsPage />} path="settings" />
      </Route>
      <Route element={<NotFoundPage />} path="*" />
    </Routes>
  );
}
