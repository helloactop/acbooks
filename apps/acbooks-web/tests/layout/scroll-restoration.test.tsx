import { I18nProvider } from "@acbooks/ui";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { appResources } from "../../src/i18n/resources";
import { AppShell } from "../../src/layout/AppShell";
import {
  clearScrollRestoration,
  readScrollPosition,
  rememberReturnScroll,
  saveScrollPosition,
  takeReturnScroll,
} from "../../src/layout/scroll-restoration";

let currentLocation = "";
let currentEntry: { key: string; pathname: string; search: string } = {
  key: "",
  pathname: "",
  search: "",
};

function LocationProbe() {
  const location = useLocation();
  useEffect(() => {
    currentEntry = {
      key: location.key,
      pathname: location.pathname,
      search: location.search,
    };
    currentLocation = `${location.pathname}${location.search}`;
  }, [location.key, location.pathname, location.search]);
  return null;
}

function HistoryControls() {
  const navigate = useNavigate();
  return (
    <>
      <button
        onClick={() => {
          navigate("/b");
        }}
        type="button"
      >
        go-b
      </button>
      <button
        onClick={() => {
          navigate(-1);
        }}
        type="button"
      >
        history-back
      </button>
      <button
        onClick={() => {
          navigate(1);
        }}
        type="button"
      >
        history-forward
      </button>
    </>
  );
}

function renderApp() {
  render(
    <I18nProvider defaultLanguage="zh-CN" resources={appResources}>
      <MemoryRouter initialEntries={["/a"]}>
        <LocationProbe />
        <HistoryControls />
        <AppShell
          accountName="admin"
          expireSession={vi.fn()}
          loggingOut={false}
          onLogout={vi.fn()}
        >
          <Routes>
            <Route element={<h1>Page A</h1>} path="/a" />
            <Route element={<h1>Page B</h1>} path="/b" />
          </Routes>
        </AppShell>
      </MemoryRouter>
    </I18nProvider>,
  );
}

function prepareScrollContainer() {
  const main = screen.getByRole("main");
  let top = 0;
  Object.defineProperty(main, "scrollTop", {
    configurable: true,
    get: () => top,
    set: (value: number) => {
      top = value;
    },
  });
  const scrollTo = vi.fn((options: ScrollToOptions) => {
    top = options.top ?? 0;
  });
  Object.defineProperty(main, "scrollTo", {
    configurable: true,
    value: scrollTo,
  });
  return {
    main,
    scrollTo,
    getTop: () => top,
    setTop: (value: number) => {
      top = value;
    },
  };
}

const LOCATION_A = { key: "default", pathname: "/a", search: "" };
const LOCATION_B = { key: "other", pathname: "/b", search: "" };

beforeEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
  currentLocation = "";
  currentEntry = { key: "", pathname: "", search: "" };
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("scroll restoration", () => {
  it("stores, consumes and clears scroll positions", () => {
    saveScrollPosition(LOCATION_A, 42.7);
    expect(readScrollPosition(LOCATION_A)).toBe(42);
    expect(readScrollPosition(LOCATION_B)).toBe(0);

    rememberReturnScroll(88.4);
    expect(takeReturnScroll()).toBe(88);
    expect(takeReturnScroll()).toBe(0);

    clearScrollRestoration();
    expect(readScrollPosition(LOCATION_A)).toBe(0);
  });

  it("scrolls new pages to top and restores entries on back and forward", async () => {
    const user = userEvent.setup();
    renderApp();
    const container = prepareScrollContainer();

    container.setTop(320);
    fireEvent.scroll(container.main);
    await waitFor(() => {
      expect(readScrollPosition(currentEntry)).toBe(320);
    });
    expect(currentEntry.pathname).toBe("/a");

    await user.click(screen.getByRole("button", { name: "go-b" }));
    await waitFor(() => {
      expect(currentLocation).toBe("/b");
    });
    await waitFor(() => {
      expect(container.scrollTo).toHaveBeenCalledWith({ top: 0 });
    });
    expect(container.getTop()).toBe(0);

    container.setTop(120);
    fireEvent.scroll(container.main);
    await new Promise((resolve) => setTimeout(resolve, 200));
    container.setTop(160);
    fireEvent.scroll(container.main);
    await waitFor(() => {
      expect(readScrollPosition(currentEntry)).toBe(160);
    });
    expect(currentEntry.pathname).toBe("/b");

    await user.click(screen.getByRole("button", { name: "history-back" }));
    await waitFor(() => {
      expect(currentLocation).toBe("/a");
    });
    await waitFor(() => {
      expect(container.scrollTo).toHaveBeenLastCalledWith({ top: 320 });
    });
    expect(container.getTop()).toBe(320);

    await user.click(screen.getByRole("button", { name: "history-forward" }));
    await waitFor(() => {
      expect(currentLocation).toBe("/b");
    });
    await waitFor(() => {
      expect(container.scrollTo).toHaveBeenLastCalledWith({ top: 160 });
    });
    expect(container.getTop()).toBe(160);
  });
});
