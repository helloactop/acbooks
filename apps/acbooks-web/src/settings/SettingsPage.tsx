import { Button, useLanguage, useTheme } from "@acbooks/ui";
import { ArrowLeft } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { useAppShell } from "../layout/app-shell-context";
import {
  SETTINGS_NAV_GROUPS,
  settingsSectionFromSearch,
  type SettingsNavItem,
} from "../layout/navigation";
import { PageHeader } from "../layout/PageHeader";
import { settingsHref } from "../layout/routes";
import { SettingsGroup, SettingsRow } from "./SettingsGroup";

const SELECT_CLASS =
  "h-10 w-full min-w-44 rounded-control border border-control-border bg-background px-3 text-sm text-foreground transition-colors duration-150 ease-out focus-visible:border-accent-text focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none";

export function SettingsPage() {
  const { t } = useTranslation("app");
  const { theme, setTheme } = useTheme();
  const { language, setLanguage } = useLanguage();
  const {
    accountName,
    loggingOut,
    logoutError,
    onLogout,
    search,
  } = useAppShell();
  const section = settingsSectionFromSearch(search);
  const items = SETTINGS_NAV_GROUPS.flatMap((group) => group.items);
  const currentItem = items.find((item) => item.section === section);

  const renderSection = (item: SettingsNavItem) => {
    if (item.section === "account") {
      return (
        <div className="flex flex-col gap-2" key={item.section}>
          <SettingsGroup
            description={t("settings.accountDescription")}
            title={t(item.labelKey)}
          >
            <SettingsRow label={t("settings.accountName")}>
              <span className="text-sm text-muted-foreground">
                {accountName}
              </span>
            </SettingsRow>
            <SettingsRow label={t("settings.logout")}>
              <Button
                loading={loggingOut}
                onClick={onLogout}
                variant="outline"
              >
                {loggingOut
                  ? t("settings.loggingOut")
                  : t("settings.logout")}
              </Button>
            </SettingsRow>
          </SettingsGroup>
          {logoutError !== undefined ? (
            <p className="text-sm text-destructive-text" role="alert">
              {logoutError}
            </p>
          ) : null}
        </div>
      );
    }

    if (item.section === "appearance") {
      return (
        <SettingsGroup
          description={t("settings.appearanceDescription")}
          key={item.section}
          title={t(item.labelKey)}
        >
          <SettingsRow
            description={t("settings.themeDescription")}
            htmlFor="settings-theme"
            label={t("settings.theme")}
          >
            <select
              className={SELECT_CLASS}
              id="settings-theme"
              onChange={(event) => {
                setTheme(event.target.value as "system" | "light" | "dark");
              }}
              value={theme}
            >
              <option value="system">{t("settings.themeSystem")}</option>
              <option value="light">{t("settings.themeLight")}</option>
              <option value="dark">{t("settings.themeDark")}</option>
            </select>
          </SettingsRow>
        </SettingsGroup>
      );
    }

    return (
      <SettingsGroup
        description={t("settings.languageDescription")}
        key={item.section}
        title={t(item.labelKey)}
      >
        <SettingsRow
          description={t("settings.languageDescriptionShort")}
          htmlFor="settings-language"
          label={t("settings.languageMode")}
        >
          <select
            className={SELECT_CLASS}
            id="settings-language"
            onChange={(event) => {
              setLanguage(
                event.target.value as "system" | "zh-CN" | "en",
              );
            }}
            value={language}
          >
            <option value="system">{t("settings.languageSystem")}</option>
            <option value="zh-CN">{t("settings.languageZh")}</option>
            <option value="en">{t("settings.languageEn")}</option>
          </select>
        </SettingsRow>
      </SettingsGroup>
    );
  };

  return (
    <div className="mx-auto flex w-full max-w-[960px] flex-col gap-5">
      <PageHeader
        description={
          currentItem === undefined
            ? t("settings.description")
            : t(currentItem.descriptionKey)
        }
        title={t("settings.title")}
      />

      {section !== null ? (
        <Link
          className="inline-flex w-fit items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-accent-text focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none lg:hidden"
          to={settingsHref()}
        >
          <ArrowLeft aria-hidden="true" className="size-4" />
          {t("settings.title")}
        </Link>
      ) : null}

      {section === null ? (
        <div className="grid grid-cols-2 gap-3 max-[360px]:grid-cols-1 sm:grid-cols-3 lg:hidden">
          {items.map((item) => {
            const Icon = item.icon;
            const label = t(item.labelKey);
            return (
              <Link
                aria-label={t("settings.openSection", { name: label })}
                className="flex min-h-28 flex-col gap-2 rounded-card border border-border bg-surface p-4 transition-colors duration-150 ease-out hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                key={item.section}
                to={settingsHref(item.section)}
              >
                <Icon
                  aria-hidden="true"
                  className="size-5 text-accent-text"
                  strokeWidth={2}
                />
                <span className="text-sm font-medium">{label}</span>
                <span className="text-xs text-muted-foreground">
                  {t(item.descriptionKey)}
                </span>
              </Link>
            );
          })}
        </div>
      ) : null}

      {section === null ? (
        <div className="hidden flex-col gap-8 lg:flex">
          {items.map(renderSection)}
        </div>
      ) : currentItem !== undefined ? (
        <div className="flex flex-col gap-8">{renderSection(currentItem)}</div>
      ) : null}

      {section === null ? (
        <p className="text-xs text-muted-foreground lg:hidden">
          {t("settings.categoryHint")}
        </p>
      ) : null}
    </div>
  );
}
