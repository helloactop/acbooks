import { DropdownMenu, IconButton, useLanguage } from "@acbooks/ui";
import { Copy, Image, Trash2 } from "lucide-react";
import { Fragment, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { BookCover } from "./BookCover";
import { BookDescription } from "./BookDescription";
import { BookDetailActions } from "./BookDetailActions";
import { FormatBadges, PhysicalCopiesBadge, ShelfBadges, TagLinks } from "./BookMeta";
import type { BookDetail, ReadingStatus } from "./books-api";
import { libraryHref } from "../layout/routes";
import { joinNames } from "./presentation";

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

export interface BookDetailHeaderProps {
  coverBusy: boolean;
  detail: BookDetail;
  onDelete: () => void;
  onDeleteCover: () => void;
  onEdit: () => void;
  onMetadata: () => void;
  onStatusChange: (status: ReadingStatus) => void;
  onUploadCover: () => void;
  statusSaving: boolean;
}

export function BookDetailHeader({
  coverBusy,
  detail,
  onDelete,
  onDeleteCover,
  onEdit,
  onMetadata,
  onStatusChange,
  onUploadCover,
  statusSaving,
}: BookDetailHeaderProps) {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const authorLabel = joinNames(detail.authors, resolvedLanguage);
  const translators = joinNames(detail.translators, resolvedLanguage);
  const authorParts =
    detail.authors.length > 0
      ? new Intl.ListFormat(resolvedLanguage, {
          style: "short",
          type: "conjunction",
        }).formatToParts(detail.authors)
      : [];

  return (
    <header className="flex w-full flex-col items-center gap-5 text-center @min-[640px]:grid @min-[640px]:grid-cols-[132px_minmax(0,1fr)] @min-[640px]:items-start @min-[640px]:gap-x-6 @min-[640px]:gap-y-4 @min-[640px]:text-left @min-[1000px]:grid-cols-[150px_minmax(0,1fr)_230px] @min-[1000px]:grid-rows-[auto_1fr]">
      <div className="relative shrink-0 @min-[640px]:col-start-1 @min-[640px]:row-start-1 @min-[640px]:row-span-2">
        <BookCover
          authorClassName="text-[11px]"
          authorLabel={authorLabel}
          book={detail}
          className="w-[132px] @min-[1000px]:w-[150px]"
          titleClassName="text-[22px] leading-[1.25] @min-[1000px]:text-2xl"
        />
        <DropdownMenu
          actions={[
            { icon: Image, label: detail.hasCover ? t("cover.replace") : t("cover.upload"), onSelect: onUploadCover },
            ...(detail.hasCover
              ? [{ danger: true, icon: Trash2, label: t("cover.remove"), onSelect: onDeleteCover }]
              : []),
          ]}
          className="absolute right-1 bottom-1"
          disabled={coverBusy || statusSaving}
          label={t("cover.manage")}
          side="bottom"
          triggerVariant="outline"
          triggerClassName="bg-background"
        >
          <Image aria-hidden="true" />
        </DropdownMenu>
      </div>

      <div className="w-full min-w-0 @min-[640px]:col-start-2 @min-[640px]:row-start-1">
        <h1 className="line-clamp-3 text-[24px] leading-[1.25] font-semibold break-words @min-[640px]:line-clamp-2 @min-[640px]:text-[28px] @min-[1000px]:text-[30px]">
          {detail.title}
        </h1>

        {authorParts.length > 0 ? (
          <p className="mt-2 text-sm text-muted-foreground @min-[640px]:text-[15px]">
            {authorParts.map((part, index) =>
              part.type === "element" ? (
                <Link
                  aria-label={t("detail.searchByAuthor", { name: part.value })}
                  className="rounded-sm underline-offset-2 transition-colors duration-150 ease-out hover:text-accent-text hover:underline focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none"
                  key={`author-${part.value}-${index}`}
                  to={libraryHref(part.value)}
                >
                  {part.value}
                </Link>
              ) : (
                <Fragment key={`author-separator-${index}`}>
                  {part.value}
                </Fragment>
              ),
            )}
          </p>
        ) : null}

        {translators.length > 0 ? (
          <p className="mt-1 text-sm text-muted-foreground">
            {t("detail.translatorLine", { names: translators })}
          </p>
        ) : null}

        {detail.series ? (
          <p className="mt-1 text-sm text-muted-foreground">
            {t("metadata.fields.series")} · {detail.series}
          </p>
        ) : null}

        {detail.isbn !== null ? (
          <BookIsbn isbn={detail.isbn} key={detail.isbn} />
        ) : null}

        {detail.formats.length > 0 || detail.copyCount > 0 ? (
          <div className="mt-3 flex flex-wrap justify-center gap-1 @min-[640px]:justify-start">
            <FormatBadges formats={detail.formats} />
            <PhysicalCopiesBadge count={detail.copyCount} />
          </div>
        ) : null}

        {detail.tags.length > 0 || detail.shelves.length > 0 ? (
          <div className="mt-3 flex flex-wrap justify-center gap-1 @min-[640px]:justify-start">
            <span className="sr-only">{t("detail.tags")}</span>
            <TagLinks tags={detail.tags} />
            <span className="sr-only">{t("detail.shelves")}</span>
            <ShelfBadges shelves={detail.shelves} />
          </div>
        ) : null}
      </div>

      <div className="w-full @min-[640px]:col-start-2 @min-[640px]:row-start-2 @min-[1000px]:col-start-3 @min-[1000px]:row-span-2 @min-[1000px]:row-start-1 @min-[1000px]:flex @min-[1000px]:flex-col @min-[1000px]:justify-start @min-[1000px]:self-stretch">
        <BookDetailActions
          coverBusy={coverBusy}
          detail={detail}
          onDelete={onDelete}
          onEdit={onEdit}
          onMetadata={onMetadata}
          onStatusChange={onStatusChange}
          statusSaving={statusSaving}
        />
      </div>

      <div className="w-full max-w-[70ch] self-start text-left @min-[640px]:col-span-2 @min-[640px]:col-start-1 @min-[640px]:row-start-3 @min-[1000px]:col-span-1 @min-[1000px]:col-start-2 @min-[1000px]:row-start-2">
        <section aria-label={t("detail.description")}>
          <BookDescription
            bookId={detail.id}
            description={detail.description}
            key={detail.id}
          />
        </section>
      </div>
    </header>
  );
}

function BookIsbn({ isbn }: { isbn: string }) {
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
    <div className="mt-1 flex flex-wrap items-center justify-center gap-x-1.5 text-[13px] text-muted-foreground @min-[640px]:justify-start">
      <span>{t("detail.isbn")}</span>
      <span className="tabular-nums">{isbn}</span>
      <IconButton label={t("detail.copyIsbn")} onClick={() => void copyIsbn()}>
        <Copy aria-hidden="true" />
      </IconButton>
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
