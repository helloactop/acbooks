import { buttonVariants } from "@acbooks/ui";
import { CircleAlert } from "lucide-react";
import { useTranslation } from "react-i18next";

export function NotFoundPage() {
  const { t } = useTranslation("app");

  return (
    <div className="flex h-dvh items-center justify-center bg-frame p-4">
      <div className="w-full max-w-[400px] rounded-panel border border-border bg-background p-6 text-center">
        <CircleAlert
          aria-hidden="true"
          className="mx-auto size-8 text-muted-foreground"
          strokeWidth={2}
        />
        <h1 className="mt-3 text-xl font-semibold">{t("notFound.title")}</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {t("notFound.description")}
        </p>
        <a
          className={buttonVariants({
            className: "mt-5 w-full",
            size: "lg",
            variant: "soft",
          })}
          href="/"
        >
          {t("notFound.back")}
        </a>
      </div>
    </div>
  );
}
