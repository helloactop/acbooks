import { Button, Spinner } from "@acbooks/ui";
import { useId } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { Pagination } from "../library/Pagination";
import type { RecordAction } from "./ReadingRecordDialog";
import type { TimelinePage } from "./stats-api";
import { useStatsFormat } from "./use-stats-format";

export function ReadingTimeline({ bookId, timeZone, page, failed, onRetry, onOffset, onAction }: {
  bookId?: string; timeZone: string; page?: TimelinePage; failed: boolean;
  onRetry: () => void; onOffset: (offset: number) => void; onAction: (action: RecordAction) => void;
}) {
  const { t } = useTranslation("stats");
  const titleId = useId();
  const format = useStatsFormat();
  const date = new Intl.DateTimeFormat(format.lang, { dateStyle: "full", timeZone });
  const timestamp = new Intl.DateTimeFormat(format.lang, { dateStyle: "medium", timeStyle: "short", timeZone });
  const groups = new Map<string, TimelinePage["items"]>();
  for (const item of page?.items ?? []) {
    const key = date.format(item.startedAt);
    const group = groups.get(key) ?? [];
    group.push(item);
    groups.set(key, group);
  }

  return <section aria-labelledby={titleId} className="rounded-panel border border-border bg-background p-3 sm:p-4">
    <header className="flex flex-wrap items-center justify-between gap-2">
      <h3 id={titleId} className="font-semibold">{t("timeline")}</h3>
      <Button variant="soft" onClick={() => onAction({ type: "add" })}>{t("addRecord")}</Button>
    </header>
    {page ? <>
      {page.items.length === 0 && <p className="mt-2 text-sm text-muted-foreground">{t("empty")}</p>}
      {[...groups].map(([day, items]) => <div key={day} className="mt-4">
        <h4 className="text-sm font-medium text-muted-foreground">{day}</h4>
        <ul className="divide-y divide-border">
          {items.map(item => <li key={item.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
            <div className="min-w-0">
              {!bookId && <Link className="block break-words text-accent-text hover:underline" to={`/library/books/${item.bookId}`}>{item.bookTitle}</Link>}
              <time dateTime={new Date(item.startedAt).toISOString()}>{timestamp.format(item.startedAt)}</time>
              {item.endedAt !== null && <> – <time dateTime={new Date(item.endedAt).toISOString()}>{timestamp.format(item.endedAt)}</time></>}
              <p className="text-muted-foreground">
                {t(item.source === "manual" ? "manualSource" : "automaticSource")}
                {item.fileFormat && <> · {item.fileFormat.toUpperCase()}</>}
                {" · "}{format.duration(item.activeSeconds)}
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-1">
              <Button variant="ghost" onClick={() => onAction({ type: "edit", session: item })}>{t("edit")}</Button>
              <Button variant="danger" onClick={() => onAction({ type: "delete", session: item })}>{t("delete")}</Button>
            </div>
          </li>)}
        </ul>
      </div>)}
      {page.total > page.limit && <Pagination {...page} onOffsetChange={onOffset} />}
    </> : failed ? <div role="alert" className="flex flex-wrap items-center gap-2 py-3">
      {t("failed")} <Button variant="outline" onClick={onRetry}>{t("retry")}</Button>
    </div> : <div role="status" className="flex items-center gap-2 py-3"><Spinner />{t("loading")}</div>}
  </section>;
}
