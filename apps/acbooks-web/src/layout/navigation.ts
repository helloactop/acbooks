import {
  BookMarked,
  BookOpen,
  ChartBar,
  FileText,
  History,
  House,
  Languages,
  LibraryBig,
  SunMoon,
  UserRound,
  type LucideIcon,
} from "lucide-react";

import { homeHref, libraryMediaHref } from "./routes";

export interface NavItem {
  disabled?: boolean;
  href?: string;
  icon: LucideIcon;
  labelKey: string;
}

export interface NavGroup {
  items: readonly NavItem[];
  labelKey?: string;
}

export const APP_NAV_GROUPS: readonly NavGroup[] = [
  {
    items: [{ href: homeHref(), icon: House, labelKey: "nav.home" }],
  },
  {
    labelKey: "nav.library",
    items: [
      {
        href: libraryMediaHref("all"),
        icon: LibraryBig,
        labelKey: "nav.allBooks",
      },
      {
        href: libraryMediaHref("physical"),
        icon: BookOpen,
        labelKey: "nav.physical",
      },
      {
        href: libraryMediaHref("digital"),
        icon: FileText,
        labelKey: "nav.digital",
      },
    ],
  },
  {
    labelKey: "nav.shelves",
    items: [
      {
        disabled: true,
        icon: BookMarked,
        labelKey: "nav.bookShelf",
      },
    ],
  },
  {
    labelKey: "nav.reading",
    items: [
      { disabled: true, icon: History, labelKey: "nav.history" },
      { disabled: true, icon: ChartBar, labelKey: "nav.statistics" },
    ],
  },
];

export type SettingsSection = "account" | "appearance" | "language";

export interface SettingsNavItem {
  descriptionKey: string;
  icon: LucideIcon;
  labelKey: string;
  section: SettingsSection;
}

export interface SettingsNavGroup {
  items: readonly SettingsNavItem[];
  labelKey: string;
}

export const SETTINGS_NAV_GROUPS: readonly SettingsNavGroup[] = [
  {
    labelKey: "settings.personal",
    items: [
      {
        descriptionKey: "settings.accountDescription",
        icon: UserRound,
        labelKey: "settings.account",
        section: "account",
      },
      {
        descriptionKey: "settings.appearanceDescription",
        icon: SunMoon,
        labelKey: "settings.appearance",
        section: "appearance",
      },
      {
        descriptionKey: "settings.languageDescription",
        icon: Languages,
        labelKey: "settings.language",
        section: "language",
      },
    ],
  },
];

export function settingsSectionFromSearch(
  search: string,
): SettingsSection | null {
  const value = new URLSearchParams(search).get("section");
  return value === "account" ||
    value === "appearance" ||
    value === "language"
    ? value
    : null;
}

function currentMediaType(search: string): "all" | "physical" | "digital" {
  const value = new URLSearchParams(search).get("mediaType");
  return value === "physical" || value === "digital" ? value : "all";
}

export function isNavItemActive(
  item: NavItem,
  pathname: string,
  search: string,
): boolean {
  if (item.href === undefined) {
    return false;
  }
  const target = new URL(item.href, window.location.origin);
  if (target.pathname === "/library") {
    if (pathname.startsWith("/library/books/")) {
      return target.searchParams.get("mediaType") === "all";
    }
    if (pathname !== "/library") {
      return false;
    }
    return (
      (target.searchParams.get("mediaType") ?? "all") ===
      currentMediaType(search)
    );
  }
  return pathname === target.pathname;
}

export function isSettingsItemActive(
  item: SettingsNavItem,
  pathname: string,
  search: string,
): boolean {
  return (
    pathname === "/settings" &&
    settingsSectionFromSearch(search) === item.section
  );
}
