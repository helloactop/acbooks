import { cn } from "@acbooks/ui";
import { CircleAlert } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

export interface LibraryAlertProps {
  children: ReactNode;
  className?: string;
  requestId?: string;
}

export function LibraryAlert({
  children,
  className,
  requestId,
}: LibraryAlertProps) {
  const { t } = useTranslation("library");

  return (
    <div
      className={cn(
        "flex items-start gap-2 rounded-control bg-destructive-text/10 p-3 text-sm text-destructive-text",
        className,
      )}
      role="alert"
    >
      <CircleAlert
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
