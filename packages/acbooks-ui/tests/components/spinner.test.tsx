import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Spinner } from "../../src/components/spinner";
import { renderWithI18n } from "../render-with-i18n";

describe("Spinner", () => {
  it("uses the shared loading label in Chinese", () => {
    renderWithI18n(<Spinner />, "zh-CN");

    expect(screen.getByRole("status")).toHaveTextContent("加载中");
  });

  it("uses the shared loading label in English", () => {
    renderWithI18n(<Spinner />, "en");

    expect(screen.getByRole("status")).toHaveTextContent("Loading");
  });

  it("keeps an explicit label override", () => {
    renderWithI18n(<Spinner label="正在同步" />, "en");

    expect(screen.getByRole("status")).toHaveTextContent("正在同步");
  });
});
