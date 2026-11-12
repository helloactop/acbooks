import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { SegmentedControl } from "./segmented-control";

const OPTIONS = [
  { value: "system", label: "跟随系统" },
  { value: "light", label: "浅色" },
  { value: "dark", label: "深色" },
] as const;

describe("SegmentedControl", () => {
  it("exposes the selected option and returns the chosen value", async () => {
    const onValueChange = vi.fn();
    render(
      <SegmentedControl
        ariaLabel="外观模式"
        onValueChange={onValueChange}
        options={OPTIONS}
        value="system"
      />,
    );

    expect(screen.getByRole("button", { name: "跟随系统" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByRole("button", { name: "深色" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );

    await userEvent.click(screen.getByRole("button", { name: "深色" }));
    expect(onValueChange).toHaveBeenCalledWith("dark");
  });
});
