import { Button, DropdownMenu, Select, Spinner } from "@acbooks/ui";
import { Ellipsis } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import type { BookDetail, ReadingStatus } from "./books-api";

const STATUSES: readonly ReadingStatus[] = ["unread", "reading", "done"];

type CopyState = "idle" | "copied" | "error";

async function copyToClipboard(value: string): Promise<void> {
  if (navigator.clipboard !== undefined) {
    await navigator.clipboard.writeText(value);
    return;
  }
  const textarea = document.createElement("textarea");
  textarea.value = value;
  textarea.setAttribute("readonly", "");
  textarea.style.position = "fixed";
  textarea.style.top = "-9999px";
  document.body.appendChild(textarea);
  textarea.select();
  try {
    if (!document.execCommand("copy")) {
      throw new Error("Copy failed.");
    }
  } finally {
    textarea.remove();
  }
}

export interface BookDetailActionsProps {
  detail: BookDetail;
  onEdit: () => void;
  onStatusChange: (status: ReadingStatus) => void;
  statusSaving: boolean;
}

export function BookDetailActions({
  detail,
  onEdit,
  onStatusChange,
  statusSaving,
}: BookDetailActionsProps) {
  const { t } = useTranslation("library");
  const [copyState, setCopyState] = useState<CopyState>("idle");
  const copyTimer = useRef<number | undefined>(undefined);

  useEffect(
    () => () => {
      if (copyTimer.current !== undefined) {
        window.clearTimeout(copyTimer.current);
      }
    },
    [],
  );

  const copyIsbn = async () => {
    const isbn = detail.isbn;
    if (isbn === null) {
      setCopyState("error");
      return;
    }
    try {
      await copyToClipboard(isbn);
      setCopyState("copied");
    } catch {
      setCopyState("error");
    } finally {
      if (copyTimer.current !== undefined) {
        window.clearTimeout(copyTimer.current);
      }
      copyTimer.current = window.setTimeout(() => {
        copyTimer.current = undefined;
        setCopyState("idle");
      }, 2500);
    }
  };

  return (
    <div className="flex w-full flex-col gap-2 @min-[640px]:flex-row @min-[640px]:flex-wrap @min-[640px]:items-center @min-[1000px]:flex-col @min-[1000px]:items-stretch">
      <Button
        className="w-full @min-[640px]:w-auto @min-[1000px]:w-full"
        onClick={onEdit}
        size="lg"
        variant="soft"
      >
        {t("detail.edit")}
      </Button>

      <div className="flex min-w-0 flex-1 items-center gap-2 @min-[1000px]:w-full">
        <label className="sr-only" htmlFor="book-reading-status">
          {t("detail.readingStatus")}
        </label>
        <Select
          aria-busy={statusSaving || undefined}
          className="h-11 @min-[640px]:h-10"
          containerClassName="min-w-0 flex-1"
          disabled={statusSaving}
          id="book-reading-status"
          onChange={(event) => {
            onStatusChange(event.target.value as ReadingStatus);
          }}
          value={detail.readingStatus}
        >
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {t(`status.${status}`)}
            </option>
          ))}
        </Select>

        {statusSaving ? <Spinner label={t("detail.savingStatus")} /> : null}

        {detail.isbn !== null ? (
          <DropdownMenu
            actions={[
              {
                label: t("detail.copyIsbn"),
                onSelect: () => {
                  void copyIsbn();
                },
              },
            ]}
            className="shrink-0"
            label={t("actions.more")}
            side="top"
            triggerClassName="@max-[639px]:size-11"
            triggerVariant="outline"
          >
            <Ellipsis aria-hidden="true" />
          </DropdownMenu>
        ) : null}
      </div>

      {copyState !== "idle" ? (
        <p
          className={
            copyState === "error"
              ? "text-xs text-destructive-text"
              : "text-xs text-muted-foreground"
          }
          role={copyState === "error" ? "alert" : "status"}
        >
          {copyState === "error"
            ? t("detail.copyFailed")
            : t("detail.isbnCopied")}
        </p>
      ) : null}
    </div>
  );
}
