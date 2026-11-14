import { Button, Spinner } from "@acbooks/ui";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import type { components } from "../../../../packages/acbooks-api-client/schema";
import { apiRequest, patchWithCsrf, toApiError } from "../api/client";
import { useAppShell } from "../layout/app-shell-context";
import { SettingsGroup, SettingsRow } from "./SettingsGroup";

type Settings = components["schemas"]["MetadataSettings"];
const fields = ["title", "authors", "translators", "series", "description", "isbn", "cover"] as const;

export function MetadataSettings() {
  const { t } = useTranslation("library");
  const { expireSession } = useAppShell();
  const [settings, setSettings] = useState<Settings>();
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest<Settings>("/metadata/settings", { signal: controller.signal })
      .then((value) => { if (!controller.signal.aborted) setSettings(value); })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (toApiError(error).status === 401) expireSession();
        else setFailed(true);
      });
    return () => controller.abort();
  }, [attempt, expireSession]);

  const save = async (next: Settings) => {
    if (busy) return;
    setBusy(true);
    setFailed(false);
    try {
      setSettings(await patchWithCsrf<Settings>("/metadata/settings", next));
    } catch (error) {
      if (toApiError(error).status === 401) expireSession();
      else setFailed(true);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      {failed ? (
        <div role="alert" className="flex items-center gap-2 text-sm text-destructive-text">
          {t("metadata.error")}
          {settings === undefined ? (
            <Button onClick={() => { setFailed(false); setAttempt(attempt + 1); }}>
              {t("errors.retry")}
            </Button>
          ) : null}
        </div>
      ) : null}
      {settings === undefined ? (failed ? null : <Spinner />) : (
        <SettingsGroup ariaLabel={t("metadata.auto")}>
          <SettingsRow label={t("metadata.auto")} htmlFor="metadata-auto" description={t("metadata.autoHint")}>
            <input aria-label={t("metadata.auto")} checked={settings.autoFill ?? false}
              disabled={busy} id="metadata-auto" type="checkbox" role="switch"
              onChange={(event) => void save({ ...settings, autoFill: event.target.checked })} />
          </SettingsRow>
          {fields.map((field) => (
            <SettingsRow key={field} label={t(`metadata.fields.${field}`)} htmlFor={`metadata-field-${field}`}>
              <input checked={(settings.fields ?? fields).includes(field)}
                disabled={busy} id={`metadata-field-${field}`} type="checkbox" role="switch"
                onChange={(event) => {
                  const enabled = settings.fields ?? [...fields];
                  void save({ ...settings, fields: event.target.checked
                    ? [...enabled, field] : enabled.filter((value) => value !== field) });
                }} />
            </SettingsRow>
          ))}
        </SettingsGroup>
      )}
    </>
  );
}
