import { forwardRef, type TextareaHTMLAttributes } from "react";

import { cn } from "../lib/utils";

export type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement>;

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  function Textarea({ className, ...props }, ref) {
    return (
      <textarea
        className={cn(
          "min-h-24 w-full resize-y rounded-control border border-control-border bg-background p-3 text-sm text-foreground transition-colors duration-150 ease-out placeholder:text-muted-foreground focus-visible:border-accent-text focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none disabled:cursor-not-allowed disabled:border-disabled-border disabled:bg-disabled-surface disabled:text-disabled-foreground aria-invalid:border-destructive-text aria-invalid:focus-visible:ring-destructive",
          className,
        )}
        ref={ref}
        {...props}
      />
    );
  },
);
