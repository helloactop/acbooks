import { cn } from "@acbooks/ui";
import type { ReactNode } from "react";

interface PageContainerProps {
  children: ReactNode;
  className?: string;
  width?: "wide" | "form";
}

export function PageContainer({
  children,
  className,
  width = "wide",
}: PageContainerProps) {
  return (
    <div
      className={cn(
        "flex min-w-0 w-full flex-col gap-4",
        width === "form" ? "max-w-[960px]" : "max-w-[1440px]",
        className,
      )}
    >
      {children}
    </div>
  );
}
