import { Button, Field, Input, PasswordInput } from "@acbooks/ui";
import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";

import { AuthAlert } from "./AuthAlert";

type LocalError = "password" | "username";

export interface LoginFormProps {
  busy: boolean;
  errorMessage?: string;
  errorRequestId?: string;
  initialUsername?: string;
  notice?: string;
  noticeTone?: "info" | "success";
  onClearError: () => void;
  onSubmit: (credentials: { password: string; username: string }) => void;
}

export function LoginForm({
  busy,
  errorMessage,
  errorRequestId,
  initialUsername,
  notice,
  noticeTone = "success",
  onClearError,
  onSubmit,
}: LoginFormProps) {
  const { t } = useTranslation("auth");
  const [username, setUsername] = useState(initialUsername ?? "");
  const [password, setPassword] = useState("");
  const [localError, setLocalError] = useState<LocalError | null>(null);

  const localMessage =
    localError === "username"
      ? t("login.usernameRequired")
      : localError === "password"
        ? t("login.passwordRequired")
        : undefined;
  const alertMessage = localMessage ?? errorMessage;
  const alertRequestId = localMessage === undefined ? errorRequestId : undefined;
  const invalidUsername = localError === "username" || errorMessage !== undefined;
  const invalidPassword = localError === "password" || errorMessage !== undefined;

  const clearErrors = () => {
    setLocalError(null);
    onClearError();
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedUsername = username.trim();

    if (trimmedUsername.length === 0) {
      setLocalError("username");
      return;
    }
    if (password.length === 0) {
      setLocalError("password");
      return;
    }

    setLocalError(null);
    onSubmit({ password, username: trimmedUsername });
  };

  return (
    <form
      aria-busy={busy || undefined}
      className="flex flex-col gap-5"
      noValidate
      onSubmit={handleSubmit}
    >
      {alertMessage ? (
        <AuthAlert requestId={alertRequestId}>{alertMessage}</AuthAlert>
      ) : notice ? (
        <AuthAlert tone={noticeTone}>{notice}</AuthAlert>
      ) : null}

      <Field
        htmlFor="login-username"
        invalid={invalidUsername}
        label={t("login.username")}
      >
        <Input
          autoCapitalize="none"
          autoComplete="username"
          controlSize="auth"
          disabled={busy}
          id="login-username"
          maxLength={128}
          onChange={(event) => {
            setUsername(event.target.value);
            clearErrors();
          }}
          placeholder={t("login.usernamePlaceholder")}
          required
          spellCheck={false}
          value={username}
        />
      </Field>

      <Field
        htmlFor="login-password"
        invalid={invalidPassword}
        label={t("login.password")}
      >
        <PasswordInput
          autoComplete="current-password"
          controlSize="auth"
          disabled={busy}
          id="login-password"
          maxLength={1024}
          onChange={(event) => {
            setPassword(event.target.value);
            clearErrors();
          }}
          placeholder={t("login.passwordPlaceholder")}
          required
          value={password}
        />
      </Field>

      <Button
        className="w-full"
        loading={busy}
        size="lg"
        type="submit"
        variant="soft"
      >
        {t("login.submit")}
      </Button>
    </form>
  );
}
