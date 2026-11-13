import { Button, Field, IconButton, Input, Select, Spinner } from "@acbooks/ui";
import { RefreshCw, Trash2 } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { isAbortError, toApiError, type ApiError } from "../api/client";
import { useAppShell } from "../layout/app-shell-context";
import { PageHeader } from "../layout/PageHeader";
import { useDocumentTitle } from "../layout/use-document-title";
import { libraryErrorMessage } from "../library/errors";
import { Modal } from "../library/Modal";
import {
  createStorageRoot,
  deleteStorageRoot,
  getJob,
  listStorageRoots,
  startStorageScan,
  type JobStatus,
  type StorageRoot,
  type StorageRootCreate,
} from "./storage-api";

type Mode = StorageRootCreate["mode"];

interface ScanState {
  errorCode: string | null;
  jobId: string;
  result: JobStatus["result"];
  rootId: string;
  status: JobStatus["status"];
}

const POLL_INTERVAL = 1200;
const delay = (ms: number) =>
  new Promise<void>((resolve) => {
    setTimeout(resolve, ms);
  });

function scanCounts(result: JobStatus["result"]) {
  if (!result) {
    return null;
  }
  const value = (key: string) => Number((result as Record<string, unknown>)[key] ?? 0);
  return {
    scanned: value("scanned"),
    created: value("created"),
    updated: value("updated"),
    unchanged: value("unchanged"),
    missing: value("missing"),
    skipped: value("skipped"),
    errors: value("errors"),
  };
}

