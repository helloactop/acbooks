import { IconButton } from "@acbooks/ui";
import { Menu } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

import { useAppShell } from "./app-shell-context";

export interface MobileNavigationTriggerProps {
  className?: string;
}

export function MobileNavigationTrigger({
  className,
}: MobileNavigationTriggerProps) {
  const { t } = useTranslation("app");
  const { navigationOpen, openNavigation } = useAppShell();

  return (
    <IconButton
      aria-controls="app-navigation-drawer"
      aria-expanded={navigationOpen}
      className={className}
      label={t("nav.openMenu")}
      onClick={openNavigation}
    >
      <Menu aria-hidden="true" />
    </IconButton>
  );
}

export interface PageHeaderProps {
  actions?: ReactNode;
  actionsClassName?: string;
  description?: ReactNode;
  title: string;
  titleId?: string;
}

export function PageHeader({
  actions,
  actionsClassName,
  description,
  title,
  titleId,
}: PageHeaderProps) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div className="flex min-w-0 items-start gap-2">
        <MobileNavigationTrigger className="-ml-1 shrink-0 lg:hidden" />
        <div className="min-w-0">
          <h1
            className="text-[28px] leading-[1.25] font-semibold break-words"
            id={titleId}
          >
            {title}
          </h1>
          {description !== undefined ? (
            <div className="mt-1 text-sm text-muted-foreground">
              {description}
            </div>
          ) : null}
        </div>
      </div>
      {actions !== undefined ? (
        <div
          className={`ml-auto flex min-w-0 flex-wrap items-center gap-2 ${actionsClassName ?? ""}`}
        >
          {actions}
        </div>
      ) : null}
    </div>
  );
}
