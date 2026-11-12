import { forwardRef, type ReactNode } from "react";

import { Button, type ButtonProps } from "./button";

export interface IconButtonProps
  extends Omit<ButtonProps, "children" | "size" | "variant"> {
  children: ReactNode;
  label: string;
  variant?: ButtonProps["variant"];
}

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(
  function IconButton(
    { children, label, variant = "ghost", ...props },
    ref,
  ) {
    return (
      <Button
        ref={ref}
        aria-label={label}
        size="icon"
        variant={variant}
        {...props}
      >
        {children}
      </Button>
    );
  },
);