export function StoragePage() {
  const { t } = useTranslation("storage");
  const { t: tLibrary } = useTranslation("library");
  const { expireSession } = useAppShell();
  useDocumentTitle(t("title"));
  const [roots, setRoots] = useState<StorageRoot[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<ApiError | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [name, setName] = useState("");
  const [rootPath, setRootPath] = useState("");
  const [mode, setMode] = useState<Mode>("external");
  const [formError, setFormError] = useState<ApiError | null>(null);
  const [saving, setSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<StorageRoot | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<ApiError | null>(null);
  const [scan, setScan] = useState<ScanState | null>(null);
  const [actionError, setActionError] = useState<ApiError | null>(null);
  const scanToken = useRef(0);

  useEffect(() => {
    const controller = new AbortController();
    listStorageRoots(controller.signal)
      .then((items) => {
        setRoots(items);
        setLoadError(null);
      })
      .catch((cause) => {
        if (isAbortError(cause)) {
          return;
        }
        const apiError = toApiError(cause);
        if (apiError.status === 401) {
          expireSession();
          return;
        }
        setLoadError(apiError);
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [attempt, expireSession]);

  useEffect(
    () => () => {
      scanToken.current += 1;
    },
    [],
  );

  const reload = () => {
    setLoading(true);
    setAttempt((current) => current + 1);
  };

  const poll = async (jobId: string, rootId: string, token: number) => {
    for (;;) {
      await delay(POLL_INTERVAL);
      if (scanToken.current !== token) {
        return;
      }
      try {
        const job = await getJob(jobId);
        if (scanToken.current !== token) {
          return;
        }
        setScan({
          rootId,
          jobId,
          status: job.status,
          result: job.result ?? null,
          errorCode: job.errorCode ?? null,
        });
        if (job.status === "succeeded" || job.status === "failed") {
          reload();
          return;
        }
      } catch (cause) {
        if (scanToken.current !== token) {
          return;
        }
        const apiError = toApiError(cause);
        if (apiError.status === 401) {
          expireSession();
          return;
        }
        setActionError(apiError);
        return;
      }
    }
  };

  const runScan = async (root: StorageRoot, deep: boolean) => {
    if (scan !== null && (scan.status === "queued" || scan.status === "running")) {
      return;
    }
    setActionError(null);
    try {
      const accepted = await startStorageScan(root.id, deep);
      const token = scanToken.current + 1;
      scanToken.current = token;
      setScan({
        rootId: root.id,
        jobId: accepted.jobId,
        status: "queued",
        result: null,
        errorCode: null,
      });
      void poll(accepted.jobId, root.id, token);
    } catch (cause) {
      const apiError = toApiError(cause);
      if (apiError.status === 401) {
        expireSession();
        return;
      }
      setActionError(apiError);
    }
  };

  const submitRoot = async () => {
    if (saving || name.trim() === "" || rootPath.trim() === "") {
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      const body: StorageRootCreate = {
        name: name.trim(),
        rootPath: rootPath.trim(),
        mode,
      };
      await createStorageRoot(body);
      setSaving(false);
      setRegisterOpen(false);
      setName("");
      setRootPath("");
      setMode("external");
      reload();
    } catch (cause) {
      const apiError = toApiError(cause);
      setSaving(false);
      if (apiError.status === 401) {
        expireSession();
        return;
      }
      setFormError(apiError);
    }
  };

  const confirmDelete = async () => {
    if (deleteTarget === null || deleting) {
      return;
    }
    setDeleting(true);
    setDeleteError(null);
    try {
      await deleteStorageRoot(deleteTarget.id);
      setDeleting(false);
      setDeleteTarget(null);
      reload();
    } catch (cause) {
      const apiError = toApiError(cause);
      setDeleting(false);
      if (apiError.status === 401) {
        expireSession();
        return;
      }
      setDeleteError(apiError);
    }
  };

  const busyScan =
    scan !== null && (scan.status === "queued" || scan.status === "running");
  const loadMessage =
    loadError === null ? undefined : libraryErrorMessage(tLibrary, loadError);
  const actionMessage =
    actionError === null ? undefined : libraryErrorMessage(tLibrary, actionError);
  const formMessage =
    formError === null ? undefined : libraryErrorMessage(tLibrary, formError);
  const deleteMessage =
    deleteError === null ? undefined : libraryErrorMessage(tLibrary, deleteError);

  return (
    <div className="@container mx-auto flex min-h-0 w-full max-w-[900px] flex-col gap-4">
      <PageHeader
        actions={
          <>
            <Button onClick={reload} variant="outline">
              <RefreshCw aria-hidden="true" className="size-4" />
              {t("refresh")}
            </Button>
            <Button onClick={() => setRegisterOpen(true)} variant="soft">
              {t("addRoot")}
            </Button>
          </>
        }
        description={t("description")}
        title={t("title")}
      />

      {actionMessage !== undefined ? (
        <p className="text-sm text-destructive-text" role="alert">
          {actionMessage}
        </p>
      ) : null}

      {loading ? (
        <p className="py-8 text-center text-sm text-muted-foreground">
          {t("loading")}
        </p>
      ) : loadMessage !== undefined ? (
        <div className="flex flex-col items-center gap-3 py-10">
          <p className="text-sm text-destructive-text" role="alert">
            {loadMessage}
          </p>
          <Button onClick={reload} variant="soft">
            {t("retry")}
          </Button>
        </div>
      ) : roots.length === 0 ? (
        <p className="rounded-card border border-border bg-surface px-6 py-10 text-center text-sm text-muted-foreground">
          {t("empty")}
        </p>
      ) : (
        <ul className="flex flex-col divide-y divide-border overflow-hidden rounded-card border border-border">
          {roots.map((root) => {
            const current = scan !== null && scan.rootId === root.id ? scan : null;
            const counts = current === null ? null : scanCounts(current.result);
            return (
              <li
                className="flex flex-col gap-2 p-3 sm:flex-row sm:items-start"
                key={root.id}
              >
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{root.name}</span>
                    <span className="rounded bg-surface px-1.5 py-0.5 text-xs text-muted-foreground">
                      {root.mode === "managed"
                        ? t("modeManaged")
                        : t("modeExternal")}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {t("fileCount", { count: root.fileCount })}
                    </span>
                  </div>
                  <p className="mt-0.5 truncate font-mono text-xs text-muted-foreground">
                    {root.rootPath}
                  </p>
                  {current !== null ? (
                    <p
                      aria-live="polite"
                      className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground"
                    >
                      {current.status === "queued" ? (
                        <span>{t("scanQueued")}</span>
                      ) : current.status === "running" ? (
                        <span className="inline-flex items-center gap-1">
                          <Spinner />
                          {t("scanRunning")}
                        </span>
                      ) : current.status === "failed" ? (
                        <span className="text-destructive-text">
                          {t("scanFailed")}
                          {current.errorCode ? ` · ${current.errorCode}` : ""}
                        </span>
                      ) : (
                        <span>{t("scanSucceeded")}</span>
                      )}
                      {counts !== null ? (
                        <span>{t("scanResult", counts)}</span>
                      ) : null}
                    </p>
                  ) : null}
                </div>
                <div className="flex flex-wrap items-center gap-1">
                  <Button
                    disabled={busyScan}
                    onClick={() => {
                      void runScan(root, false);
                    }}
                    size="sm"
                    variant="outline"
                  >
                    {t("scanIncremental")}
                  </Button>
                  <Button
                    disabled={busyScan}
                    onClick={() => {
                      void runScan(root, true);
                    }}
                    size="sm"
                    variant="outline"
                  >
                    {t("scanDeep")}
                  </Button>
                  <IconButton
                    label={t("delete")}
                    onClick={() => {
                      setDeleteError(null);
                      setDeleteTarget(root);
                    }}
                  >
                    <Trash2 aria-hidden="true" />
                  </IconButton>
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {registerOpen ? (
        <Modal
          labelledBy="storage-form-title"
          onClose={saving ? () => undefined : () => setRegisterOpen(false)}
        >
          <div className="flex items-center justify-between gap-3 border-b border-border p-4 sm:p-5">
            <h2 className="text-lg font-semibold" id="storage-form-title">
              {t("formTitle")}
            </h2>
          </div>
          <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto p-4 sm:p-5">
            {formMessage !== undefined ? (
              <p className="text-sm text-destructive-text" role="alert">
                {formMessage}
              </p>
            ) : null}
            <Field htmlFor="storage-name" label={t("name")}>
              <Input
                disabled={saving}
                id="storage-name"
                maxLength={200}
                onChange={(event) => {
                  setName(event.target.value);
                  setFormError(null);
                }}
                placeholder={t("namePlaceholder")}
                value={name}
              />
            </Field>
            <Field
              description={t("pathHint")}
              htmlFor="storage-path"
              label={t("path")}
            >
              <Input
                autoCapitalize="off"
                autoComplete="off"
                disabled={saving}
                id="storage-path"
                maxLength={4096}
                onChange={(event) => {
                  setRootPath(event.target.value);
                  setFormError(null);
                }}
                placeholder={t("pathPlaceholder")}
                spellCheck={false}
                value={rootPath}
              />
            </Field>
            <Field htmlFor="storage-mode" label={t("mode")}>
              <Select
                ariaLabel={t("mode")}
                disabled={saving}
                id="storage-mode"
                onValueChange={(value) => {
                  setMode(value as Mode);
                  setFormError(null);
                }}
                options={[
                  { label: t("modeExternal"), value: "external" },
                  { label: t("modeManaged"), value: "managed" },
                ]}
                value={mode}
              />
            </Field>
          </div>
          <div className="flex flex-wrap justify-end gap-2 border-t border-border p-4 sm:p-5">
            <Button
              disabled={saving}
              onClick={() => setRegisterOpen(false)}
              variant="ghost"
            >
              {t("cancel")}
            </Button>
            <Button
              loading={saving}
              onClick={() => {
                void submitRoot();
              }}
            >
              {t("save")}
            </Button>
          </div>
        </Modal>
      ) : null}

      {deleteTarget !== null ? (
        <Modal
          describedBy="storage-delete-description"
          labelledBy="storage-delete-title"
          onClose={
            deleting
              ? () => undefined
              : () => {
                  setDeleteTarget(null);
                }
          }
          size="confirm"
        >
          <div className="p-5">
            <h2 className="text-lg font-semibold" id="storage-delete-title">
              {t("deleteTitle", { name: deleteTarget.name })}
            </h2>
            <p
              className="mt-2 text-sm text-muted-foreground"
              id="storage-delete-description"
            >
              {t("deleteDescription")}
            </p>
            {deleteMessage !== undefined ? (
              <p className="mt-3 text-sm text-destructive-text" role="alert">
                {deleteMessage}
              </p>
            ) : null}
            <div className="mt-6 flex flex-wrap items-center justify-end gap-2">
              {deleting ? <Spinner /> : null}
              <Button
                disabled={deleting}
                onClick={() => setDeleteTarget(null)}
                variant="ghost"
              >
                {t("cancel")}
              </Button>
              <Button
                disabled={deleting}
                onClick={() => {
                  void confirmDelete();
                }}
                variant="danger-confirm"
              >
                {t("deleteConfirm")}
              </Button>
            </div>
          </div>
        </Modal>
      ) : null}
    </div>
  );
}