import type { ReactNode } from "react";

import { cn } from "../lib/utils";

export interface SegmentedControlOption<T extends string> {
  label: ReactNode;
  value: T;
}

export interface SegmentedControlProps<T extends string> {
  ariaLabel: string;
  className?: string;
  onValueChange: (value: T) => void;
  options: readonly SegmentedControlOption<T>[];
  value: T;
}

export function SegmentedControl<T extends string>({
  ariaLabel,
  className,
  onValueChange,
  options,
  value,
}: SegmentedControlProps<T>) {
  return (
    <div
      aria-label={ariaLabel}
      className={cn(
        "inline-flex h-9 items-center gap-1 rounded-full bg-surface p-1 max-md:h-11 max-md:p-0",
        className,
      )}
      role="group"
    >
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            aria-pressed={selected}
            className={cn(
              "inline-flex h-7 items-center justify-center rounded-full px-3 text-sm font-medium transition-colors duration-150 ease-out focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none max-md:h-11",
              selected
                ? "bg-background text-foreground"
                : "text-muted-foreground hover:bg-surface-hover hover:text-foreground",
            )}
            onClick={() => {
              onValueChange(option.value);
            }}
            type="button"
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
