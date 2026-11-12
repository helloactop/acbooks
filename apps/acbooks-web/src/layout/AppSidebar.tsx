import { IconButton } from "@acbooks/ui";
import { ArrowLeft, ChevronRight, LogOut, X } from "lucide-react";
import type { RefObject } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { GlobalSearch } from "./GlobalSearch";
import {
  APP_NAV_GROUPS,
  SETTINGS_NAV_GROUPS,
  isNavItemActive,
  isSettingsItemActive,
} from "./navigation";
import {
  homeHref,
  rememberSettingsReturn,
  settingsHref,
  settingsReturnHref,
} from "./routes";

export interface SidebarContentProps {
  accountName: string;
  focusSearchOnOpen?: boolean;
  loggingOut: boolean;
  logoutError?: string;
  onCloseNavigation?: () => void;
  onFocusSearchHandled?: () => void;
  onLogout: () => void;
  onNavigate?: () => void;
  pathname: string;
  search: string;
  searchInputRef?: RefObject<HTMLInputElement | null>;
  variant: "desktop" | "mobile";
}

export function SidebarContent({
  accountName,
  focusSearchOnOpen = false,
  loggingOut,
  logoutError,
  onCloseNavigation,
  onFocusSearchHandled,
  onLogout,
  onNavigate,
  pathname,
  search,
  searchInputRef,
  variant,
}: SidebarContentProps) {
  const { t } = useTranslation("app");
  const settingsMode = pathname === "/settings";
  const itemClass =
    "flex w-full items-center gap-2.5 rounded-control px-3 text-sm transition-colors duration-150 ease-out focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-frame focus-visible:outline-none";

  return (
    <div className="flex h-full min-h-0 flex-col px-4 py-3">
      <div className="flex h-14 shrink-0 items-center gap-2.5">
        <Link
          aria-label="acbooks"
          className="flex min-w-0 items-center gap-2.5 rounded-control focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-frame focus-visible:outline-none"
          onClick={onNavigate}
          to={homeHref()}
        >
          <img
            alt=""
            className="size-8 shrink-0"
            height={32}
            src="/icons/acbooks-192.png"
            width={32}
          />
          <span className="truncate font-semibold">acbooks</span>
        </Link>
        {variant === "mobile" && onCloseNavigation !== undefined ? (
          <IconButton
            className="ml-auto"
            label={t("nav.closeMenu")}
            onClick={onCloseNavigation}
          >
            <X aria-hidden="true" />
          </IconButton>
        ) : null}
      </div>

      {settingsMode ? (
        <>
          <Link
            className="flex h-10 shrink-0 items-center gap-2 rounded-control px-3 text-sm text-muted-foreground transition-colors duration-150 ease-out hover:bg-surface-hover hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
            onClick={onNavigate}
            to={settingsReturnHref()}
          >
            <ArrowLeft aria-hidden="true" className="size-[18px]" />
            {t("settings.back")}
          </Link>

          <nav
            aria-label={t("settings.title")}
            className="mt-4 flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto pb-3"
          >
            {SETTINGS_NAV_GROUPS.map((group) => (
              <div className="flex flex-col gap-1" key={group.labelKey}>
                <div className="px-3 pb-1 text-xs text-muted-foreground">
                  {t(group.labelKey)}
                </div>
                {group.items.map((item) => {
                  const Icon = item.icon;
                  const active = isSettingsItemActive(
                    item,
                    pathname,
                    search,
                  );
                  const label = t(item.labelKey);
                  return (
                    <Link
                      aria-current={active ? "page" : undefined}
                      className={`${itemClass} h-10 ${
                        active
                          ? "bg-accent-soft font-medium text-accent-text"
                          : "text-foreground hover:bg-surface-hover"
                      }`}
                      key={item.section}
                      onClick={onNavigate}
                      to={settingsHref(item.section)}
                    >
                      <Icon
                        aria-hidden="true"
                        className="size-5 shrink-0"
                        strokeWidth={2}
                      />
                      <span className="truncate">{label}</span>
                    </Link>
                  );
                })}
              </div>
            ))}
          </nav>
        </>
      ) : (
        <>
          <div className="mt-2 shrink-0">
            <GlobalSearch
              focusOnMount={focusSearchOnOpen}
              inputRef={searchInputRef}
              onFocusHandled={onFocusSearchHandled}
            />
          </div>

          <nav
            aria-label={t("nav.main")}
            className="mt-4 flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto pb-3"
          >
            {APP_NAV_GROUPS.map((group, groupIndex) => (
              <div
                className="flex flex-col gap-1"
                key={group.labelKey ?? "home"}
              >
                {group.labelKey !== undefined ? (
                  <div
                    className={`px-3 pb-1 text-xs text-muted-foreground ${
                      groupIndex === 0 ? "pt-3" : ""
                    }`}
                  >
                    {t(group.labelKey)}
                  </div>
                ) : null}
                {group.items.map((item) => {
                  const Icon = item.icon;
                  const label = t(item.labelKey);
                  if (item.disabled === true || item.href === undefined) {
                    return (
                      <button
                        className={`${itemClass} h-10 cursor-not-allowed text-disabled-foreground max-md:min-h-11`}
                        disabled
                        key={item.labelKey}
                        title={t("nav.notAvailable")}
                        type="button"
                      >
                        <Icon
                          aria-hidden="true"
                          className="size-5 shrink-0"
                          strokeWidth={2}
                        />
                        <span className="truncate">{label}</span>
                      </button>
                    );
                  }
                  const active = isNavItemActive(item, pathname, search);
                  return (
                    <Link
                      aria-current={active ? "page" : undefined}
                      className={`${itemClass} h-10 max-md:min-h-11 ${
                        active
                          ? "bg-accent-soft font-medium text-accent-text"
                          : "text-foreground hover:bg-surface-hover"
                      }`}
                      key={item.labelKey}
                      onClick={onNavigate}
                      to={item.href}
                    >
                      <Icon
                        aria-hidden="true"
                        className="size-5 shrink-0"
                        strokeWidth={2}
                      />
                      <span className="truncate">{label}</span>
                    </Link>
                  );
                })}
              </div>
            ))}
          </nav>
        </>
      )}

      <div className="mt-auto shrink-0 border-t border-border pt-3">
        <div className="flex items-center gap-2">
          <Link
            aria-label={t("account.settingsFor", { username: accountName })}
            className="flex min-h-11 min-w-0 flex-1 items-center gap-2.5 rounded-control px-2 py-1.5 transition-colors duration-150 ease-out hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-frame focus-visible:outline-none"
            onClick={() => {
              if (!settingsMode) {
                rememberSettingsReturn(`${pathname}${search}`);
              }
              onNavigate?.();
            }}
            to={settingsMode ? settingsHref("account") : settingsHref()}
          >
            <span
              aria-hidden="true"
              className="flex size-9 shrink-0 items-center justify-center rounded-full bg-accent-soft text-sm font-semibold text-accent-text"
            >
              {formatInitial(accountName)}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium">
                {accountName}
              </span>
              <span className="block truncate text-xs text-muted-foreground">
                {t("account.settings")}
              </span>
            </span>
            <ChevronRight
              aria-hidden="true"
              className="size-4 shrink-0 text-muted-foreground"
            />
          </Link>
          <IconButton
            label={loggingOut ? t("account.loggingOut") : t("account.logout")}
            loading={loggingOut}
            onClick={onLogout}
          >
            <LogOut aria-hidden="true" />
          </IconButton>
        </div>
        {logoutError !== undefined ? (
          <p
            className="mt-1.5 break-words px-2 text-xs text-destructive-text"
            role="alert"
          >
            {logoutError}
          </p>
        ) : null}
      </div>
    </div>
  );
}

function formatInitial(name: string): string {
  return Array.from(name.trim())[0]?.toUpperCase() ?? "?";
}
