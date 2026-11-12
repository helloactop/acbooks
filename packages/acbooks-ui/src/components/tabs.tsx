import { useRef, type KeyboardEvent, type ReactNode } from "react";

import { cn } from "../lib/utils";

export interface TabItem<T extends string> {
  label: ReactNode;
  value: T;
}

export interface TabsProps<T extends string> {
  ariaLabel: string;
  className?: string;
  idPrefix: string;
  items: readonly TabItem<T>[];
  onValueChange: (value: T) => void;
  panelId: string;
  value: T;
}

export function Tabs<T extends string>({
  ariaLabel,
  className,
  idPrefix,
  items,
  onValueChange,
  panelId,
  value,
}: TabsProps<T>) {
  const buttons = useRef<Array<HTMLButtonElement | null>>([]);

  const selectTab = (index: number) => {
    const item = items[index];
    if (item === undefined) {
      return;
    }
    onValueChange(item.value);
    buttons.current[index]?.focus();
  };

  const handleKeyDown = (
    event: KeyboardEvent<HTMLButtonElement>,
    index: number,
  ) => {
    let nextIndex: number;
    switch (event.key) {
      case "ArrowRight":
      case "ArrowDown":
        nextIndex = (index + 1) % items.length;
        break;
      case "ArrowLeft":
      case "ArrowUp":
        nextIndex = (index - 1 + items.length) % items.length;
        break;
      case "Home":
        nextIndex = 0;
        break;
      case "End":
        nextIndex = items.length - 1;
        break;
      default:
        return;
    }
    event.preventDefault();
    selectTab(nextIndex);
  };

  return (
    <div
      aria-label={ariaLabel}
      className={cn(
        "flex gap-6 overflow-x-auto border-b border-border [scrollbar-width:none] [&::-webkit-scrollbar]:hidden",
        className,
      )}
      role="tablist"
    >
      {items.map((item, index) => {
        const selected = item.value === value;
        return (
          <button
            aria-controls={panelId}
            aria-selected={selected}
            className={cn(
              "-mb-px inline-flex h-11 shrink-0 items-center border-b-2 px-1 text-sm font-medium transition-colors duration-150 ease-out focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none",
              selected
                ? "border-accent-text text-accent-text"
                : "border-transparent text-muted-foreground hover:text-foreground",
            )}
            id={`${idPrefix}-${item.value}`}
            key={item.value}
            onClick={() => {
              onValueChange(item.value);
            }}
            onKeyDown={(event) => {
              handleKeyDown(event, index);
            }}
            ref={(node) => {
              buttons.current[index] = node;
            }}
            role="tab"
            tabIndex={selected ? 0 : -1}
            type="button"
          >
            {item.label}
          </button>
        );
      })}
    </div>
  );
}
