import { IconButton, Input } from "@acbooks/ui";
import { Menu, Search } from "lucide-react";
import { useTranslation } from "react-i18next";

export interface LibraryTopbarProps {
  accountName: string;
  onCompositionEnd: (value: string) => void;
  onCompositionStart: () => void;
  onSearchChange: (value: string) => void;
  query: string;
}

export function LibraryTopbar({
  accountName,
  onCompositionEnd,
  onCompositionStart,
  onSearchChange,
  query,
}: LibraryTopbarProps) {
  const { t } = useTranslation("library");
  const initial =
    Array.from(accountName.trim())[0]?.toUpperCase() ?? "?";

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

      <div
        className="flex size-9 shrink-0 items-center justify-center rounded-full bg-accent-soft text-sm font-semibold text-accent-text"
        title={accountName}
      >
        <span aria-hidden="true">{initial}</span>
        <span className="sr-only">
          {t("header.account", { username: accountName })}
        </span>
      </div>
    </header>
  );
}
