import { Tabs } from "@acbooks/ui";
import { Info } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";

import { BookInfoPanel } from "./BookInfoPanel";
import type { BookDetail } from "./books-api";
import type { BookCopy } from "./copies-api";

type DetailTab = "info" | "reading" | "notes";

const TAB_IDS: readonly DetailTab[] = ["info", "reading", "notes"];
const TAB_KEYS: Record<DetailTab, string> = {
  info: "tabs.info",
  reading: "tabs.reading",
  notes: "tabs.notes",
};

export interface BookDetailTabsProps {
  detail: BookDetail;
  onAddCopy?: () => void;
  onDeleteCopy?: (copy: BookCopy) => void;
  onEditCopy?: (copy: BookCopy) => void;
}

export function BookDetailTabs({
  detail,
  onAddCopy,
  onDeleteCopy,
  onEditCopy,
}: BookDetailTabsProps) {
  const { t } = useTranslation("library");
  const [activeTab, setActiveTab] = useState<DetailTab>("info");
  const items = TAB_IDS.map((tab) => ({
    label: t(TAB_KEYS[tab]),
    value: tab,
  }));

  return (
    <div className="w-full">
      <Tabs
        ariaLabel={t("tabs.label")}
        idPrefix="book-tab"
        items={items}
        onValueChange={setActiveTab}
        panelId="book-detail-tab-panel"
        value={activeTab}
      />

      <div
        aria-labelledby={`book-tab-${activeTab}`}
        className="pt-6"
        id="book-detail-tab-panel"
        role="tabpanel"
        tabIndex={0}
      >
        {activeTab === "info" ? (
          <BookInfoPanel
            detail={detail}
            onAddCopy={onAddCopy}
            onDeleteCopy={onDeleteCopy}
            onEditCopy={onEditCopy}
          />
        ) : null}
        {activeTab === "reading" ? (
          <UnavailablePanel
            description={t("tabs.readingDescription")}
            title={t("tabs.readingTitle")}
          />
        ) : null}
        {activeTab === "notes" ? (
          <UnavailablePanel
            description={t("tabs.notesDescription")}
            title={t("tabs.notesTitle")}
          />
        ) : null}
      </div>
    </div>
  );
}

function UnavailablePanel({
  description,
  title,
}: {
  description: string;
  title: string;
}) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-card border border-border bg-surface px-6 py-10 text-center">
      <Info
        aria-hidden="true"
        className="size-7 text-muted-foreground"
        strokeWidth={2}
      />
      <h2 className="text-base font-semibold">{title}</h2>
      <p className="max-w-[42ch] text-sm text-muted-foreground">{description}</p>
    </div>
  );
}
