import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Ellipsis } from "lucide-react";
import { describe, expect, it, vi } from "vitest";

import { DropdownMenu } from "./dropdown-menu";

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
});
