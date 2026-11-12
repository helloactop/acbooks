import { Button, Spinner } from "@acbooks/ui";
import { RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import {
  getCurrentAccount,
  getSetupStatus,
  initializeSetup,
  login,
  toAuthError,
  type Account,
  type AuthError,
  type LoginRequest,
  type SetupRequest,
} from "./auth-api";
import { authErrorMessage, showRequestId } from "./auth-messages";
import { AuthAlert } from "./AuthAlert";
import { AuthPanel } from "./AuthPanel";
import { LoginForm } from "./LoginForm";
import { SetupForm } from "./SetupForm";

type Screen = "loading" | "login" | "setup" | "statusError";
type Notice = "alreadyInitialized" | "setupSuccess" | null;

export interface AuthPageProps {
  onAuthenticated?: (account: Account) => void;
}

function redirectToPreview(): void {
  window.location.assign("/preview");
}

export function AuthPage({
  onAuthenticated = redirectToPreview,
}: AuthPageProps) {
  const { t } = useTranslation("auth");
  const [screen, setScreen] = useState<Screen>("loading");
  const [checkingSession, setCheckingSession] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [error, setError] = useState<AuthError | null>(null);
  const [notice, setNotice] = useState<Notice>(null);
  const [loginUsername, setLoginUsername] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        const status = await getSetupStatus();
        if (cancelled) {
          return;
        }
        if (!status.initialized) {
          setScreen("setup");
          return;
        }

        setCheckingSession(true);
        const account = await getCurrentAccount();
        if (cancelled) {
          return;
        }
        if (account !== null) {
          onAuthenticated(account);
          return;
        }
        setScreen("login");
      } catch (cause) {
        if (cancelled) {
          return;
        }
        setError(toAuthError(cause));
        setScreen("statusError");
      } finally {
        if (!cancelled) {
          setCheckingSession(false);
        }
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [attempt, onAuthenticated]);

  const handleLogin = async ({ password, username }: LoginRequest) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const response = await login({ password, username });
      onAuthenticated(response.account);
    } catch (cause) {
      setError(toAuthError(cause));
    } finally {
      setBusy(false);
    }
  };

  const handleSetup = async (values: SetupRequest) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const account = await initializeSetup(values);
      setLoginUsername(account.username);
      setNotice("setupSuccess");
      setScreen("login");
    } catch (cause) {
      const failure = toAuthError(cause);
      if (failure.code === "SETUP_ALREADY_INITIALIZED") {
        setNotice("alreadyInitialized");
        setScreen("login");
      } else {
        setError(failure);
      }
    } finally {
      setBusy(false);
    }
  };

  const errorMessage = error === null ? undefined : authErrorMessage(t, error);
  const errorRequestId = error === null ? undefined : showRequestId(error);
  const noticeMessage =
    notice === "setupSuccess"
      ? t("login.setupSuccess")
      : notice === "alreadyInitialized"
        ? t("login.alreadyInitialized")
        : undefined;

  if (screen === "loading") {
    const statusText = checkingSession
      ? t("loading.session")
      : t("loading.status");

    return (
      <AuthPanel title={t("loading.title")}>
        <div className="flex items-center justify-center gap-3 py-2 text-sm text-muted-foreground">
          <Spinner label={statusText} />
          <span>{statusText}</span>
        </div>
      </AuthPanel>
    );
  }

  if (screen === "statusError") {
    return (
      <AuthPanel
        description={t("statusError.description")}
        title={t("statusError.title")}
      >
        <div className="flex flex-col gap-5">
          {errorMessage ? (
            <AuthAlert requestId={errorRequestId}>{errorMessage}</AuthAlert>
          ) : null}
          <Button
            className="w-full"
            onClick={() => {
              setError(null);
              setCheckingSession(false);
              setScreen("loading");
              setAttempt((current) => current + 1);
            }}
            size="lg"
            variant="soft"
          >
            <RefreshCw aria-hidden="true" />
            {t("statusError.retry")}
          </Button>
        </div>
      </AuthPanel>
    );
  }

  if (screen === "setup") {
    return (
      <AuthPanel
        description={t("setup.description")}
        title={t("setup.title")}
      >
        <SetupForm
          busy={busy}
          errorMessage={errorMessage}
          errorRequestId={errorRequestId}
          onClearError={() => {
            setError(null);
          }}
          onSubmit={handleSetup}
        />
      </AuthPanel>
    );
  }

  return (
    <AuthPanel
      description={t("login.description")}
      title={t("login.title")}
    >
      <LoginForm
        busy={busy}
        errorMessage={errorMessage}
        errorRequestId={errorRequestId}
        initialUsername={loginUsername}
        notice={noticeMessage}
        onClearError={() => {
          setError(null);
        }}
        onSubmit={handleLogin}
      />
    </AuthPanel>
  );
}
