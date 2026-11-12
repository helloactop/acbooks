import {
  SegmentedControl,
  useLanguage,
  type LanguageMode,
  type SegmentedControlOption,
} from "@acbooks/ui";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

export interface AuthPanelProps {
  children: ReactNode;
  description?: string;
  title: string;
}

export function AuthPanel({ children, description, title }: AuthPanelProps) {
  const { language, setLanguage } = useLanguage();
  const { t } = useTranslation("auth");
  const { t: tCommon } = useTranslation("common");

  const languageOptions: readonly SegmentedControlOption<LanguageMode>[] = [
    { value: "system", label: tCommon("language.system") },
    { value: "zh-CN", label: tCommon("language.zhCN") },
    { value: "en", label: tCommon("language.en") },
  ];

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-5 bg-frame px-4 py-8 text-foreground">
      <main className="w-full max-w-[400px] rounded-panel border border-border bg-background p-6 min-[481px]:p-8">
        <header className="flex flex-col items-center gap-3 text-center">
          <img
            alt="acbooks"
            className="block size-16"
            height={64}
            src="/icons/acbooks-192.png"
            width={64}
          />
          <div className="flex flex-col gap-1">
            <h1 className="text-xl leading-[1.3] font-semibold">{title}</h1>
            {description ? (
              <p className="text-sm text-muted-foreground">{description}</p>
            ) : null}
          </div>
        </header>
        <div className="mt-6">{children}</div>
      </main>

      <SegmentedControl
        ariaLabel={tCommon("language.ariaLabel")}
        onValueChange={setLanguage}
        options={languageOptions}
        value={language}
      />

      <p className="text-center text-xs text-muted-foreground">
        {t("footer")}
      </p>
    </div>
  );
}
