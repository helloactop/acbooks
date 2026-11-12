import { Eye, EyeOff } from "lucide-react";
import { forwardRef, useId, useState } from "react";
import { useTranslation } from "react-i18next";

import { cn } from "../lib/utils";
import { IconButton } from "./icon-button";
import { Input, type InputProps } from "./input";

export type PasswordInputProps = Omit<InputProps, "type">;

export const PasswordInput = forwardRef<HTMLInputElement, PasswordInputProps>(
  function PasswordInput({ className, id, ...props }, ref) {
    const generatedId = useId();
    const inputId = id ?? generatedId;
    const { t } = useTranslation();
    const [visible, setVisible] = useState(false);
    const label = visible ? t("password.hide") : t("password.show");

    return (
      <div className="relative">
        <Input
          ref={ref}
          id={inputId}
          type={visible ? "text" : "password"}
          className={cn("pr-11", className)}
          {...props}
        />
        <IconButton
          aria-controls={inputId}
          aria-pressed={visible}
          className="absolute top-1/2 right-1 -translate-y-1/2"
          disabled={props.disabled}
          label={label}
          onClick={() => {
            setVisible((current) => !current);
          }}
          onMouseDown={(event) => {
            event.preventDefault();
          }}
        >
          {visible ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
        </IconButton>
      </div>
    );
  },
);
