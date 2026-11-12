import { I18nProvider } from "@acbooks/ui";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { appResources } from "../i18n/resources";
import { AuthPage } from "./AuthPage";

const ACCOUNT = { id: "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f", username: "Admin" };
const PASSWORD = "correct horse battery staple";

const fetchMock = vi.fn();

function mockResponse(
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
): Response {
  const normalized = Object.fromEntries(
    Object.entries(headers).map(([name, value]) => [name.toLowerCase(), value]),
  );

  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get: (name: string) => normalized[name.toLowerCase()] ?? null,
    },
    json: async () => body,
  } as unknown as Response;
}

function problemResponse(
  status: number,
  code: string,
  requestId = "request-1",
  headers: Record<string, string> = {},
): Response {
  return mockResponse(
    {
      type: "about:blank",
      title: "",
      status,
      code,
      detail: "",
      requestId,
    },
    status,
    { "Content-Type": "application/problem+json", "X-Request-ID": requestId, ...headers },
  );
}

function mockApi(
  routes: Record<string, () => Response | Promise<Response>>,
) {
  fetchMock.mockImplementation(
    async (input: string, init: RequestInit = {}) => {
      const key = `${init.method ?? "GET"} ${input}`;
      const handler = routes[key];
      if (handler === undefined) {
        throw new Error(`Unexpected request: ${key}`);
      }
      return handler();
    },
  );
}

function findCall(method: string, suffix: string) {
  return fetchMock.mock.calls.find(([input, init]) => {
    const requestInit = (init ?? {}) as RequestInit;
    return (
      String(input).endsWith(suffix) &&
      (requestInit.method ?? "GET") === method
    );
  });
}

function renderAuth() {
  const onAuthenticated = vi.fn();
  render(
    <I18nProvider defaultLanguage="zh-CN" resources={appResources}>
      <AuthPage onAuthenticated={onAuthenticated} />
    </I18nProvider>,
  );
  return { onAuthenticated };
}

beforeEach(() => {
  window.localStorage.clear();
  document.documentElement.lang = "";
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(window.navigator, "languages", "get").mockReturnValue(["zh-CN"]);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.history.replaceState({}, "", "/");
});

