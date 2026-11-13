import { Check, ChevronDown } from "lucide-react";
import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent,
  type ReactNode,
} from "react";
import { createPortal } from "react-dom";

import { cn } from "../lib/utils";
import { resolveOverlayContainer } from "../lib/overlay-container";

export interface SelectOption {
  disabled?: boolean;
  label: ReactNode;
  value: string;
}

export interface SelectGroup {
  label: string;
  options: readonly SelectOption[];
}

export interface SelectProps {
  ariaBusy?: boolean;
  ariaLabel?: string;
  className?: string;
  containerClassName?: string;
  disabled?: boolean;
  groups?: readonly SelectGroup[];
  id?: string;
  invalid?: boolean;
  onValueChange: (value: string) => void;
  options?: readonly SelectOption[];
  placeholder?: ReactNode;
  value: string;
}

interface MenuPosition {
  bottom?: number;
  left: number;
  maxHeight: number;
  top?: number;
  width: number;
}

const VIEWPORT_PADDING = 12;
const MENU_GAP = 8;
const MENU_MAX_HEIGHT = 320;

const TRIGGER_CLASS =
  "flex h-8 w-full items-center gap-2 rounded-control border border-control-border bg-background px-2.5 text-[13px] text-foreground transition-colors duration-150 ease-out focus-visible:border-accent-text focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none disabled:cursor-not-allowed disabled:border-disabled-border disabled:bg-disabled-surface disabled:text-disabled-foreground aria-invalid:border-destructive-text max-md:h-11 max-md:text-base";

const OPTION_CLASS =
  "flex min-h-8 w-full items-center gap-2 rounded px-2.5 py-1.5 text-left text-[13px] text-foreground transition-colors duration-150 ease-out focus-visible:outline-none max-md:min-h-11";

