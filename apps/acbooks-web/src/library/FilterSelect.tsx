import { cn } from "@acbooks/ui";
import { ChevronDown } from "lucide-react";
import { useTranslation } from "react-i18next";

export interface FilterOption {
  label: string;
  value: string;
}

export interface FilterSelectProps {
  disabled?: boolean;
  label: string;
  onChange: (value: string) => void;
  options: readonly FilterOption[];
  value: string;
}

export function FilterSelect({
  disabled = false,
  label,
  onChange,
  options,
  value,
}: FilterSelectProps) {
  const { t } = useTranslation("library");
  const currentLabel =
    options.find((option) => option.value === value)?.label ?? value;

  return (
    <label
      className={cn(
        "relative inline-flex h-11 shrink-0 items-center gap-2 rounded-control border px-3 text-sm whitespace-nowrap transition-colors focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2 focus-within:ring-offset-background md:h-10",
        disabled
          ? "border-disabled-border bg-disabled-surface text-disabled-foreground"
          : "border-control-border bg-background text-foreground",
      )}
      title={disabled ? t("nav.notAvailable") : undefined}
    >
      <span className={disabled ? undefined : "text-muted-foreground"}>
        {label}
      </span>
      <span className="font-medium">{currentLabel}</span>
      <ChevronDown
        aria-hidden="true"
        className={cn(
          "size-4",
          disabled ? "text-disabled-foreground" : "text-muted-foreground",
        )}
        strokeWidth={2}
      />
      <select
        aria-label={label}
        className="absolute inset-0 h-full w-full cursor-pointer opacity-0 disabled:cursor-not-allowed"
        disabled={disabled}
        onChange={(event) => {
          onChange(event.target.value);
        }}
        value={value}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}
