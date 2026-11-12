import {
  Button,
  Field,
  Input,
  PasswordInput,
  SegmentedControl,
  useTheme,
  type SegmentedControlOption,
  type ThemeMode,
} from "@acbooks/ui";

const THEME_LABELS: Record<ThemeMode, string> = {
  system: "跟随系统",
  light: "浅色",
  dark: "深色",
};

const THEME_OPTIONS: readonly SegmentedControlOption<ThemeMode>[] = [
  { value: "system", label: THEME_LABELS.system },
  { value: "light", label: THEME_LABELS.light },
  { value: "dark", label: THEME_LABELS.dark },
];

export default function App() {
  const { resolvedTheme, setTheme, theme } = useTheme();

  return (
    <main className="min-h-dvh bg-frame px-4 py-10 text-foreground sm:px-6">
      <div className="mx-auto flex max-w-3xl flex-col gap-8">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-sm font-medium text-accent-text">acbooks</p>
            <h1 className="mt-1 text-[28px] leading-tight font-semibold">
              前端工程预览
            </h1>
            <p className="mt-2 text-sm text-muted-foreground">
              共享令牌、基础组件与构建工具已就绪；业务页面暂未实现。
            </p>
          </div>
          <div className="flex flex-col items-end gap-2">
            <SegmentedControl
              ariaLabel="外观模式"
              onValueChange={setTheme}
              options={THEME_OPTIONS}
              value={theme}
            />
            <span className="text-xs text-muted-foreground">
              当前：{THEME_LABELS[theme]}（{resolvedTheme}）
            </span>
          </div>
        </header>

        <section className="rounded-panel border border-border bg-background p-6">
          <h2 className="text-xl font-semibold">按钮</h2>
          <div className="mt-4 flex flex-wrap gap-3">
            <Button variant="soft">主要操作</Button>
            <Button>确认</Button>
            <Button variant="outline">次要操作</Button>
            <Button variant="ghost">取消</Button>
            <Button variant="danger">删除</Button>
            <Button variant="danger-confirm">确认删除</Button>
            <Button loading>提交中</Button>
          </div>
        </section>

        <section className="rounded-panel border border-border bg-background p-6">
          <h2 className="text-xl font-semibold">表单</h2>
          <div className="mt-4 grid max-w-md gap-4">
            <Field description="用于登录 acbooks" label="用户名">
              <Input autoComplete="username" controlSize="auth" />
            </Field>
            <Field label="密码" required>
              <PasswordInput
                autoComplete="current-password"
                controlSize="auth"
              />
            </Field>
          </div>
        </section>
      </div>
    </main>
  );
}
