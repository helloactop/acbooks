import {
  Button,
  Field,
  IconButton,
  Input,
  MultiValueInput,
  PasswordInput,
  SegmentedControl,
  Spinner,
  Tabs,
  Textarea,
  useLanguage,
  useTheme,
  type LanguageMode,
  type SegmentedControlOption,
  type ThemeMode,
} from "@acbooks/ui";
import {
  CircleAlert,
  CircleCheck,
  House,
  Languages,
  LayoutGrid,
  List,
  LoaderCircle,
  Plus,
  Search,
  Settings2,
  SlidersHorizontal,
  Trash2,
  TriangleAlert,
  type LucideIcon,
} from "lucide-react";
import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";

interface IconSample {
  className?: string;
  icon: LucideIcon;
  labelKey: string;
}

const ICON_SAMPLES: readonly IconSample[] = [
  { icon: House, labelKey: "icons.samples.home", className: "size-5" },
  { icon: Search, labelKey: "icons.samples.search" },
  { icon: Plus, labelKey: "icons.samples.add" },
  { icon: SlidersHorizontal, labelKey: "icons.samples.filter" },
  { icon: LayoutGrid, labelKey: "icons.samples.grid" },
  { icon: List, labelKey: "icons.samples.list" },
  { icon: Settings2, labelKey: "icons.samples.settings" },
  { icon: Languages, labelKey: "icons.samples.language" },
  {
    icon: CircleCheck,
    labelKey: "icons.samples.success",
    className: "size-[18px] text-success",
  },
  {
    icon: TriangleAlert,
    labelKey: "icons.samples.warning",
    className: "size-[18px] text-warning",
  },
  {
    icon: CircleAlert,
    labelKey: "icons.samples.error",
    className: "size-[18px] text-destructive-text",
  },
  {
    icon: LoaderCircle,
    labelKey: "icons.samples.loading",
    className: "size-[18px] animate-spin motion-reduce:animate-none",
  },
];

interface IconLabelProps {
  className?: string;
  icon: LucideIcon;
  label: string;
}

function IconLabel({ icon: Icon, label, className }: IconLabelProps) {
  return (
    <div className="flex items-center gap-2 text-sm text-muted-foreground">
      <Icon
        aria-hidden="true"
        className={className ?? "size-[18px] text-foreground"}
        strokeWidth={2}
      />
      <span>{label}</span>
    </div>
  );
}

interface PreviewSectionProps {
  children: ReactNode;
  description?: string;
  id: string;
  title: string;
}

function PreviewSection({
  children,
  description,
  id,
  title,
}: PreviewSectionProps) {
  const headingId = `${id}-title`;
  return (
    <section
      aria-labelledby={headingId}
      className="rounded-panel border border-border bg-background p-6"
      id={id}
    >
      <h2 className="text-xl font-semibold" id={headingId}>
        {title}
      </h2>
      {description ? (
        <p className="mt-1 text-sm text-muted-foreground">{description}</p>
      ) : null}
      <div className="mt-4">{children}</div>
    </section>
  );
}

