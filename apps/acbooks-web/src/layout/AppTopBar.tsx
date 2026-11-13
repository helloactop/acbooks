import type { RefObject } from "react";
import { useTranslation } from "react-i18next";
import { Link, useLocation } from "react-router-dom";

import { GlobalSearch } from "./GlobalSearch";
import { rememberSettingsReturn, settingsHref } from "./routes";

export interface AppTopBarProps {
  accountName: string;
  searchInputRef: RefObject<HTMLInputElement | null>;
}

export function AppTopBar({
  accountName,
  searchInputRef,
}: AppTopBarProps) {
  const { t } = useTranslation("app");
  const location = useLocation();
  const settingsMode = location.pathname === "/settings";
  const initial = Array.from(accountName.trim())[0]?.toUpperCase() ?? "?";

  return (
    <header className="hidden h-14 shrink-0 items-center gap-3 bg-frame px-4 md:flex">
      <div className="w-full max-w-[420px]">
        <GlobalSearch inputRef={searchInputRef} />
      </div>

      <div className="ml-auto flex items-center gap-2">
        <Link
          aria-label={t("account.settingsFor", { username: accountName })}
          className="flex h-8 min-w-0 items-center gap-2 rounded-control px-1.5 transition-colors duration-150 ease-out hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-frame focus-visible:outline-none"
          onClick={() => {
            if (!settingsMode) {
              rememberSettingsReturn(`${location.pathname}${location.search}`);
            }
          }}
          to={settingsHref()}
        >
          <span
            aria-hidden="true"
            className="flex size-6 shrink-0 items-center justify-center rounded-full bg-accent-soft text-[11px] font-semibold text-accent-text"
          >
            {initial}
          </span>
          <span className="max-w-[140px] truncate text-[13px] font-medium">
            {accountName}
          </span>
        </Link>
      </div>
    </header>
  );
}
