import { cn } from "@acbooks/ui";
import { CircleAlert, CircleCheck, Info } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

type AuthAlertTone = "error" | "info" | "success";

export interface AuthAlertProps {
  children: ReactNode;
  requestId?: string;
  tone?: AuthAlertTone;
}

const TONE_CLASSES: Record<AuthAlertTone, string> = {
  error: "bg-destructive-text/10 text-destructive-text",
  info: "bg-surface text-foreground",
  success: "bg-accent-soft text-accent-text",
};

const TONE_ICONS = {
  error: CircleAlert,
  info: Info,
  success: CircleCheck,
} as const;

export function AuthAlert({
  children,
  requestId,
  tone = "error",
}: AuthAlertProps) {
  const { t } = useTranslation("auth");
  const Icon = TONE_ICONS[tone];

  return (
    <div
      className={cn(
        "flex items-start gap-2 rounded-control p-3 text-sm",
        TONE_CLASSES[tone],
      )}
      role={tone === "error" ? "alert" : "status"}
    >
      <Icon
        aria-hidden="true"
        className="mt-0.5 size-[18px] shrink-0"
        strokeWidth={2}
      />
      <div className="min-w-0">
        <span className="block">{children}</span>
        {requestId ? (
          <span className="mt-1 block break-all text-xs opacity-80">
            {t("errors.requestId", { requestId })}
          </span>
        ) : null}
      </div>
    </div>
  );
}