export function PreviewPage() {
  const { resolvedTheme, setTheme, theme } = useTheme();
  const { language, resolvedLanguage, setLanguage } = useLanguage();
  const { t } = useTranslation("preview");
  const { t: tCommon } = useTranslation("common");
  const [previewValues, setPreviewValues] = useState(["示例"]);
  const [previewTab, setPreviewTab] = useState("info");

  const themeOptions: readonly SegmentedControlOption<ThemeMode>[] = [
    { value: "system", label: tCommon("theme.system") },
    { value: "light", label: tCommon("theme.light") },
    { value: "dark", label: tCommon("theme.dark") },
  ];
  const languageOptions: readonly SegmentedControlOption<LanguageMode>[] = [
    { value: "system", label: tCommon("language.system") },
    { value: "zh-CN", label: tCommon("language.zhCN") },
    { value: "en", label: tCommon("language.en") },
  ];
  const languageLabel = tCommon(
    language === "system"
      ? "language.system"
      : language === "zh-CN"
        ? "language.zhCN"
        : "language.en",
  );

  return (
    <main className="min-h-dvh bg-frame px-4 py-10 text-foreground sm:px-6">
      <div className="mx-auto flex w-full max-w-4xl flex-col gap-8">
        <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-accent-text">acbooks</p>
            <h1 className="mt-1 text-[28px] leading-tight font-semibold">
              {t("page.title")}
            </h1>
            <p className="mt-2 text-sm text-muted-foreground">
              {t("page.description")}
            </p>
          </div>
          <div className="flex w-full flex-col items-start gap-3 sm:w-auto sm:shrink-0 sm:items-end">
            <div className="flex flex-col items-start gap-2 sm:items-end">
              <SegmentedControl
                ariaLabel={tCommon("theme.ariaLabel")}
                onValueChange={setTheme}
                options={themeOptions}
                value={theme}
              />
              <span
                aria-live="polite"
                className="text-xs text-muted-foreground"
              >
                {t("currentTheme", {
                  mode: tCommon(`theme.${theme}`),
                  resolved: tCommon(`theme.${resolvedTheme}`),
                })}
              </span>
            </div>
            <div className="flex flex-col items-start gap-2 sm:items-end">
              <SegmentedControl
                ariaLabel={tCommon("language.ariaLabel")}
                onValueChange={setLanguage}
                options={languageOptions}
                value={language}
              />
              <span
                aria-live="polite"
                className="text-xs text-muted-foreground"
              >
                {t("currentLanguage", {
                  language: languageLabel,
                  code: resolvedLanguage,
                })}
              </span>
            </div>
          </div>
        </header>

        <PreviewSection
          description={t("sections.buttons.description")}
          id="buttons"
          title={t("sections.buttons.title")}
        >
          <div className="flex flex-wrap items-center gap-3">
            <Button variant="soft">{t("buttons.primary")}</Button>
            <Button>{t("buttons.save")}</Button>
            <Button variant="outline">{t("buttons.filter")}</Button>
            <Button variant="ghost">{t("buttons.cancel")}</Button>
            <Button variant="danger">{t("buttons.delete")}</Button>
            <Button variant="danger-confirm">{t("buttons.deleteConfirm")}</Button>
          </div>
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <Button size="sm">{t("buttons.small")}</Button>
            <Button size="md">{t("buttons.medium")}</Button>
            <Button size="lg">{t("buttons.large")}</Button>
            <Button disabled>{t("buttons.disabled")}</Button>
            <Button loading>{t("buttons.loading")}</Button>
            <IconButton label={t("buttons.settings")}>
              <Settings2 aria-hidden="true" />
            </IconButton>
            <IconButton label={t("buttons.delete")} variant="danger">
              <Trash2 aria-hidden="true" />
            </IconButton>
            <Button asChild variant="outline">
              <a href="#forms">{t("buttons.jumpToForms")}</a>
            </Button>
          </div>
        </PreviewSection>

        <PreviewSection
          description={t("sections.forms.description")}
          id="forms"
          title={t("sections.forms.title")}
        >
          <div className="grid max-w-2xl gap-5 md:grid-cols-2">
            <Field
              description={t("forms.authDescription")}
              label={t("forms.username")}
            >
              <Input autoComplete="username" controlSize="auth" />
            </Field>
            <Field
              description={t("forms.authDescription")}
              label={t("forms.password")}
              required
            >
              <PasswordInput
                autoComplete="current-password"
                controlSize="auth"
              />
            </Field>
            <Field
              description={t("forms.initializationCodeDescription")}
              error={t("forms.initializationCodeError")}
              label={t("forms.initializationCode")}
            >
              <Input autoComplete="one-time-code" controlSize="auth" />
            </Field>
            <Field
              description={t("forms.disabledDescription")}
              label={t("forms.disabledLabel")}
            >
              <Input
                controlSize="auth"
                disabled
                readOnly
                value={t("forms.disabledValue")}
              />
            </Field>
          </div>

          <div className="mt-4 grid max-w-2xl gap-4 md:grid-cols-2">
            <Field label={t("forms.multiValueLabel")}>
              <MultiValueInput
                onChange={setPreviewValues}
                placeholder={t("forms.multiValuePlaceholder")}
                values={previewValues}
              />
            </Field>
            <Field label={t("forms.textareaLabel")}>
              <Textarea placeholder={t("forms.textareaPlaceholder")} />
            </Field>
          </div>

          <div className="mt-4 max-w-2xl">
            <Tabs
              ariaLabel={t("forms.tabsLabel")}
              idPrefix="preview-tab"
              items={[
                { label: t("forms.tabInfo"), value: "info" },
                { label: t("forms.tabNotes"), value: "notes" },
              ]}
              onValueChange={setPreviewTab}
              panelId="preview-tab-panel"
              value={previewTab}
            />
            <div className="pt-3 text-[13px] text-muted-foreground" id="preview-tab-panel" role="tabpanel">
              {t("forms.tabPanel")}
            </div>
          </div>
        </PreviewSection>

        <PreviewSection
          description={t("sections.icons.description")}
          id="icons"
          title={t("sections.icons.title")}
        >
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {ICON_SAMPLES.map((sample) => (
              <IconLabel
                key={sample.labelKey}
                className={sample.className}
                icon={sample.icon}
                label={t(sample.labelKey)}
              />
            ))}
          </div>
        </PreviewSection>

        <PreviewSection
          description={t("sections.feedback.description")}
          id="feedback"
          title={t("sections.feedback.title")}
        >
          <div className="flex items-center gap-3">
            <Spinner />
            <span className="text-sm text-muted-foreground">
              {t("feedback.loading")}
            </span>
          </div>
        </PreviewSection>
      </div>
    </main>
  );
}
