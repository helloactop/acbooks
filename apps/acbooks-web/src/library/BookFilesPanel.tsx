import {
  Button,
  IconButton,
  Spinner,
  buttonVariants,
  useLanguage,
} from "@acbooks/ui";
import { Download, Trash2 } from "lucide-react";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { toApiError, type ApiError } from "../api/client";
import { FormatBadges } from "./BookMeta";
import { libraryErrorMessage } from "./errors";
import {
  bookFileContentUrl,
  deleteBookFile,
  uploadBookFile,
  type BookFile,
} from "./files-api";
import { Modal } from "./Modal";
import { formatBytes } from "./presentation";

export interface BookFilesPanelProps {
  bookId: string;
  files: BookFile[];
  onChanged: () => void;
  onSessionExpired: () => void;
}

interface UploadProgress {
  loaded: number;
  name: string;
  total: number;
}

export function BookFilesPanel({
  bookId,
  files,
  onChanged,
  onSessionExpired,
}: BookFilesPanelProps) {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState<UploadProgress | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<BookFile | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState<ApiError | null>(null);

  const uploadFiles = async (selected: File[]) => {
    if (selected.length === 0 || uploading) {
      return;
    }
    setUploading(true);
    setError(null);
    for (const file of selected) {
      setProgress({ loaded: 0, name: file.name, total: file.size });
      try {
        await uploadBookFile(bookId, file, (loaded, total) => {
          setProgress({ loaded, name: file.name, total });
        });
      } catch (cause) {
        const apiError = toApiError(cause);
        if (apiError.status === 401) {
          onSessionExpired();
          return;
        }
        setError(apiError);
        break;
      }
    }
    setUploading(false);
    setProgress(null);
    onChanged();
  };

  const confirmDelete = async () => {
    if (deleteTarget === null || deleteBusy) {
      return;
    }
    setDeleteBusy(true);
    setDeleteError(null);
    try {
      await deleteBookFile(deleteTarget.id);
      setDeleteBusy(false);
      setDeleteTarget(null);
      onChanged();
    } catch (cause) {
      const apiError = toApiError(cause);
      setDeleteBusy(false);
      if (apiError.status === 401) {
        onSessionExpired();
        return;
      }
      setDeleteError(apiError);
    }
  };

  const percent =
    progress === null || progress.total <= 0
      ? 0
      : Math.min(100, Math.round((progress.loaded / progress.total) * 100));
  const errorMessage =
    error === null ? undefined : libraryErrorMessage(t, error);

  return (
    <section>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-base font-semibold">{t("detail.files")}</h2>
        <Button
          disabled={uploading}
          onClick={() => {
            inputRef.current?.click();
          }}
          size="sm"
          variant="outline"
        >
          {t("files.upload")}
        </Button>
      </div>
      <input
        accept=".epub,.pdf,application/epub+zip,application/pdf"
        className="hidden"
        multiple
        onChange={(event) => {
          const selected = Array.from(event.target.files ?? []);
          void uploadFiles(selected);
          event.target.value = "";
        }}
        ref={inputRef}
        type="file"
      />

      {errorMessage !== undefined ? (
        <p className="mt-2 text-sm text-destructive-text" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {progress !== null ? (
        <div aria-live="polite" className="mt-2" role="status">
          <p className="text-xs text-muted-foreground">
            {t("files.uploading", {
              name: progress.name,
              percent,
            })}
          </p>
          <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-surface">
            <div
              className="h-full rounded-full bg-accent-text transition-[width] duration-150 ease-out"
              style={{ width: `${percent}%` }}
            />
          </div>
        </div>
      ) : null}

      {files.length === 0 ? (
        <p className="mt-3 text-sm text-muted-foreground">
          {t("detail.noFiles")}
        </p>
      ) : (
        <ul
          aria-label={t("detail.files")}
          className="mt-3 divide-y divide-border border-y border-border"
        >
          {files.map((file, index) => (
            <li
              className="flex flex-wrap items-center gap-x-2 gap-y-1 py-3 text-sm"
              key={file.id}
            >
              <span className="text-xs text-muted-foreground">
                {t("detail.fileNumber", { number: index + 1 })}
              </span>
              <FormatBadges formats={[file.format]} />
              <span
                className={
                  file.fileStatus === "present"
                    ? "text-[13px] text-muted-foreground"
                    : "text-[13px] text-destructive-text"
                }
              >
                {file.fileStatus === "present"
                  ? t("detail.filePresent")
                  : t("detail.fileMissing")}
              </span>
              <span className="ml-auto text-xs tabular-nums text-muted-foreground">
                {formatBytes(file.sizeBytes, resolvedLanguage)}
              </span>
              <a
                aria-label={t("files.download")}
                className={buttonVariants({ size: "icon", variant: "ghost" })}
                download
                href={bookFileContentUrl(file.id)}
              >
                <Download aria-hidden="true" className="size-4" />
              </a>
              <IconButton
                label={t("files.delete")}
                onClick={() => {
                  setDeleteError(null);
                  setDeleteTarget(file);
                }}
              >
                <Trash2 aria-hidden="true" />
              </IconButton>
            </li>
          ))}
        </ul>
      )}

      {deleteTarget !== null ? (
        <Modal
          describedBy="delete-file-description"
          labelledBy="delete-file-title"
          onClose={
            deleteBusy
              ? () => undefined
              : () => {
                  setDeleteTarget(null);
                }
          }
          size="confirm"
        >
          <div className="p-5">
            <h2 className="text-lg font-semibold" id="delete-file-title">
              {t("files.deleteTitle")}
            </h2>
            <p
              className="mt-2 text-sm text-muted-foreground"
              id="delete-file-description"
            >
              {t("files.deleteDescription")}
            </p>
            {deleteError !== null ? (
              <p className="mt-3 text-sm text-destructive-text" role="alert">
                {libraryErrorMessage(t, deleteError)}
              </p>
            ) : null}
            <div className="mt-6 flex flex-wrap items-center justify-end gap-2">
              {deleteBusy ? <Spinner /> : null}
              <Button
                disabled={deleteBusy}
                onClick={() => {
                  setDeleteTarget(null);
                }}
                variant="ghost"
              >
                {t("form.cancel")}
              </Button>
              <Button
                disabled={deleteBusy}
                onClick={() => {
                  void confirmDelete();
                }}
                variant="danger-confirm"
              >
                {t("files.deleteConfirm")}
              </Button>
            </div>
          </div>
        </Modal>
      ) : null}
    </section>
  );
}