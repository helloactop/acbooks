import {
  HardDrive,
  House,
  Languages,
  LibraryBig,
  MapPin,
  ListChecks,
  BookOpen,
  ChartBar,
  KeyRound,
  Laptop,
  Shield,
  SunMoon,
  UserRound,
  type LucideIcon,
} from "lucide-react";

import { homeHref, libraryHref, locationsHref, storageHref } from "./routes";

export interface NavItem {
  href: string;
  icon: LucideIcon;
  labelKey: string;
}

export const APP_NAV_GROUPS: readonly {
  labelKey: string;
  items: readonly NavItem[];
}[] = [
  {
    labelKey: "nav.contentGroup",
    items: [
      { href: homeHref(), icon: House, labelKey: "nav.home" },
      { href: libraryHref(), icon: LibraryBig, labelKey: "nav.library" },
      { href: "/stats", icon: ChartBar, labelKey: "nav.stats" },
    ],
  },
  {
    labelKey: "nav.managementGroup",
    items: [
      { href: "/organization", icon: ListChecks, labelKey: "nav.organization" },
      { href: locationsHref(), icon: MapPin, labelKey: "nav.locations" },
      { href: storageHref(), icon: HardDrive, labelKey: "nav.storage" },
    ],
  },
];

export type SettingsSection =
  | "account" | "appearance" | "language" | "metadata"
  | "password" | "devices" | "credentials";

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
      {
        descriptionKey: "settings.metadataDescription",
        icon: BookOpen,
        labelKey: "settings.metadata",
        section: "metadata",
      },
    ],
  },
  {
    labelKey: "settings.security",
    items: [
      { section: "password", icon: Shield, labelKey: "settings.password", descriptionKey: "settings.passwordDescription" },
      { section: "devices", icon: Laptop, labelKey: "settings.devices", descriptionKey: "settings.devicesDescription" },
      { section: "credentials", icon: KeyRound, labelKey: "settings.credentials", descriptionKey: "settings.credentialsDescription" },
    ],
  },
];

export function settingsSectionFromSearch(
  search: string,
): SettingsSection | null {
  const value = new URLSearchParams(search).get("section");
  return SETTINGS_NAV_GROUPS.flatMap(group => group.items)
    .find(item => item.section === value)?.section ?? null;
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
