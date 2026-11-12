import { Button, IconButton, Spinner } from "@acbooks/ui";
import { Copy, Ellipsis, Pencil } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
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
  const [menuOpen, setMenuOpen] = useState(false);
  const [copyState, setCopyState] = useState<CopyState>("idle");
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuId = useId();

  useEffect(() => {
    if (!menuOpen) {
      return;
    }
    const handlePointerDown = (event: PointerEvent) => {
      if (
        containerRef.current !== null &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setMenuOpen(false);
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setMenuOpen(false);
        triggerRef.current?.focus();
      }
    };
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [menuOpen]);

  useEffect(() => {
    if (copyState === "idle") {
      return;
    }
    const timer = window.setTimeout(() => {
      setCopyState("idle");
    }, 2500);
    return () => {
      window.clearTimeout(timer);
    };
  }, [copyState]);

  const copyIsbn = async () => {
    setMenuOpen(false);
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
      triggerRef.current?.focus();
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
        <Pencil aria-hidden="true" />
        {t("detail.edit")}
      </Button>

      <div className="flex min-w-0 flex-1 items-center gap-2 @min-[1000px]:w-full">
        <label className="sr-only" htmlFor="book-reading-status">
          {t("detail.readingStatus")}
        </label>
        <select
          aria-busy={statusSaving || undefined}
          className="h-11 min-w-0 flex-1 rounded-control border border-control-border bg-background px-3 text-sm text-foreground transition-colors duration-150 ease-out focus-visible:border-accent-text focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none disabled:cursor-not-allowed disabled:border-disabled-border disabled:bg-disabled-surface disabled:text-disabled-foreground @min-[640px]:h-10"
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
        </select>

        {statusSaving ? <Spinner label={t("detail.savingStatus")} /> : null}

        {detail.isbn !== null ? (
          <div className="relative shrink-0" ref={containerRef}>
            <IconButton
              aria-controls={menuOpen ? menuId : undefined}
              aria-expanded={menuOpen}
              aria-haspopup="menu"
              className="@max-[639px]:size-11"
              label={t("actions.more")}
              onClick={() => {
                setMenuOpen((current) => !current);
              }}
              ref={triggerRef}
              variant="outline"
            >
              <Ellipsis aria-hidden="true" />
            </IconButton>

            {menuOpen ? (
              <div
                className="absolute right-0 bottom-full z-20 mb-2 min-w-44 rounded-menu border border-border bg-background p-1 shadow-overlay"
                id={menuId}
                role="menu"
              >
                <button
                  className="flex w-full items-center gap-2 rounded px-3 py-2 text-left text-sm text-foreground hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none max-md:min-h-11"
                  onClick={() => {
                    void copyIsbn();
                  }}
                  role="menuitem"
                  type="button"
                >
                  <Copy aria-hidden="true" className="size-[18px]" />
                  {t("detail.copyIsbn")}
                </button>
              </div>
            ) : null}
          </div>
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
