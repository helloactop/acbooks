import { ChevronDown } from "lucide-react";
import { forwardRef, type SelectHTMLAttributes } from "react";

import { cn } from "../lib/utils";

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  containerClassName?: string;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  function Select({ children, className, containerClassName, ...props }, ref) {
    return (
      <span className={cn("relative block", containerClassName)}>
        <select
          className={cn(
            "h-10 w-full appearance-none rounded-control border border-control-border bg-background px-3 pr-9 text-sm text-foreground transition-colors duration-150 ease-out focus-visible:border-accent-text focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none disabled:cursor-not-allowed disabled:border-disabled-border disabled:bg-disabled-surface disabled:text-disabled-foreground aria-invalid:border-destructive-text aria-invalid:focus-visible:ring-destructive max-md:h-11 max-md:text-base",
            className,
          )}
          ref={ref}
          {...props}
        >
          {children}
        </select>
        <ChevronDown
          aria-hidden="true"
          className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted-foreground"
          strokeWidth={2}
        />
      </span>
    );
  },
);
