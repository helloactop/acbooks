import { Eye, EyeOff } from "lucide-react";
import { forwardRef, useState } from "react";

import { cn } from "../lib/utils";
import { IconButton } from "./icon-button";
import { Input, type InputProps } from "./input";

export type PasswordInputProps = Omit<InputProps, "type">;

export const PasswordInput = forwardRef<HTMLInputElement, PasswordInputProps>(
  function PasswordInput({ className, ...props }, ref) {
    const [visible, setVisible] = useState(false);
    const label = visible ? "隐藏密码" : "显示密码";

    return (
      <div className="relative">
        <Input
          ref={ref}
          type={visible ? "text" : "password"}
          className={cn("pr-11", className)}
          {...props}
        />
        <IconButton
          aria-pressed={visible}
          className="absolute top-1/2 right-1 -translate-y-1/2"
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
