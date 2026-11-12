import { describe, expect, it } from "vitest";

import { formatDateTime } from "./presentation";

describe("formatDateTime", () => {
  it("formats created and updated timestamps in UTC", () => {
    const timestamp = Date.UTC(2025, 0, 1, 23, 30);
    const formatted = formatDateTime(timestamp, "en-US");

    expect(formatted).toContain("Jan 1, 2025");
    expect(formatted).toMatch(/11:30\sPM/);
  });
});
