import { IconButton, Input } from "@acbooks/ui";
import { LogOut, Menu, Search } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

export interface LibraryTopbarProps {
  accountName: string;
  loggingOut: boolean;
  logoutError?: string;
  onCompositionEnd: (value: string) => void;
  onCompositionStart: () => void;
  onLogout: () => void;
  onSearchChange: (value: string) => void;
  query: string;
}

export function LibraryTopbar({
  accountName,
  loggingOut,
  logoutError,
  onCompositionEnd,
  onCompositionStart,
  onLogout,
  onSearchChange,
  query,
}: LibraryTopbarProps) {
  const { t } = useTranslation("library");
  const [menuOpen, setMenuOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const initial =
    Array.from(accountName.trim())[0]?.toUpperCase() ?? "?";

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
        buttonRef.current?.focus();
      }
    };
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [menuOpen]);

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 pr-4 pl-2">
      <IconButton
        className="md:hidden"
        disabled
        label={t("nav.openMenu")}
        title={t("nav.notAvailable")}
      >
        <Menu aria-hidden="true" />
      </IconButton>

      <div className="relative min-w-0 max-w-[480px] flex-1">
        <Search
          aria-hidden="true"
          className="pointer-events-none absolute top-1/2 left-3 size-[18px] -translate-y-1/2 text-muted-foreground"
          strokeWidth={2}
        />
        <Input
          aria-label={t("header.searchLabel")}
          className="pl-9 [&::-webkit-search-cancel-button]:appearance-none"
          onCompositionEnd={(event) => {
            onCompositionEnd(event.currentTarget.value);
          }}
          onCompositionStart={onCompositionStart}
          onChange={(event) => {
            onSearchChange(event.target.value);
          }}
          placeholder={t("header.searchPlaceholder")}
          type="search"
          value={query}
        />
      </div>

      <div className="hidden flex-1 md:block" />

      <div className="relative" ref={containerRef}>
        <button
          aria-controls="library-account-menu"
          aria-expanded={menuOpen}
          aria-haspopup="menu"
          aria-label={t("header.accountMenu", { username: accountName })}
          className="flex size-9 shrink-0 items-center justify-center rounded-full bg-accent-soft text-sm font-semibold text-accent-text transition-colors duration-150 ease-out hover:bg-accent-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-frame focus-visible:outline-none max-md:size-11"
          onClick={() => {
            setMenuOpen((current) => !current);
          }}
          ref={buttonRef}
          title={accountName}
          type="button"
        >
          <span aria-hidden="true">{initial}</span>
        </button>

        {menuOpen ? (
          <div
            className="absolute top-full right-0 z-20 mt-2 min-w-52 rounded-menu border border-border bg-background p-1 shadow-overlay"
            id="library-account-menu"
            role="menu"
          >
            <div
              className="px-3 py-2 text-xs text-muted-foreground"
              role="presentation"
            >
              {t("header.account", { username: accountName })}
            </div>
            <button
              className="flex w-full items-center gap-2 rounded px-3 py-2 text-left text-sm text-foreground hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none disabled:cursor-not-allowed disabled:text-disabled-foreground disabled:hover:bg-transparent max-md:min-h-11"
              disabled={loggingOut}
              onClick={onLogout}
              role="menuitem"
              type="button"
            >
              <LogOut aria-hidden="true" className="size-[18px]" />
              {loggingOut ? t("account.loggingOut") : t("account.logout")}
            </button>
            {logoutError !== undefined ? (
              <p className="px-3 py-2 text-xs text-destructive-text" role="alert">
                {logoutError}
              </p>
            ) : null}
          </div>
        ) : null}
      </div>
    </header>
  );
}
