import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { Button } from "./button";

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
});