export function Select({
  ariaBusy,
  ariaLabel,
  className,
  containerClassName,
  disabled = false,
  groups,
  id,
  invalid = false,
  onValueChange,
  options = [],
  placeholder,
  value,
}: SelectProps) {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [menuPosition, setMenuPosition] = useState<MenuPosition | null>(null);
  const [portalContainer, setPortalContainer] = useState<HTMLElement | null>(
    null,
  );
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const listId = useId();
  const optionGroups: readonly SelectGroup[] =
    groups ?? (options.length > 0 ? [{ label: "", options }] : []);
  let flatIndex = 0;
  const groupsWithIndex = optionGroups.map((group) => ({
    label: group.label,
    options: group.options.map((option) => ({
      index: flatIndex++,
      option,
    })),
  }));
  const flatOptions = groupsWithIndex.flatMap((group) =>
    group.options.map((entry) => entry.option),
  );
  const selectedIndex = flatOptions.findIndex(
    (option) => option.value === value,
  );
  const selectedLabel =
    (selectedIndex >= 0 ? flatOptions[selectedIndex]?.label : undefined) ??
    placeholder ??
    value;
  const activeOption = flatOptions[activeIndex];

  const updatePosition = useCallback(() => {
    const trigger = triggerRef.current;
    if (trigger === null) {
      return;
    }
    const rect = trigger.getBoundingClientRect();
    const width = Math.min(
      Math.max(rect.width, 176),
      window.innerWidth - VIEWPORT_PADDING * 2,
    );
    const availableBelow =
      window.innerHeight - rect.bottom - MENU_GAP - VIEWPORT_PADDING;
    const availableAbove = rect.top - MENU_GAP - VIEWPORT_PADDING;
    const openUp =
      availableBelow < 176 && availableAbove > availableBelow;
    const maxHeight = Math.max(
      88,
      Math.min(MENU_MAX_HEIGHT, openUp ? availableAbove : availableBelow),
    );
    setMenuPosition({
      left: Math.max(
        VIEWPORT_PADDING,
        Math.min(
          rect.left,
          window.innerWidth - width - VIEWPORT_PADDING,
        ),
      ),
      maxHeight,
      width,
      ...(openUp
        ? { bottom: window.innerHeight - rect.top + MENU_GAP }
        : { top: rect.bottom + MENU_GAP }),
    });
  }, []);

  const closeMenu = useCallback(() => {
    setOpen(false);
    setActiveIndex(-1);
    setMenuPosition(null);
    setPortalContainer(null);
  }, []);

  const openMenu = () => {
    if (disabled) {
      return;
    }
    updatePosition();
    setPortalContainer(resolveOverlayContainer(triggerRef.current));
    setOpen(true);
    setActiveIndex(
      selectedIndex >= 0
        ? selectedIndex
        : flatOptions.findIndex((option) => option.disabled !== true),
    );
  };

  useEffect(() => {
    if (!open) {
      return;
    }
    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node;
      if (
        rootRef.current?.contains(target) !== true &&
        menuRef.current?.contains(target) !== true
      ) {
        closeMenu();
      }
    };
    const reposition = () => {
      updatePosition();
    };
    document.addEventListener("pointerdown", handlePointerDown);
    window.addEventListener("resize", reposition);
    window.addEventListener("scroll", reposition, true);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      window.removeEventListener("resize", reposition);
      window.removeEventListener("scroll", reposition, true);
    };
  }, [closeMenu, open, updatePosition]);

  useEffect(() => {
    if (!open || activeIndex < 0) {
      return;
    }
    document
      .getElementById(`${listId}-option-${activeIndex}`)
      ?.scrollIntoView?.({ block: "nearest" });
  }, [activeIndex, listId, open]);

  const moveActive = (step: 1 | -1) => {
    if (flatOptions.length === 0) {
      return;
    }
    let next = activeIndex;
    for (let count = 0; count < flatOptions.length; count += 1) {
      next =
        (next + step + flatOptions.length) % flatOptions.length;
      if (flatOptions[next]?.disabled !== true) {
        setActiveIndex(next);
        return;
      }
    }
  };

  const selectOption = (nextValue: string) => {
    onValueChange(nextValue);
    closeMenu();
    triggerRef.current?.focus();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (disabled) {
      return;
    }
    if (!open) {
      if (
        event.key === "ArrowDown" ||
        event.key === "ArrowUp" ||
        event.key === "Enter" ||
        event.key === " "
      ) {
        event.preventDefault();
        openMenu();
      }
      return;
    }
    switch (event.key) {
      case "ArrowDown":
        event.preventDefault();
        moveActive(1);
        break;
      case "ArrowUp":
        event.preventDefault();
        moveActive(-1);
        break;
      case "Home":
        event.preventDefault();
        setActiveIndex(
          flatOptions.findIndex((option) => option.disabled !== true),
        );
        break;
      case "End": {
        event.preventDefault();
        for (let index = flatOptions.length - 1; index >= 0; index -= 1) {
          if (flatOptions[index]?.disabled !== true) {
            setActiveIndex(index);
            break;
          }
        }
        break;
      }
      case "Enter":
      case " ":
        event.preventDefault();
        if (activeOption !== undefined && activeOption.disabled !== true) {
          selectOption(activeOption.value);
        }
        break;
      case "Escape":
        event.preventDefault();
        closeMenu();
        break;
      case "Tab":
        closeMenu();
        break;
      default:
        break;
    }
  };

  const menuStyle: CSSProperties | undefined =
    menuPosition === null
      ? undefined
      : {
          bottom: menuPosition.bottom,
          left: menuPosition.left,
          maxHeight: menuPosition.maxHeight,
          top: menuPosition.top,
          width: menuPosition.width,
        };

  return (
    <div
      className={cn("relative min-w-0", containerClassName)}
      ref={rootRef}
    >
      <button
        aria-activedescendant={
          open && activeOption !== undefined
            ? `${listId}-option-${activeIndex}`
            : undefined
        }
        aria-busy={ariaBusy || undefined}
        aria-controls={open ? listId : undefined}
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-invalid={invalid || undefined}
        aria-label={ariaLabel}
        className={cn(TRIGGER_CLASS, className)}
        disabled={disabled}
        id={id}
        onClick={() => {
          if (open) {
            closeMenu();
          } else {
            openMenu();
          }
        }}
        onKeyDown={handleKeyDown}
        ref={triggerRef}
        role="combobox"
        type="button"
      >
        <span className="min-w-0 flex-1 truncate text-left">
          {selectedLabel}
        </span>
        <ChevronDown
          aria-hidden="true"
          className="size-3.5 shrink-0 text-muted-foreground"
          strokeWidth={2}
        />
      </button>

      {open && menuPosition !== null && portalContainer !== null
        ? createPortal(
            <div
              className="fixed z-40 overflow-y-auto overscroll-contain rounded-menu border border-border bg-background p-1 shadow-overlay"
              id={listId}
              ref={menuRef}
              role="listbox"
              style={menuStyle}
            >
              {groupsWithIndex.map((group, groupIndex) => (
                <div
                  aria-label={group.label.length > 0 ? group.label : undefined}
                  key={`${group.label}-${groupIndex}`}
                  role="group"
                >
                  {group.label.length > 0 ? (
                    <div className="px-2.5 pt-2 pb-1 text-[11px] font-medium text-muted-foreground">
                      {group.label}
                    </div>
                  ) : null}
                  {group.options.map(({ index, option }) => {
                    const selected = option.value === value;
                    return (
                      <button
                        aria-selected={selected}
                        className={cn(
                          OPTION_CLASS,
                          option.disabled === true
                            ? "cursor-not-allowed text-disabled-foreground"
                            : index === activeIndex
                              ? "bg-surface-hover"
                              : "hover:bg-surface-hover",
                          selected && "font-medium",
                        )}
                        disabled={option.disabled}
                        id={`${listId}-option-${index}`}
                        key={option.value}
                        onClick={() => {
                          if (option.disabled !== true) {
                            selectOption(option.value);
                          }
                        }}
                        onPointerDown={(event) => {
                          event.preventDefault();
                        }}
                        onPointerMove={() => {
                          if (option.disabled !== true) {
                            setActiveIndex(index);
                          }
                        }}
                        role="option"
                        tabIndex={-1}
                        type="button"
                      >
                        <span className="min-w-0 flex-1 truncate">
                          {option.label}
                        </span>
                        <Check
                          aria-hidden="true"
                          className={cn(
                            "size-4 shrink-0 text-accent-text",
                            selected ? "opacity-100" : "opacity-0",
                          )}
                        />
                      </button>
                    );
                  })}
                </div>
              ))}
            </div>,
            portalContainer,
          )
        : null}
    </div>
  );
}
