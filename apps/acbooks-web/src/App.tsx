import { lazy, Suspense } from "react";
import { Route, Routes, useParams } from "react-router-dom";

import { AuthPage } from "./auth/AuthPage";
import { DashboardPage } from "./dashboard/DashboardPage";
import { AuthenticatedLayout } from "./layout/AuthenticatedLayout";
import { BookDetailPage } from "./library/BookDetailPage";
import { OrganizationPage } from "./library/OrganizationPage";
import { LibraryPage } from "./library/LibraryPage";
import { LocationsPage } from "./locations/LocationsPage";
import { StoragePage } from "./storage/StoragePage";
import { NotFoundPage } from "./NotFoundPage";
import { PreviewPage } from "./preview/PreviewPage";
import { SettingsPage } from "./settings/SettingsPage";

const ReadingStatsPage = lazy(() => import("./stats/ReadingStatsPage"));

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
        <Route element={<Suspense><ReadingStatsPage /></Suspense>} path="stats" />
        <Route element={<BookDetailRoute />} path="library/books/:bookId" />
        <Route element={<OrganizationPage />} path="organization" />
        <Route element={<LocationsPage />} path="locations" />
        <Route element={<StoragePage />} path="storage" />
        <Route element={<SettingsPage />} path="settings" />
      </Route>
      <Route element={<NotFoundPage />} path="*" />
    </Routes>
  );
}
