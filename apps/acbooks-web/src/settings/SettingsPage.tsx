import { Button, Select, useLanguage, useTheme } from "@acbooks/ui";
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
import { useDocumentTitle } from "../layout/use-document-title";
import { SettingsGroup, SettingsRow } from "./SettingsGroup";

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
  useDocumentTitle(
    currentItem === undefined
      ? t("settings.title")
      : `${t(currentItem.labelKey)} · ${t("settings.title")}`,
  );

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
            <Select
              ariaLabel={t("settings.theme")}
              containerClassName="min-w-44"
              id="settings-theme"
              onValueChange={(value) => {
                setTheme(value as "system" | "light" | "dark");
              }}
              options={[
                { label: t("settings.themeSystem"), value: "system" },
                { label: t("settings.themeLight"), value: "light" },
                { label: t("settings.themeDark"), value: "dark" },
              ]}
              value={theme}
            />
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
          <Select
            ariaLabel={t("settings.languageMode")}
            containerClassName="min-w-44"
            id="settings-language"
            onValueChange={(value) => {
              setLanguage(value as "system" | "zh-CN" | "en");
            }}
            options={[
              { label: t("settings.languageSystem"), value: "system" },
              { label: t("settings.languageZh"), value: "zh-CN" },
              { label: t("settings.languageEn"), value: "en" },
            ]}
            value={language}
          />
        </SettingsRow>
      </SettingsGroup>
    );
  };

  return (
    <div className="mx-auto flex w-full max-w-[960px] flex-col gap-4">
      <PageHeader
        description={
          currentItem === undefined
            ? t("settings.description")
            : t(currentItem.descriptionKey)
        }
        title={t("settings.title")}
      />

      <nav
        aria-label={t("settings.personal")}
        className="hidden items-center gap-1 md:flex"
      >
        {items.map((item) => {
          const active = section === item.section;
          return (
            <Link
              aria-current={active ? "page" : undefined}
              className={`inline-flex h-7 items-center rounded-control px-2 text-[13px] transition-colors duration-150 ease-out focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none ${
                active
                  ? "bg-accent-soft font-medium text-accent-text"
                  : "text-muted-foreground hover:bg-surface-hover hover:text-foreground"
              }`}
              key={item.section}
              to={settingsHref(item.section)}
            >
              {t(item.labelKey)}
            </Link>
          );
        })}
      </nav>

      {section !== null ? (
        <Link
          className="inline-flex w-fit items-center gap-1.5 text-[13px] font-medium text-muted-foreground hover:text-accent-text focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none md:hidden"
          to={settingsHref()}
        >
          <ArrowLeft aria-hidden="true" className="size-4" />
          {t("settings.title")}
        </Link>
      ) : null}

      {section === null ? (
        <div className="grid grid-cols-2 gap-2 max-[360px]:grid-cols-1 sm:grid-cols-3 md:hidden">
          {items.map((item) => {
            const Icon = item.icon;
            const label = t(item.labelKey);
            return (
              <Link
                aria-label={t("settings.openSection", { name: label })}
                className="flex min-h-24 flex-col gap-1.5 rounded-card border border-border bg-surface p-3 transition-colors duration-150 ease-out hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                key={item.section}
                to={settingsHref(item.section)}
              >
                <Icon
                  aria-hidden="true"
                  className="size-4 text-accent-text"
                  strokeWidth={2}
                />
                <span className="text-[13px] font-medium">{label}</span>
                <span className="text-xs text-muted-foreground">
                  {t(item.descriptionKey)}
                </span>
              </Link>
            );
          })}
        </div>
      ) : null}

      {section === null ? (
        <div className="hidden flex-col gap-6 md:flex">
          {items.map(renderSection)}
        </div>
      ) : currentItem !== undefined ? (
        <div className="flex flex-col gap-6">{renderSection(currentItem)}</div>
      ) : null}

      {section === null ? (
        <p className="text-xs text-muted-foreground md:hidden">
          {t("settings.categoryHint")}
        </p>
      ) : null}
    </div>
  );
}
