import type { ReactNode } from "react";

export interface SettingsGroupProps {
  children: ReactNode;
  description?: string;
  title: string;
}

export function SettingsGroup({
  children,
  description,
  title,
}: SettingsGroupProps) {
  return (
    <section>
      <h2 className="text-lg font-semibold">{title}</h2>
      {description !== undefined ? (
        <p className="mt-1 text-sm text-muted-foreground">{description}</p>
      ) : null}
      <div className="mt-3 divide-y divide-border rounded-card border border-border bg-surface">
        {children}
      </div>
    </section>
  );
}

export interface SettingsRowProps {
  children: ReactNode;
  description?: string;
  htmlFor?: string;
  label: string;
}

export function SettingsRow({
  children,
  description,
  htmlFor,
  label,
}: SettingsRowProps) {
  return (
    <div className="flex min-h-14 flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:gap-6">
      <div className="min-w-0">
        {htmlFor === undefined ? (
          <span className="text-sm font-medium">{label}</span>
        ) : (
          <label className="text-sm font-medium" htmlFor={htmlFor}>
            {label}
          </label>
        )}
        {description !== undefined ? (
          <p className="mt-0.5 text-xs text-muted-foreground">{description}</p>
        ) : null}
      </div>
      <div className="min-w-0 shrink-0">{children}</div>
    </div>
  );
}
