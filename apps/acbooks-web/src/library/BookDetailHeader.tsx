import { useLanguage } from "@acbooks/ui";
import { Fragment } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { BookCover } from "./BookCover";
import { BookDescription } from "./BookDescription";
import { BookDetailActions } from "./BookDetailActions";
import { ShelfBadges, TagLinks } from "./BookMeta";
import type { BookDetail, ReadingStatus } from "./books-api";
import { libraryHref } from "../layout/routes";
import { joinNames } from "./presentation";

export interface BookDetailHeaderProps {
  detail: BookDetail;
  onEdit: () => void;
  onStatusChange: (status: ReadingStatus) => void;
  statusSaving: boolean;
}

export function BookDetailHeader({
  detail,
  onEdit,
  onStatusChange,
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
    <header className="flex w-full flex-col items-center gap-5 text-center @min-[640px]:grid @min-[640px]:grid-cols-[132px_minmax(0,1fr)] @min-[640px]:items-start @min-[640px]:gap-x-6 @min-[640px]:gap-y-4 @min-[640px]:text-left @min-[1000px]:grid-cols-[150px_minmax(0,1fr)_230px]">
      <div className="shrink-0 @min-[640px]:col-start-1 @min-[640px]:row-start-1 @min-[1000px]:row-span-2">
        <BookCover
          authorClassName="text-[11px]"
          authorLabel={authorLabel}
          book={detail}
          className="w-[132px] @min-[1000px]:w-[150px]"
          titleClassName="text-[22px] leading-[1.25] @min-[1000px]:text-2xl"
        />
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

        {detail.isbn !== null ? (
          <p className="mt-2 text-[13px] text-muted-foreground">
            <span className="mr-1.5">{t("detail.isbn")}</span>
            <span className="tabular-nums">{detail.isbn}</span>
          </p>
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
          detail={detail}
          onEdit={onEdit}
          onStatusChange={onStatusChange}
          statusSaving={statusSaving}
        />
      </div>

      <div className="w-full max-w-[70ch] text-left @min-[640px]:col-span-2 @min-[640px]:col-start-1 @min-[640px]:row-start-3 @min-[1000px]:col-span-1 @min-[1000px]:col-start-2 @min-[1000px]:row-start-2">
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
