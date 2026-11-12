import { forwardRef, type InputHTMLAttributes } from "react";

import { cn } from "../lib/utils";

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  controlSize?: "default" | "auth";
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  {
    className,
    controlSize = "default",
    type = "text",
    ...props
  },
  ref,
) {
  return (
    <input
      ref={ref}
      type={type}
      className={cn(
        "h-10 w-full rounded-control border border-control-border bg-background px-3 text-sm text-foreground transition-colors duration-150 ease-out placeholder:text-muted-foreground focus-visible:border-accent-text focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none disabled:cursor-not-allowed disabled:border-disabled-border disabled:bg-disabled-surface disabled:text-disabled-foreground aria-invalid:border-destructive-text aria-invalid:focus-visible:ring-destructive",
        controlSize === "auth" && "h-11",
        className,
      )}
      {...props}
    />
  );
});
