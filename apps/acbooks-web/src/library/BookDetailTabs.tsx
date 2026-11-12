import { Info } from "lucide-react";
import { useRef, useState, type KeyboardEvent } from "react";
import { useTranslation } from "react-i18next";

import { BookInfoPanel } from "./BookInfoPanel";
import type { BookDetail } from "./books-api";

type DetailTab = "info" | "reading" | "notes";

const TAB_IDS: readonly DetailTab[] = ["info", "reading", "notes"];
const TAB_KEYS: Record<DetailTab, string> = {
  info: "tabs.info",
  reading: "tabs.reading",
  notes: "tabs.notes",
};

export interface BookDetailTabsProps {
  detail: BookDetail;
}

export function BookDetailTabs({ detail }: BookDetailTabsProps) {
  const { t } = useTranslation("library");
  const [activeTab, setActiveTab] = useState<DetailTab>("info");
  const buttons = useRef<Array<HTMLButtonElement | null>>([]);

  const selectTab = (index: number) => {
    const tab = TAB_IDS[index];
    if (tab === undefined) {
      return;
    }
    setActiveTab(tab);
    buttons.current[index]?.focus();
  };

  const handleKeyDown = (
    event: KeyboardEvent<HTMLButtonElement>,
    index: number,
  ) => {
    let nextIndex: number;
    switch (event.key) {
      case "ArrowRight":
      case "ArrowDown":
        nextIndex = (index + 1) % TAB_IDS.length;
        break;
      case "ArrowLeft":
      case "ArrowUp":
        nextIndex = (index - 1 + TAB_IDS.length) % TAB_IDS.length;
        break;
      case "Home":
        nextIndex = 0;
        break;
      case "End":
        nextIndex = TAB_IDS.length - 1;
        break;
      default:
        return;
    }
    event.preventDefault();
    selectTab(nextIndex);
  };

  return (
    <div className="w-full">
      <div
        aria-label={t("tabs.label")}
        className="flex gap-6 overflow-x-auto border-b border-border [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        role="tablist"
      >
        {TAB_IDS.map((tab, index) => {
          const selected = tab === activeTab;
          return (
            <button
              aria-controls="book-detail-tab-panel"
              aria-selected={selected}
              className={`-mb-px inline-flex h-11 shrink-0 items-center border-b-2 px-1 text-sm font-medium transition-colors duration-150 ease-out focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none ${
                selected
                  ? "border-accent-text text-accent-text"
                  : "border-transparent text-muted-foreground hover:text-foreground"
              }`}
              id={`book-tab-${tab}`}
              key={tab}
              onClick={() => {
                setActiveTab(tab);
              }}
              onKeyDown={(event) => {
                handleKeyDown(event, index);
              }}
              ref={(node) => {
                buttons.current[index] = node;
              }}
              role="tab"
              tabIndex={selected ? 0 : -1}
              type="button"
            >
              {t(TAB_KEYS[tab])}
            </button>
          );
        })}
      </div>

      <div
        aria-labelledby={`book-tab-${activeTab}`}
        className="pt-6"
        id="book-detail-tab-panel"
        role="tabpanel"
        tabIndex={0}
      >
        {activeTab === "info" ? <BookInfoPanel detail={detail} /> : null}
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
