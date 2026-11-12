import {
  cloneElement,
  useId,
  type ReactElement,
  type ReactNode,
} from "react";

import { cn } from "../lib/utils";

interface FieldControlProps {
  id?: string;
  "aria-describedby"?: string;
  "aria-invalid"?: boolean;
  "aria-required"?: boolean;
}

export interface FieldProps {
  children: ReactElement<FieldControlProps>;
  className?: string;
  description?: ReactNode;
  error?: ReactNode;
  htmlFor?: string;
  label: ReactNode;
  required?: boolean;
}

export function Field({
  children,
  className,
  description,
  error,
  htmlFor,
  label,
  required = false,
}: FieldProps) {
  const generatedId = useId();
  const controlId = htmlFor ?? children.props.id ?? generatedId;
  const descriptionId = description ? `${controlId}-description` : undefined;
  const errorId = error ? `${controlId}-error` : undefined;
  const describedBy =
    [descriptionId, errorId].filter(Boolean).join(" ") || undefined;

  const control = cloneElement(children, {
    id: controlId,
    "aria-describedby": describedBy,
    "aria-invalid": error ? true : undefined,
    "aria-required": required || undefined,
  });

  return (
    <div className={cn("grid gap-2", className)}>
      <label
        className={cn(
          "text-sm font-medium text-foreground",
          required &&
            "after:ml-1 after:text-destructive-text after:content-['*']",
        )}
        htmlFor={controlId}
      >
        {label}
      </label>
      {control}
      {description ? (
        <p className="text-xs text-muted-foreground" id={descriptionId}>
          {description}
        </p>
      ) : null}
      {error ? (
        <p className="text-xs text-destructive-text" id={errorId} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
