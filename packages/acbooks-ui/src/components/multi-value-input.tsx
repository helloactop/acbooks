import { X } from "lucide-react";
import {
  useRef,
  useState,
  type KeyboardEvent,
  type MouseEvent,
} from "react";
import { useTranslation } from "react-i18next";

import { cn } from "../lib/utils";

export interface MultiValueInputProps {
  "aria-describedby"?: string;
  "aria-errormessage"?: string;
  "aria-invalid"?: boolean;
  "aria-required"?: boolean;
  className?: string;
  disabled?: boolean;
  id?: string;
  onChange: (values: string[]) => void;
  placeholder?: string;
  values: readonly string[];
}

export function MultiValueInput({
  "aria-describedby": ariaDescribedBy,
  "aria-errormessage": ariaErrorMessage,
  "aria-invalid": ariaInvalid,
  "aria-required": ariaRequired,
  className,
  disabled = false,
  id,
  onChange,
  placeholder,
  values,
}: MultiValueInputProps) {
  const { t } = useTranslation();
  const [draft, setDraft] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  const commitDraft = () => {
    const trimmed = draft.trim();
    if (trimmed.length === 0) {
      setDraft("");
      return;
    }
    if (!values.includes(trimmed)) {
      onChange([...values, trimmed]);
    }
    setDraft("");
  };

  const removeValue = (value: string) => {
    onChange(values.filter((current) => current !== value));
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.nativeEvent.isComposing) {
      return;
    }
    if (
      event.key === "Enter" ||
      event.key === "," ||
      event.key === "，"
    ) {
      event.preventDefault();
      commitDraft();
      return;
    }
    if (
      event.key === "Backspace" &&
      draft.length === 0 &&
      values.length > 0
    ) {
      event.preventDefault();
      onChange(values.slice(0, -1));
    }
  };

  const handleContainerClick = (event: MouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget) {
      inputRef.current?.focus();
    }
  };

  return (
    <div
      className={cn(
        "flex min-h-8 w-full flex-wrap items-center gap-1 rounded-control border border-control-border bg-background px-1.5 py-0.5 transition-colors duration-150 ease-out focus-within:border-accent-text focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2 focus-within:ring-offset-background max-md:min-h-11",
        disabled && "border-disabled-border bg-disabled-surface",
        ariaInvalid && "border-destructive-text",
        className,
      )}
      onClick={handleContainerClick}
    >
      {values.map((value) => (
        <span
          className="inline-flex max-w-full items-center gap-1 rounded bg-surface py-0.5 pr-1 pl-1.5 text-xs leading-4 text-foreground"
          key={value}
        >
          <span className="truncate">{value}</span>
          <button
            aria-label={t("multiValue.remove", { value })}
            className="inline-flex size-4 shrink-0 items-center justify-center rounded-full text-muted-foreground transition-colors duration-150 ease-out hover:bg-surface-hover hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none disabled:pointer-events-none"
            disabled={disabled}
            onClick={(event) => {
              event.stopPropagation();
              removeValue(value);
            }}
            type="button"
          >
            <X aria-hidden="true" className="size-3" />
          </button>
        </span>
      ))}
      <input
        aria-describedby={ariaDescribedBy}
        aria-errormessage={ariaErrorMessage}
        aria-invalid={ariaInvalid}
        aria-required={ariaRequired}
        className="h-6 min-w-[8rem] flex-1 border-0 bg-transparent p-0 text-[13px] text-foreground outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed max-md:text-base"
        disabled={disabled}
        id={id}
        onBlur={commitDraft}
        onChange={(event) => {
          setDraft(event.target.value);
        }}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        ref={inputRef}
        value={draft}
      />
    </div>
  );
}
