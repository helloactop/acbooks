import {
  BookMarked,
  ChartBar,
  HardDrive,
  History,
  House,
  LibraryBig,
  ListChecks,
  Settings,
  type LucideIcon,
} from "lucide-react";
import { useTranslation } from "react-i18next";

interface NavItem {
  href?: string;
  icon: LucideIcon;
  labelKey: string;
}

interface NavGroup {
  items: readonly NavItem[];
  labelKey: string;
}

const NAV_GROUPS: readonly NavGroup[] = [
  {
    labelKey: "nav.library",
    items: [
      { icon: House, labelKey: "nav.home" },
      { href: "/library", icon: LibraryBig, labelKey: "nav.allBooks" },
      { icon: BookMarked, labelKey: "nav.shelves" },
    ],
  },
  {
    labelKey: "nav.reading",
    items: [
      { icon: History, labelKey: "nav.history" },
      { icon: ChartBar, labelKey: "nav.statistics" },
    ],
  },
  {
    labelKey: "nav.management",
    items: [
      { icon: HardDrive, labelKey: "nav.storage" },
      { icon: ListChecks, labelKey: "nav.jobs" },
      { icon: Settings, labelKey: "nav.settings" },
    ],
  },
];

export function LibrarySidebar() {
  const { t } = useTranslation("library");

  return (
    <aside className="w-60 shrink-0 overflow-y-auto px-4 py-3 max-lg:w-16 max-lg:px-2 max-md:hidden">
      <a
        aria-label="acbooks"
        className="flex h-14 items-center gap-2.5 rounded-control px-3 font-semibold max-lg:justify-center max-lg:px-0"
        href="/library"
      >
        <img
          alt=""
          className="size-8"
          height={32}
          src="/icons/acbooks-192.png"
          width={32}
        />
        <span className="max-lg:hidden">acbooks</span>
      </a>

      <nav aria-label={t("nav.main")} className="flex flex-col gap-1">
        {NAV_GROUPS.map((group, groupIndex) => (
          <div key={group.labelKey} className="flex flex-col gap-1">
            <div
              className={
                groupIndex === 0
                  ? "px-3 pt-3 pb-1 text-xs text-muted-foreground max-lg:hidden"
                  : "px-3 pt-4 pb-1 text-xs text-muted-foreground max-lg:hidden"
              }
            >
              {t(group.labelKey)}
            </div>
            {group.items.map((item) => {
              const Icon = item.icon;
              const label = t(item.labelKey);
              if (item.href !== undefined) {
                return (
                  <a
                    key={item.labelKey}
                    aria-current="page"
                    className="flex h-10 w-full items-center gap-2 rounded-control bg-accent-soft px-3 text-sm font-medium text-accent-text max-lg:justify-center max-lg:px-0"
                    href={item.href}
                  >
                    <Icon aria-hidden="true" className="size-5" strokeWidth={2} />
                    <span className="max-lg:hidden">{label}</span>
                  </a>
                );
              }
              return (
                <button
                  key={item.labelKey}
                  className="flex h-10 w-full items-center gap-2 rounded-control px-3 text-sm text-disabled-foreground disabled:cursor-not-allowed max-lg:justify-center max-lg:px-0"
                  disabled
                  title={t("nav.notAvailable")}
                  type="button"
                >
                  <Icon aria-hidden="true" className="size-5" strokeWidth={2} />
                  <span className="max-lg:hidden">{label}</span>
                </button>
              );
            })}
          </div>
        ))}
      </nav>
    </aside>
  );
}
