import { Button, Field, Input, PasswordInput } from "@acbooks/ui";
import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";

import type { SetupRequest } from "./auth-api";
import { AuthAlert } from "./AuthAlert";

type SetupField =
  | "confirmPassword"
  | "password"
  | "setupCode"
  | "username";

type SetupFieldError =
  | "passwordMismatch"
  | "passwordTooShort"
  | "setupCodeRequired"
  | "usernameRequired";

const ERROR_KEYS: Record<SetupFieldError, string> = {
  passwordMismatch: "setup.passwordMismatch",
  passwordTooShort: "setup.passwordTooShort",
  setupCodeRequired: "setup.setupCodeRequired",
  usernameRequired: "setup.usernameRequired",
};

export interface SetupFormProps {
  busy: boolean;
  errorMessage?: string;
  errorRequestId?: string;
  onClearError: () => void;
  onSubmit: (values: SetupRequest) => void;
}

export function SetupForm({
  busy,
  errorMessage,
  errorRequestId,
  onClearError,
  onSubmit,
}: SetupFormProps) {
  const { t } = useTranslation("auth");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [setupCode, setSetupCode] = useState("");
  const [errors, setErrors] = useState<
    Partial<Record<SetupField, SetupFieldError>>
  >({});

  const errorFor = (field: SetupField): string | undefined => {
    const code = errors[field];
    return code === undefined ? undefined : t(ERROR_KEYS[code]);
  };

  const update = (field: SetupField, value: string) => {
    switch (field) {
      case "username":
        setUsername(value);
        break;
      case "password":
        setPassword(value);
        break;
      case "confirmPassword":
        setConfirmPassword(value);
        break;
      case "setupCode":
        setSetupCode(value);
        break;
    }
    setErrors((current) => ({ ...current, [field]: undefined }));
    onClearError();
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedUsername = username.trim();
    const trimmedSetupCode = setupCode.trim();
    const nextErrors: Partial<Record<SetupField, SetupFieldError>> = {};

    if (trimmedUsername.length === 0) {
      nextErrors.username = "usernameRequired";
    }
    if (Array.from(password).length < 12) {
      nextErrors.password = "passwordTooShort";
    }
    if (confirmPassword !== password) {
      nextErrors.confirmPassword = "passwordMismatch";
    }
    if (trimmedSetupCode.length === 0) {
      nextErrors.setupCode = "setupCodeRequired";
    }

    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    onSubmit({
      confirmPassword,
      password,
      setupCode: trimmedSetupCode,
      username: trimmedUsername,
    });
  };

  return (
    <form
      aria-busy={busy || undefined}
      className="flex flex-col gap-5"
      noValidate
      onSubmit={handleSubmit}
    >
      {errorMessage ? (
        <AuthAlert requestId={errorRequestId}>{errorMessage}</AuthAlert>
      ) : null}

      <Field
        error={errorFor("username")}
        htmlFor="setup-username"
        label={t("setup.username")}
      >
        <Input
          autoCapitalize="none"
          autoComplete="username"
          controlSize="auth"
          disabled={busy}
          id="setup-username"
          maxLength={128}
          onChange={(event) => {
            update("username", event.target.value);
          }}
          placeholder={t("setup.usernamePlaceholder")}
          required
          spellCheck={false}
          value={username}
        />
      </Field>

      <Field
        description={t("setup.passwordDescription")}
        error={errorFor("password")}
        htmlFor="setup-password"
        label={t("setup.password")}
      >
        <PasswordInput
          autoComplete="new-password"
          controlSize="auth"
          disabled={busy}
          id="setup-password"
          maxLength={1024}
          onChange={(event) => {
            update("password", event.target.value);
          }}
          placeholder={t("setup.passwordPlaceholder")}
          required
          value={password}
        />
      </Field>

      <Field
        error={errorFor("confirmPassword")}
        htmlFor="setup-confirm-password"
        label={t("setup.confirmPassword")}
      >
        <PasswordInput
          autoComplete="new-password"
          controlSize="auth"
          disabled={busy}
          id="setup-confirm-password"
          maxLength={1024}
          onChange={(event) => {
            update("confirmPassword", event.target.value);
          }}
          placeholder={t("setup.confirmPasswordPlaceholder")}
          required
          value={confirmPassword}
        />
      </Field>

      <Field
        description={t("setup.setupCodeDescription")}
        error={errorFor("setupCode")}
        htmlFor="setup-code"
        label={t("setup.setupCode")}
      >
        <Input
          autoCapitalize="none"
          autoComplete="one-time-code"
          controlSize="auth"
          disabled={busy}
          id="setup-code"
          maxLength={128}
          onChange={(event) => {
            update("setupCode", event.target.value);
          }}
          placeholder={t("setup.setupCodePlaceholder")}
          required
          spellCheck={false}
          value={setupCode}
        />
      </Field>

      <Button
        className="w-full"
        loading={busy}
        size="lg"
        type="submit"
        variant="soft"
      >
        {t("setup.submit")}
      </Button>
    </form>
  );
}
