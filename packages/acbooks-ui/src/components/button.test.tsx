import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Settings2 } from "lucide-react";
import { describe, expect, it, vi } from "vitest";

import { Button } from "./button";
import { IconButton } from "./icon-button";

describe("Button", () => {
  it("defaults to type=button and calls onClick", async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>保存</Button>);

    const button = screen.getByRole("button", { name: "保存" });
    expect(button).toHaveAttribute("type", "button");
    expect(button).toHaveClass("bg-surface");

    await userEvent.click(button);
    expect(onClick).toHaveBeenCalledOnce();
  });

  it("gives soft the primary border and semibold treatment", () => {
    render(<Button variant="soft">主要操作</Button>);

    expect(screen.getByRole("button", { name: "主要操作" })).toHaveClass(
      "border-accent-border",
      "bg-accent-soft",
      "font-semibold",
    );
  });

  it("keeps default filled and outline bordered for their documented roles", () => {
    render(
      <>
        <Button>保存</Button>
        <Button variant="outline">筛选</Button>
      </>,
    );

    expect(screen.getByRole("button", { name: "保存" })).toHaveClass(
      "bg-surface",
      "border-transparent",
    );
    expect(screen.getByRole("button", { name: "筛选" })).toHaveClass(
      "border-control-border",
      "bg-transparent",
    );
  });

  it("uses semantic disabled tokens instead of opacity", () => {
    render(<Button disabled>禁用</Button>);

    const button = screen.getByRole("button", { name: "禁用" });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute("data-disabled", "true");
    expect(button).toHaveClass(
      "data-[disabled=true]:bg-disabled-surface",
      "data-[disabled=true]:text-disabled-foreground",
      "data-[disabled=true]:border-disabled-border",
    );
    expect(button).not.toHaveClass("disabled:opacity-50");
  });

  it("blocks interaction and keeps its accessible name while loading", async () => {
    const onClick = vi.fn();
    render(
      <Button loading onClick={onClick}>
        保存
      </Button>,
    );

    const button = screen.getByRole("button", { name: "保存" });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute("aria-busy", "true");
    expect(button).not.toHaveAttribute("data-disabled");
    expect(button).toHaveClass("bg-surface");

    await userEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });

  it("uses Radix Slot for asChild composition", () => {
    render(
      <Button asChild>
        <a href="/books">书库</a>
      </Button>,
    );

    const link = screen.getByRole("link", { name: "书库" });
    expect(link).toHaveAttribute("href", "/books");
    expect(link).toHaveClass("bg-surface");
  });

  it("constrains Lucide icons to the component icon size", () => {
    render(
      <IconButton label="设置">
        <Settings2 />
      </IconButton>,
    );

    expect(screen.getByRole("button", { name: "设置" })).toHaveClass(
      "[&_svg:not([class*='size-'])]:size-[18px]",
    );
  });
});
