import { Button, Spinner } from "@acbooks/ui";
import { CircleAlert, RefreshCw } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";

import { toApiError, type ApiError } from "../api/client";
import { getCurrentAccount, type Account } from "../auth/auth-api";
import { AppShell } from "./AppShell";
import { appErrorMessage } from "./errors";
import { redirectToExpiredLogin, redirectToLogin } from "./routes";
import { useAppLogout } from "./use-app-logout";

type GuardState =
  | { status: "error"; error: ApiError }
  | { status: "loading" }
  | { account: Account; status: "ready" };

export interface AuthenticatedLayoutProps {
  children: ReactNode;
  currentPath: string;
  onLoggedOut?: () => void;
  onSessionExpired?: () => void;
  onUnauthenticated?: () => void;
}

export function AuthenticatedLayout({
  children,
  currentPath,
  onLoggedOut = redirectToLogin,
  onSessionExpired = redirectToExpiredLogin,
  onUnauthenticated = redirectToLogin,
}: AuthenticatedLayoutProps) {
  const { t } = useTranslation("app");
  const [guard, setGuard] = useState<GuardState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  const { handleLogout, loggingOut, logoutError } = useAppLogout({
    onLoggedOut,
    onSessionExpired,
  });

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        const account = await getCurrentAccount();
        if (cancelled) {
          return;
        }
        if (account === null) {
          onUnauthenticated();
          return;
        }
        setGuard({ account, status: "ready" });
      } catch (cause) {
        if (!cancelled) {
          setGuard({ error: toApiError(cause), status: "error" });
        }
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [attempt, onUnauthenticated]);

  if (guard.status === "loading") {
    return (
      <div className="flex h-dvh items-center justify-center bg-frame">
        <div className="flex items-center gap-3 text-sm text-muted-foreground">
          <Spinner label={t("guard.loading")} />
          <span>{t("guard.loading")}</span>
        </div>
      </div>
    );
  }

  if (guard.status === "error") {
    return (
      <div className="flex h-dvh items-center justify-center bg-frame p-4">
        <div className="w-full max-w-[400px] rounded-panel border border-border bg-background p-6 text-center">
          <CircleAlert
            aria-hidden="true"
            className="mx-auto size-8 text-destructive-text"
            strokeWidth={2}
          />
          <h1 className="mt-3 text-xl font-semibold">
            {t("guard.errorTitle")}
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {appErrorMessage(t, guard.error)}
          </p>
          <Button
            className="mt-5 w-full"
            onClick={() => {
              setGuard({ status: "loading" });
              setAttempt((current) => current + 1);
            }}
            size="lg"
            variant="soft"
          >
            <RefreshCw aria-hidden="true" />
            {t("guard.retry")}
          </Button>
        </div>
      </div>
    );
  }

  return (
    <AppShell
      accountName={guard.account.username}
      currentPath={currentPath}
      expireSession={onSessionExpired}
      loggingOut={loggingOut}
      logoutError={logoutError}
      onLogout={() => {
        void handleLogout();
      }}
    >
      {children}
    </AppShell>
  );
}
