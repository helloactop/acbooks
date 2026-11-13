import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Ellipsis } from "lucide-react";
import { describe, expect, it, vi } from "vitest";

import { DropdownMenu } from "../../src/components/dropdown-menu";

describe("DropdownMenu", () => {
  it("opens, moves focus, selects an action and restores trigger focus", async () => {
    const onCopy = vi.fn();
    render(
      <DropdownMenu
        actions={[
          { label: "复制 ISBN", onSelect: onCopy },
          { label: "编辑书籍", onSelect: vi.fn() },
        ]}
        label="更多操作"
      >
        <Ellipsis aria-hidden="true" />
      </DropdownMenu>,
    );
    const trigger = screen.getByRole("button", { name: "更多操作" });

    await userEvent.click(trigger);
    await screen.findByRole("menu");
    const copy = screen.getByRole("menuitem", { name: "复制 ISBN" });
    await waitFor(() => {
      expect(copy).toHaveFocus();
    });

    await userEvent.keyboard("{ArrowDown}");
    expect(screen.getByRole("menuitem", { name: "编辑书籍" })).toHaveFocus();
    await userEvent.keyboard("{ArrowUp}");
    expect(copy).toHaveFocus();

    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("closes after an action and returns focus to the trigger", async () => {
    const onSelect = vi.fn();
    render(
      <DropdownMenu
        actions={[{ label: "复制 ISBN", onSelect }]}
        label="更多操作"
      >
        <Ellipsis aria-hidden="true" />
      </DropdownMenu>,
    );
    const trigger = screen.getByRole("button", { name: "更多操作" });

    await userEvent.click(trigger);
    await userEvent.click(
      await screen.findByRole("menuitem", { name: "复制 ISBN" }),
    );

    expect(onSelect).toHaveBeenCalledOnce();
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("closes on Tab and moves focus past the trigger", async () => {
    render(
      <>
        <DropdownMenu
          actions={[{ label: "复制 ISBN", onSelect: vi.fn() }]}
          label="更多操作"
        >
          <Ellipsis aria-hidden="true" />
        </DropdownMenu>
        <button type="button">下一项</button>
      </>,
    );
    const trigger = screen.getByRole("button", { name: "更多操作" });

    await userEvent.click(trigger);
    await screen.findByRole("menuitem", { name: "复制 ISBN" });
    await userEvent.tab();

    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "下一项" })).toHaveFocus();
  });

  it("portals inside an open dialog and flips above the trigger", async () => {
    render(
      <dialog open>
        <DropdownMenu
          actions={[{ label: "复制 ISBN", onSelect: vi.fn() }]}
          label="更多操作"
        >
          <Ellipsis aria-hidden="true" />
        </DropdownMenu>
      </dialog>,
    );
    const dialog = screen.getByRole("dialog");
    const trigger = screen.getByRole("button", { name: "更多操作" });
    Object.defineProperty(trigger, "getBoundingClientRect", {
      configurable: true,
      value: () => ({
        bottom: 772,
        height: 32,
        left: 100,
        right: 160,
        top: 740,
        width: 60,
        x: 100,
        y: 740,
        toJSON: () => ({}),
      }),
    });

    await userEvent.click(trigger);
    const menu = await screen.findByRole("menu");

    expect(dialog.contains(menu)).toBe(true);
    expect(menu.style.top).toBe("");
    expect(menu.style.bottom).not.toBe("");
    expect(Number.parseFloat(menu.style.maxHeight)).toBeGreaterThan(0);
  });
});