describe("AuthPage", () => {
  it("shows setup for an uninitialized instance", async () => {
    mockApi({
      "GET /api/v1/setup/status": () =>
        mockResponse({ initialized: false }),
    });

    renderAuth();

    expect(
      await screen.findByRole("heading", { level: 1, name: "初始化 acbooks" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("用户名")).toBeInTheDocument();
    expect(screen.getByLabelText("确认密码")).toBeInTheDocument();
    expect(screen.getByLabelText("初始化码")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "初始化" }),
    ).toBeInTheDocument();
  });

  it("shows login for an initialized instance without a session", async () => {
    mockApi({
      "GET /api/v1/setup/status": () =>
        mockResponse({ initialized: true }),
      "GET /api/v1/auth/me": () => problemResponse(401, "UNAUTHORIZED"),
    });

    renderAuth();

    expect(
      await screen.findByRole("heading", { level: 1, name: "登录 acbooks" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("用户名")).toBeInTheDocument();
    expect(screen.getByLabelText("密码")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "登录" })).toBeInTheDocument();
  });

  it("redirects an existing session", async () => {
    mockApi({
      "GET /api/v1/setup/status": () =>
        mockResponse({ initialized: true }),
      "GET /api/v1/auth/me": () => mockResponse(ACCOUNT),
    });

    const { onAuthenticated } = renderAuth();

    await waitFor(() => {
      expect(onAuthenticated).toHaveBeenCalledWith(ACCOUNT);
    });
  });

  it("submits credentials with CSRF and keeps values after a login failure", async () => {
    mockApi({
      "GET /api/v1/setup/status": () =>
        mockResponse({ initialized: true }),
      "GET /api/v1/auth/me": () => problemResponse(401, "UNAUTHORIZED"),
      "GET /api/v1/auth/csrf": () =>
        mockResponse({ csrfToken: "csrf-login" }),
      "POST /api/v1/auth/login": () =>
        problemResponse(401, "INVALID_CREDENTIALS", "request-login"),
    });

    renderAuth();
    await screen.findByRole("heading", { level: 1, name: "登录 acbooks" });

    const user = userEvent.setup();
    const username = screen.getByLabelText("用户名");
    const password = screen.getByLabelText("密码");
    await user.type(username, "  reader  ");
    await user.type(password, "wrong-password");
    await user.click(screen.getByRole("button", { name: "登录" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "用户名或密码不正确，请重试。",
    );
    expect(username).toHaveAttribute("aria-invalid", "true");
    expect(username).toHaveValue("  reader  ");
    expect(password).toHaveValue("wrong-password");

    const loginCall = findCall("POST", "/auth/login");
    expect(loginCall?.[1]).toMatchObject({
      credentials: "same-origin",
      method: "POST",
    });
    expect(
      (loginCall?.[1]?.headers as Record<string, string>)["X-CSRF-Token"],
    ).toBe("csrf-login");
    expect(JSON.parse(String(loginCall?.[1]?.body))).toEqual({
      password: "wrong-password",
      username: "reader",
    });

    await user.type(password, "x");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("disables the form while login is pending and redirects on success", async () => {
    let resolveLogin: ((response: Response) => void) | undefined;
    const pendingLogin = new Promise<Response>((resolve) => {
      resolveLogin = resolve;
    });

    mockApi({
      "GET /api/v1/setup/status": () =>
        mockResponse({ initialized: true }),
      "GET /api/v1/auth/me": () => problemResponse(401, "UNAUTHORIZED"),
      "GET /api/v1/auth/csrf": () =>
        mockResponse({ csrfToken: "csrf-login" }),
      "POST /api/v1/auth/login": () => pendingLogin,
    });

    const { onAuthenticated } = renderAuth();
    await screen.findByRole("heading", { level: 1, name: "登录 acbooks" });

    const user = userEvent.setup();
    await user.type(screen.getByLabelText("用户名"), "reader");
    await user.type(screen.getByLabelText("密码"), PASSWORD);
    await user.click(screen.getByRole("button", { name: "登录" }));

    const submit = screen.getByRole("button", { name: "登录" });
    expect(submit).toBeDisabled();
    expect(submit).toHaveAttribute("aria-busy", "true");
    expect(screen.getByLabelText("用户名")).toBeDisabled();
    expect(screen.getByLabelText("密码")).toBeDisabled();

    await act(async () => {
      resolveLogin?.(mockResponse({ account: ACCOUNT }));
    });

    await waitFor(() => {
      expect(onAuthenticated).toHaveBeenCalledWith(ACCOUNT);
    });
  });

  it("initializes the account and returns to login without auto sign-in", async () => {
    mockApi({
      "GET /api/v1/setup/status": () =>
        mockResponse({ initialized: false }),
      "GET /api/v1/auth/csrf": () =>
        mockResponse({ csrfToken: "csrf-setup" }),
      "POST /api/v1/setup": () => mockResponse(ACCOUNT, 201),
    });

    const { onAuthenticated } = renderAuth();
    await screen.findByRole("heading", { level: 1, name: "初始化 acbooks" });

    const user = userEvent.setup();
    await user.type(screen.getByLabelText("用户名"), "  Admin  ");
    await user.type(screen.getByLabelText("密码"), PASSWORD);
    await user.type(screen.getByLabelText("确认密码"), PASSWORD);
    await user.type(screen.getByLabelText("初始化码"), " code-123 ");
    await user.click(screen.getByRole("button", { name: "初始化" }));

    expect(
      await screen.findByRole("heading", { level: 1, name: "登录 acbooks" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "初始化成功，请使用新账号登录。",
    );
    expect(screen.getByLabelText("用户名")).toHaveValue("Admin");
    expect(onAuthenticated).not.toHaveBeenCalled();

    const setupCall = findCall("POST", "/setup");
    expect(
      (setupCall?.[1]?.headers as Record<string, string>)["X-CSRF-Token"],
    ).toBe("csrf-setup");
    expect(JSON.parse(String(setupCall?.[1]?.body))).toEqual({
      confirmPassword: PASSWORD,
      password: PASSWORD,
      setupCode: "code-123",
      username: "Admin",
    });
  });

  it("validates setup fields before calling the API", async () => {
    mockApi({
      "GET /api/v1/setup/status": () =>
        mockResponse({ initialized: false }),
    });

    renderAuth();
    await screen.findByRole("heading", { level: 1, name: "初始化 acbooks" });

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "初始化" }));

    expect(screen.getByText("请输入用户名。")).toBeInTheDocument();
    expect(
      screen.getByText("密码至少需要 12 个字符。"),
    ).toBeInTheDocument();
    expect(screen.getByText("请输入初始化码。")).toBeInTheDocument();
    expect(findCall("POST", "/setup")).toBeUndefined();
  });

  it("maps setup errors and Retry-After to localized messages", async () => {
    mockApi({
      "GET /api/v1/setup/status": () =>
        mockResponse({ initialized: false }),
      "GET /api/v1/auth/csrf": () =>
        mockResponse({ csrfToken: "csrf-setup" }),
      "POST /api/v1/setup": () =>
        problemResponse(429, "RATE_LIMITED", "request-rate", {
          "Retry-After": "7",
        }),
    });

    renderAuth();
    await screen.findByRole("heading", { level: 1, name: "初始化 acbooks" });

    const user = userEvent.setup();
    await user.type(screen.getByLabelText("用户名"), "Admin");
    await user.type(screen.getByLabelText("密码"), PASSWORD);
    await user.type(screen.getByLabelText("确认密码"), PASSWORD);
    await user.type(screen.getByLabelText("初始化码"), "code-123");
    await user.click(screen.getByRole("button", { name: "初始化" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "尝试次数过多，请在 7 秒后重试。",
    );
  });

  it("retries after a status load failure", async () => {
    fetchMock
      .mockRejectedValueOnce(new TypeError("offline"))
      .mockResolvedValueOnce(mockResponse({ initialized: false }));

    renderAuth();

    expect(
      await screen.findByRole("heading", { level: 1, name: "无法连接 acbooks" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "无法连接到服务器，请稍后重试。",
    );

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "重试" }));

    expect(
      await screen.findByRole("heading", { level: 1, name: "初始化 acbooks" }),
    ).toBeInTheDocument();
  });

  it("switches the auth page language before sign-in", async () => {
    mockApi({
      "GET /api/v1/setup/status": () =>
        mockResponse({ initialized: false }),
    });

    renderAuth();
    await screen.findByRole("heading", { level: 1, name: "初始化 acbooks" });

    const user = userEvent.setup();
    await user.click(screen.getByRole("radio", { name: "English" }));

    expect(
      await screen.findByRole("heading", { level: 1, name: "Set up acbooks" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Confirm password")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Set up acbooks" }),
    ).toBeInTheDocument();
    expect(window.localStorage.getItem("acbooks-language")).toBe("en");
  });
});
