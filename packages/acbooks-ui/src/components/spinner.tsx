import { LoaderCircle } from "lucide-react";

import { cn } from "../lib/utils";

export interface SpinnerProps {
  className?: string;
  label?: string;
}

export function Spinner({ className, label = "加载中" }: SpinnerProps) {
  return (
    <span
      className={cn("inline-flex items-center justify-center", className)}
      role="status"
    >
      <LoaderCircle
        aria-hidden="true"
        className="size-[18px] animate-spin motion-reduce:animate-none"
      />
      <span className="sr-only">{label}</span>
    </span>
  );
}
