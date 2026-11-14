import { cn } from "@acbooks/ui";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { APP_NAV_GROUPS, isNavItemActive } from "./navigation";
import { homeHref } from "./routes";

export interface AppRailProps {
  pathname: string;
}

export function AppRail({ pathname }: AppRailProps) {
  const { t } = useTranslation("app");

  return (
    <aside
      aria-label={t("nav.sidebar")}
      className="hidden w-20 shrink-0 flex-col items-center bg-frame py-2 md:flex"
    >
      <Link
        aria-label="acbooks"
        className="mb-2 flex size-9 items-center justify-center rounded-control focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-frame focus-visible:outline-none"
        to={homeHref()}
      >
        <img
          alt=""
          className="size-8"
          height={32}
          src="/icons/acbooks-logo.png"
          width={32}
        />
      </Link>

      <nav
        aria-label={t("nav.main")}
        className="flex w-full flex-col items-center gap-0.5 px-1"
      >
        {APP_NAV_GROUPS.map(({ items, labelKey }) => (
          <div
            aria-label={t(labelKey)}
            className="flex w-full shrink-0 flex-col gap-0.5 [&+&]:mt-1 [&+&]:border-t [&+&]:border-border [&+&]:pt-1.5"
            key={labelKey}
            role="group"
          >
            {items.map((item) => {
              const Icon = item.icon;
              const label = t(item.labelKey);
              const active = isNavItemActive(item, pathname);
              return (
                <Link
                  aria-current={active ? "page" : undefined}
                  aria-label={label}
                  className={cn(
                    "flex w-full flex-col items-center gap-1 rounded-control px-1 py-1.5 text-[11px] leading-tight transition-colors duration-150 ease-out focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-frame focus-visible:outline-none",
                    active
                      ? "bg-accent-soft font-medium text-accent-text"
                      : "text-muted-foreground hover:bg-surface-hover hover:text-foreground",
                  )}
                  key={item.href}
                  title={label}
                  to={item.href}
                >
                  <Icon
                    aria-hidden="true"
                    className="size-5 shrink-0"
                    strokeWidth={2}
                  />
                  <span className="w-full text-center">{label}</span>
                </Link>
              );
            })}
          </div>
        ))}
      </nav>
    </aside>
  );
}
