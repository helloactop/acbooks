import { Button, SegmentedControl, useLanguage, useTheme } from "@acbooks/ui";
import { ArrowLeft, ChevronRight, Monitor, Moon, Sun } from "lucide-react";
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
import { PageContainer } from "../layout/PageContainer";
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
          <SettingsGroup ariaLabel={t(item.labelKey)}>
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
        <SettingsGroup ariaLabel={t(item.labelKey)} key={item.section}>
          <SettingsRow
            description={t("settings.themeDescription")}
            label={t("settings.theme")}
          >
            <SegmentedControl
              ariaLabel={t("settings.theme")}
              className="h-auto max-w-full flex-wrap [&>button]:flex-1 [&>button]:gap-1.5 max-md:[&>button]:px-1"
              onValueChange={setTheme}
              options={[
                { label: <><Monitor aria-hidden="true" />{t("settings.themeSystem")}</>, value: "system" },
                { label: <><Sun aria-hidden="true" />{t("settings.themeLight")}</>, value: "light" },
                { label: <><Moon aria-hidden="true" />{t("settings.themeDark")}</>, value: "dark" },
              ]}
              value={theme}
              variant="boxed"
            />
          </SettingsRow>
        </SettingsGroup>
      );
    }

    return (
      <SettingsGroup ariaLabel={t(item.labelKey)} key={item.section}>
        <SettingsRow
          description={t("settings.languageDescriptionShort")}
          label={t("settings.languageMode")}
        >
          <SegmentedControl
            ariaLabel={t("settings.languageMode")}
            className="h-auto max-w-full flex-wrap [&>button]:flex-1 max-md:[&>button]:px-1"
            onValueChange={setLanguage}
            options={[
              { label: t("settings.languageSystem"), value: "system" },
              { label: t("settings.languageZh"), value: "zh-CN" },
              { label: t("settings.languageEn"), value: "en" },
            ]}
            value={language}
            variant="boxed"
          />
        </SettingsRow>
      </SettingsGroup>
    );
  };

  return (
    <PageContainer width="form">
      {currentItem !== undefined ? (
        <Link
          className="inline-flex min-h-8 w-fit items-center gap-2 rounded-control text-[13px] font-medium text-muted-foreground hover:text-accent-text focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none max-md:min-h-11"
          to={settingsHref()}
        >
          <ArrowLeft aria-hidden="true" className="size-4" />
          {t("settings.backToSettings")}
        </Link>
      ) : null}
      <PageHeader title={t(currentItem?.labelKey ?? "settings.title")} />

      {currentItem === undefined ? (
        <nav aria-label={t("settings.personal")} className="flex flex-col gap-2">
          {items.map(({ descriptionKey, icon: Icon, labelKey, section }) => (
            <Link
              aria-label={t("settings.openSection", { name: t(labelKey) })}
              className="flex min-h-16 items-center gap-2 rounded-card border border-border px-3 py-3 sm:gap-3 sm:px-4 transition-colors hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
              key={section}
              to={settingsHref(section)}
            >
              <span className="grid size-9 shrink-0 place-items-center rounded-control bg-accent-soft text-accent-text">
                <Icon aria-hidden="true" className="size-4" />
              </span>
              <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="text-sm font-semibold wrap-anywhere">{t(labelKey)}</span>
                <span className="text-xs wrap-anywhere text-muted-foreground">{t(descriptionKey)}</span>
              </span>
              <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
            </Link>
          ))}
        </nav>
      ) : renderSection(currentItem)}
    </PageContainer>
  );
}
