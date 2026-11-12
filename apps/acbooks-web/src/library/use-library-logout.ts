import { useState } from "react";
import { useTranslation } from "react-i18next";

import { toApiError } from "../api/client";
import { logout } from "../auth/auth-api";
import { libraryErrorMessage } from "./errors";
import { clearLibrarySessionState } from "./library-view-state";

export interface UseLibraryLogoutOptions {
  onLoggedOut: () => void;
  onSessionExpired: () => void;
}

export function useLibraryLogout({
  onLoggedOut,
  onSessionExpired,
}: UseLibraryLogoutOptions) {
  const { t } = useTranslation("library");
  const [loggingOut, setLoggingOut] = useState(false);
  const [logoutError, setLogoutError] = useState<string | undefined>(
    undefined,
  );

  const handleLogout = async () => {
    setLoggingOut(true);
    setLogoutError(undefined);
    try {
      await logout();
      clearLibrarySessionState();
      setLoggingOut(false);
      onLoggedOut();
    } catch (cause) {
      const apiError = toApiError(cause);
      setLoggingOut(false);
      if (apiError.status === 401) {
        onSessionExpired();
        return;
      }
      setLogoutError(libraryErrorMessage(t, apiError));
    }
  };

  return { handleLogout, loggingOut, logoutError };
}
