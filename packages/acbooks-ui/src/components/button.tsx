import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { LoaderCircle } from "lucide-react";
import { forwardRef, type ButtonHTMLAttributes, type Ref } from "react";

import { cn } from "../lib/utils";

/**
 * `soft` is the single visual primary action in a region.
 * `default` is a neutral filled action for save/confirm/apply.
 * `outline` is a bordered secondary action such as filtering or navigation.
 */
const buttonVariants = cva(
  "inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-control border text-[13px] transition-colors duration-150 ease-out focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none disabled:pointer-events-none max-md:min-h-11 data-[disabled=true]:border-disabled-border data-[disabled=true]:bg-disabled-surface data-[disabled=true]:text-disabled-foreground [&_svg:not([class*='size-'])]:size-4 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        default:
          "border-transparent bg-surface font-medium text-foreground hover:bg-surface-hover active:bg-surface-active",
        soft: "border-accent-border bg-accent-soft font-semibold text-accent-text hover:bg-accent-hover active:bg-accent-active",
        outline:
          "border-control-border bg-transparent font-medium text-foreground hover:bg-surface-hover active:bg-surface-active",
        ghost:
          "border-transparent bg-transparent font-medium text-foreground hover:bg-surface-hover active:bg-surface-active",
        danger:
          "border-transparent bg-transparent font-medium text-destructive-text hover:bg-destructive/10 active:bg-destructive/15",
        "danger-confirm":
          "border-destructive bg-destructive font-medium text-destructive-foreground hover:bg-destructive/90 active:bg-destructive/80",
      },
      size: {
        sm: "h-7 px-2.5",
        md: "h-8 px-3",
        lg: "h-10 px-4",
        icon: "size-8 p-0 max-md:size-11",
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
        aria-disabled={disabled || loading || undefined}
        className={classes}
        data-disabled={disabled ? "true" : undefined}
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
      data-disabled={disabled ? "true" : undefined}
      {...props}
    >
      <span className="relative inline-flex items-center justify-center">
        {loading ? (
          <LoaderCircle
            aria-hidden="true"
            className="absolute size-4 animate-spin motion-reduce:animate-none"
          />
        ) : null}
        <span
          className={cn(
            "inline-flex items-center gap-1.5",
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
