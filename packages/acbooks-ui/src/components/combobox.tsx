import { useId, useRef, useState, type KeyboardEvent, type ReactNode } from "react";

import { cn } from "../lib/utils";
import { Input } from "./input";
import { Spinner } from "./spinner";

export interface ComboboxOption {
  count?: number;
  label: string;
  value: string;
}

export interface ComboboxProps {
  ariaLabel: string;
  className?: string;
  disabled?: boolean;
  emptyLabel?: ReactNode;
  loading?: boolean;
  onChange: (value: string) => void;
  options: readonly ComboboxOption[];
  placeholder?: string;
  status?: ReactNode;
  value: string;
}

export function Combobox({
  ariaLabel,
  className,
  disabled = false,
  emptyLabel,
  loading = false,
  onChange,
  options,
  placeholder,
  status,
  value,
}: ComboboxProps) {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const rootRef = useRef<HTMLDivElement>(null);
  const listId = useId();
  const normalized = value.trim().toLocaleLowerCase();
  const visibleOptions =
    normalized.length === 0
      ? options
      : options.filter((option) => {
          const label = option.label.toLocaleLowerCase();
          const optionValue = option.value.toLocaleLowerCase();
          return (
            label.includes(normalized) || optionValue.includes(normalized)
          );
        });
  const activeOption = visibleOptions[activeIndex];

  const close = () => {
    setOpen(false);
    setActiveIndex(-1);
  };

  const selectOption = (nextValue: string) => {
    onChange(nextValue);
    close();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown") {
      if (visibleOptions.length === 0) {
        return;
      }
      event.preventDefault();
      setOpen(true);
      setActiveIndex((current) => (current + 1) % visibleOptions.length);
      return;
    }
    if (event.key === "ArrowUp") {
      if (visibleOptions.length === 0) {
        return;
      }
      event.preventDefault();
      setOpen(true);
      setActiveIndex((current) =>
        current <= 0 ? visibleOptions.length - 1 : current - 1,
      );
      return;
    }
    if (event.key === "Enter") {
      if (open && activeOption !== undefined) {
        event.preventDefault();
        selectOption(activeOption.value);
      }
      return;
    }
    if (event.key === "Escape") {
      if (open) {
        event.preventDefault();
        close();
      }
      return;
    }
    if (event.key === "Tab") {
      close();
    }
  };

  return (
    <div
      className={cn("relative min-w-0", className)}
      onBlur={(event) => {
        if (
          rootRef.current !== null &&
          !rootRef.current.contains(event.relatedTarget as Node | null)
        ) {
          close();
        }
      }}
      ref={rootRef}
    >
      <Input
        aria-activedescendant={
          open && activeOption !== undefined
            ? `${listId}-option-${activeIndex}`
            : undefined
        }
        aria-autocomplete="list"
        aria-controls={open ? listId : undefined}
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-label={ariaLabel}
        autoComplete="off"
        disabled={disabled}
        onChange={(event) => {
          setOpen(true);
          setActiveIndex(-1);
          onChange(event.target.value);
        }}
        onFocus={() => {
          setOpen(true);
        }}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        role="combobox"
        spellCheck={false}
        type="text"
        value={value}
      />

      {open ? (
        <div
          className="absolute top-full right-0 left-0 z-30 mt-1 max-h-64 overflow-y-auto rounded-menu border border-border bg-background p-1 shadow-overlay"
          id={listId}
          role="listbox"
        >
          {loading ? (
            <div className="flex min-h-9 items-center gap-2 px-3 text-sm text-muted-foreground">
              <Spinner />
              {status}
            </div>
          ) : visibleOptions.length === 0 ? (
            <div
              className="px-3 py-2 text-sm text-muted-foreground"
              role="status"
            >
              {status ?? emptyLabel}
            </div>
          ) : (
            visibleOptions.map((option, index) => (
              <div
                aria-selected={index === activeIndex}
                className={cn(
                  "flex min-h-9 cursor-pointer items-center gap-2 rounded px-3 py-2 text-sm text-foreground",
                  index === activeIndex
                    ? "bg-accent-soft text-accent-text"
                    : "hover:bg-surface-hover",
                )}
                id={`${listId}-option-${index}`}
                key={option.value}
                onClick={() => {
                  selectOption(option.value);
                }}
                onPointerDown={(event) => {
                  event.preventDefault();
                }}
                onPointerMove={() => {
                  setActiveIndex(index);
                }}
                role="option"
              >
                <span className="min-w-0 flex-1 truncate">{option.label}</span>
                {option.count !== undefined ? (
                  <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
                    {option.count}
                  </span>
                ) : null}
              </div>
            ))
          )}
          {!loading && visibleOptions.length > 0 && status ? (
            <div
              className="border-t border-border px-3 py-2 text-xs text-muted-foreground"
              role="status"
            >
              {status}
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
