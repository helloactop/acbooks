import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { LoaderCircle } from "lucide-react";
import { forwardRef, type ButtonHTMLAttributes, type Ref } from "react";

import { cn } from "../lib/utils";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-control border border-transparent text-sm font-medium transition-colors duration-150 ease-out focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none disabled:pointer-events-none disabled:opacity-50 max-md:min-h-11",
  {
    variants: {
      variant: {
        default:
          "bg-surface text-foreground hover:bg-surface-hover active:bg-surface-active",
        soft: "bg-accent-soft text-accent-text hover:bg-accent-hover active:bg-accent-active",
        outline:
          "border-control-border bg-transparent text-foreground hover:bg-surface-hover active:bg-surface-active",
        ghost:
          "bg-transparent text-foreground hover:bg-surface-hover active:bg-surface-active",
        danger:
          "bg-transparent text-destructive-text hover:bg-destructive/10 active:bg-destructive/15",
        "danger-confirm":
          "border-destructive bg-destructive text-destructive-foreground hover:bg-destructive/90 active:bg-destructive/80",
      },
      size: {
        sm: "h-8 px-3",
        md: "h-9 px-4",
        lg: "h-11 px-6",
        icon: "size-9 p-0 max-md:size-11",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "md",
    },
  },
);

export type ButtonVariant = NonNullable<
  VariantProps<typeof buttonVariants>["variant"]
>;

export interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
  loading?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    asChild = false,
    children,
    className,
    disabled,
    loading = false,
    size,
    type,
    variant,
    ...props
  },
  ref,
) {
  const classes = cn(buttonVariants({ size, variant }), className);

  if (asChild) {
    return (
      <Slot
        ref={ref as Ref<HTMLElement>}
        aria-busy={loading || undefined}
        className={classes}
        {...props}
      >
        {children}
      </Slot>
    );
  }

  return (
    <button
      ref={ref}
      type={type ?? "button"}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={classes}
      {...props}
    >
      <span className="relative inline-flex items-center justify-center">
        {loading ? (
          <LoaderCircle
            aria-hidden="true"
            className="absolute size-[18px] animate-spin motion-reduce:animate-none"
          />
        ) : null}
        <span
          className={cn(
            "inline-flex items-center gap-2",
            loading && "opacity-0",
          )}
        >
          {children}
        </span>
      </span>
    </button>
  );
});

export { buttonVariants };
