import {
  useEffect,
  useRef,
  type MouseEvent,
  type ReactNode,
  type SyntheticEvent,
} from "react";

export interface ModalProps {
  children: ReactNode;
  describedBy?: string;
  labelledBy: string;
  onClose: () => void;
  size?: "confirm" | "form";
}

export function Modal({
  children,
  describedBy,
  labelledBy,
  onClose,
  size = "form",
}: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog !== null && !dialog.open) {
      dialog.showModal();
    }
  }, []);

  const handleCancel = (event: SyntheticEvent<HTMLDialogElement>) => {
    event.preventDefault();
    onClose();
  };

  const handleClick = (event: MouseEvent<HTMLDialogElement>) => {
    if (event.target === event.currentTarget) {
      onClose();
    }
  };

  return (
    <dialog
      aria-describedby={describedBy}
      aria-labelledby={labelledBy}
      className={
        size === "confirm"
          ? "m-auto max-h-[calc(100dvh-2rem)] w-[min(420px,calc(100vw-2rem))] rounded-dialog border border-border bg-background p-0 text-foreground shadow-overlay backdrop:bg-black/40"
          : "m-auto max-h-[calc(100dvh-2rem)] w-[min(640px,calc(100vw-2rem))] rounded-dialog border border-border bg-background p-0 text-foreground shadow-overlay backdrop:bg-black/40"
      }
      onCancel={handleCancel}
      onClick={handleClick}
      ref={dialogRef}
    >
      <div className="flex max-h-[calc(100dvh-2rem)] flex-col">
        {children}
      </div>
    </dialog>
  );
}
