import { useRef, type KeyboardEvent, type ReactNode } from "react";

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
  variant?: "boxed" | "pill";
}

export function SegmentedControl<T extends string>({
  ariaLabel,
  className,
  onValueChange,
  options,
  value,
  variant = "pill",
}: SegmentedControlProps<T>) {
  const buttons = useRef<Array<HTMLButtonElement | null>>([]);

  const selectOption = (index: number) => {
    const option = options[index];
    if (option === undefined) {
      return;
    }
    onValueChange(option.value);
    buttons.current[index]?.focus();
  };

  const handleKeyDown = (
    event: KeyboardEvent<HTMLButtonElement>,
    index: number,
  ) => {
    const lastIndex = options.length - 1;
    let nextIndex: number;

    switch (event.key) {
      case "ArrowRight":
      case "ArrowDown":
        nextIndex = (index + 1) % options.length;
        break;
      case "ArrowLeft":
      case "ArrowUp":
        nextIndex = (index - 1 + options.length) % options.length;
        break;
      case "Home":
        nextIndex = 0;
        break;
      case "End":
        nextIndex = lastIndex;
        break;
      default:
        return;
    }

    event.preventDefault();
    selectOption(nextIndex);
  };

  return (
    <div
      aria-label={ariaLabel}
      aria-orientation="horizontal"
      className={cn(
        "inline-flex items-center",
        variant === "boxed"
          ? "h-10 gap-1 rounded-menu bg-surface p-1 max-lg:h-11 max-lg:p-0"
          : "h-9 gap-1 rounded-full bg-surface p-1 max-md:h-11 max-md:p-0",
        className,
      )}
      role="radiogroup"
    >
      {options.map((option, index) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            ref={(node) => {
              buttons.current[index] = node;
            }}
            aria-checked={selected}
            className={cn(
              "inline-flex items-center justify-center border text-sm transition-colors duration-150 ease-out focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none",
              variant === "boxed"
                ? "h-8 rounded-lg px-2.5 font-medium [&_svg]:size-4 max-lg:h-11 max-lg:rounded-control max-lg:px-3"
                : "h-7 rounded-full px-3 font-medium max-md:h-11",
              selected
                ? variant === "boxed"
                  ? "border-transparent bg-accent-soft font-semibold text-accent-text"
                  : "border-segment-selected-border bg-background text-foreground"
                : "border-transparent text-muted-foreground hover:bg-surface-hover hover:text-foreground",
            )}
            onClick={() => {
              onValueChange(option.value);
            }}
            onKeyDown={(event) => {
              handleKeyDown(event, index);
            }}
            role="radio"
            tabIndex={selected ? 0 : -1}
            type="button"
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
