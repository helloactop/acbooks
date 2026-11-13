import {
  House,
  Languages,
  LibraryBig,
  SunMoon,
  UserRound,
  type LucideIcon,
} from "lucide-react";

import { homeHref, libraryHref } from "./routes";

export interface NavItem {
  href: string;
  icon: LucideIcon;
  labelKey: string;
}

export const APP_NAV_ITEMS: readonly NavItem[] = [
  { href: homeHref(), icon: House, labelKey: "nav.home" },
  { href: libraryHref(), icon: LibraryBig, labelKey: "nav.library" },
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

export function isNavItemActive(item: NavItem, pathname: string): boolean {
  if (item.href === homeHref()) {
    return pathname === "/home" || pathname === "/home/";
  }
  return pathname === item.href || pathname.startsWith(`${item.href}/`);
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
