import { IconButton, cn } from "@acbooks/ui";
import { ChevronRight, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { GlobalSearch } from "./GlobalSearch";
import { APP_NAV_ITEMS, isNavItemActive } from "./navigation";
import {
  homeHref,
  rememberSettingsReturn,
  settingsHref,
} from "./routes";

export interface AppSidebarProps {
  accountName: string;
  focusSearchOnOpen?: boolean;
  onClose?: () => void;
  onFocusSearchHandled?: () => void;
  onNavigate?: () => void;
  pathname: string;
  replaceNavigation?: boolean;
}

export function AppSidebar({
  accountName,
  focusSearchOnOpen = false,
  onClose,
  onFocusSearchHandled,
  onNavigate,
  pathname,
  replaceNavigation = false,
}: AppSidebarProps) {
  const { t } = useTranslation("app");
  const initial = Array.from(accountName.trim())[0]?.toUpperCase() ?? "?";
  const rowClass =
    "flex w-full items-center gap-2 rounded-control px-2 text-[13px] transition-colors duration-150 ease-out focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-frame focus-visible:outline-none";
  const itemClass = `${rowClass} h-8 max-md:min-h-11`;

  return (
    <div className="flex h-full min-h-0 flex-col px-2 py-2">
      <div className="flex h-11 shrink-0 items-center gap-2 px-1">
        <Link
          aria-label="acbooks"
          className="flex min-w-0 flex-1 items-center gap-2 rounded-control focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-frame focus-visible:outline-none"
          onClick={onNavigate}
          replace={replaceNavigation}
          to={homeHref()}
        >
          <img
            alt=""
            className="size-7 shrink-0"
            height={28}
            src="/icons/acbooks-logo.png"
            width={28}
          />
          <span className="truncate text-sm font-semibold">acbooks</span>
        </Link>
        <IconButton
          className="ml-auto size-11 shrink-0"
          label={t("nav.closeMenu")}
          onClick={onClose}
        >
          <X aria-hidden="true" />
        </IconButton>
      </div>

      <div className="mt-2 shrink-0">
        <GlobalSearch
          focusOnMount={focusSearchOnOpen}
          onFocusHandled={onFocusSearchHandled}
        />
      </div>

      <nav
        aria-label={t("nav.main")}
        className="mt-4 flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto pb-2"
      >
        {APP_NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const label = t(item.labelKey);
          const active = isNavItemActive(item, pathname);
          return (
            <Link
              aria-current={active ? "page" : undefined}
              className={cn(
                itemClass,
                active
                  ? "bg-accent-soft font-medium text-accent-text"
                  : "text-foreground hover:bg-surface-hover",
              )}
              key={item.href}
              onClick={onNavigate}
              replace={replaceNavigation}
              to={item.href}
            >
              <Icon
                aria-hidden="true"
                className="size-4 shrink-0"
                strokeWidth={2}
              />
              <span className="truncate">{label}</span>
            </Link>
          );
        })}
      </nav>

      <div className="mt-2 flex shrink-0 flex-col gap-1 border-t border-border pt-2">
        <Link
          aria-label={t("account.settingsFor", { username: accountName })}
          className={cn(rowClass, "h-10 px-1.5 hover:bg-surface-hover")}
          onClick={() => {
            rememberSettingsReturn();
            onNavigate?.();
          }}
          replace={replaceNavigation}
          to={settingsHref()}
        >
          <span
            aria-hidden="true"
            className="flex size-6 shrink-0 items-center justify-center rounded-full bg-accent-soft text-[11px] font-semibold text-accent-text"
          >
            {initial}
          </span>
          <span className="min-w-0 flex-1 truncate text-left font-medium">
            {accountName}
          </span>
          <ChevronRight
            aria-hidden="true"
            className="size-4 shrink-0 text-muted-foreground"
          />
        </Link>
      </div>
    </div>
  );
}
