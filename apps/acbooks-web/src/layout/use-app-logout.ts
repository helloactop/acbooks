import { useState } from "react";
import { useTranslation } from "react-i18next";

import { toApiError } from "../api/client";
import { logout } from "../auth/auth-api";
import { clearLibrarySessionState } from "../library/library-view-state";
import { appErrorMessage } from "./errors";

export interface UseAppLogoutOptions {
  onLoggedOut: () => void;
  onSessionExpired: () => void;
}

export function useAppLogout({
  onLoggedOut,
  onSessionExpired,
}: UseAppLogoutOptions) {
  const { t } = useTranslation("app");
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
      setLogoutError(appErrorMessage(t, apiError));
    }
  };

  return { handleLogout, loggingOut, logoutError };
}
