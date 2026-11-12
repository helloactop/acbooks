import { describe, expect, it } from "vitest";

import {
  libraryUrlSearch,
  readLibraryUrlState,
} from "../../src/library/library-url-state";

const FILTERS = JSON.stringify({
  combinator: "ANY",
  conditions: [
    { field: "tag", operator: "equals", value: "文学" },
  ],
});

describe("library URL state", () => {
  it("reads search, category, filters, sorting, view and pagination", () => {
    const params = new URLSearchParams({
      filters: FILTERS,
      mediaType: "physical",
      offset: "12",
      q: "三体",
      readingStatus: "done",
      sort: "title",
      view: "list",
    });
    const state = readLibraryUrlState(`?${params.toString()}`);

    expect(state.searchTerm).toBe("三体");
    expect(state.type).toBe("physical");
    expect(state.status).toBe("done");
    expect(state.sort).toBe("title");
    expect(state.view).toBe("list");
    expect(state.offset).toBe(12);
    expect(state.filters).toEqual({
      combinator: "ANY",
      conditions: [
        {
          field: "tag",
          id: "stored-filter-0",
          operator: "equals",
          value: "文学",
        },
      ],
    });
  });

  it("resets list filters but keeps the selected media category", () => {
    const state = readLibraryUrlState(
      "?mediaType=physical&reset=1&filters=broken",
    );

    expect(state.type).toBe("physical");
    expect(state.searchTerm).toBe("");
    expect(state.status).toBe("all");
    expect(state.sort).toBe("recent");
    expect(state.view).toBe("grid");
    expect(state.offset).toBe(0);
    expect(state.filters.conditions).toHaveLength(0);
  });

  it("accepts legacy tag and format parameters", () => {
    const state = readLibraryUrlState("?tag=文学&format=epub");

    expect(state.filters.conditions).toEqual([
      expect.objectContaining({
        field: "tag",
        operator: "equals",
        value: "文学",
      }),
      expect.objectContaining({
        field: "format",
        operator: "equals",
        value: "epub",
      }),
    ]);
  });

  it("keeps the offset when serializing a search result page", () => {
    const state = readLibraryUrlState(
      "?q=%E4%B8%89%E4%BD%93&offset=24",
    );

    const search = new URLSearchParams(libraryUrlSearch(state));
    expect(search.get("q")).toBe("三体");
    expect(search.get("offset")).toBe("24");
  });

  it("serializes back to a canonical URL", () => {
    const state = readLibraryUrlState(
      `?${new URLSearchParams({
        filters: FILTERS,
        mediaType: "digital",
        offset: "24",
        readingStatus: "reading",
        sort: "author",
        view: "list",
      }).toString()}`,
    );

    const search = new URLSearchParams(libraryUrlSearch(state));
    expect(search.get("mediaType")).toBe("digital");
    expect(search.get("readingStatus")).toBe("reading");
    expect(search.get("sort")).toBe("author");
    expect(search.get("view")).toBe("list");
    expect(search.get("offset")).toBe("24");
    expect(JSON.parse(search.get("filters") ?? "{}")).toEqual({
      combinator: "ANY",
      conditions: [
        { field: "tag", operator: "equals", value: "文学" },
      ],
    });
  });
});
