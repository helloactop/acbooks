import {
  Button,
  Field,
  IconButton,
  Input,
  PasswordInput,
  SegmentedControl,
  Spinner,
  useTheme,
  type SegmentedControlOption,
  type ThemeMode,
} from "@acbooks/ui";
import { Settings2, Trash2 } from "lucide-react";
import type { ReactNode } from "react";

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

interface PreviewSectionProps {
  children: ReactNode;
  description?: string;
  id: string;
  title: string;
}

function PreviewSection({
  children,
  description,
  id,
  title,
}: PreviewSectionProps) {
  const headingId = `${id}-title`;
  return (
    <section
      aria-labelledby={headingId}
      className="rounded-panel border border-border bg-background p-6"
      id={id}
    >
      <h2 className="text-xl font-semibold" id={headingId}>
        {title}
      </h2>
      {description ? (
        <p className="mt-1 text-sm text-muted-foreground">{description}</p>
      ) : null}
      <div className="mt-4">{children}</div>
    </section>
  );
}

export default function App() {
  const { resolvedTheme, setTheme, theme } = useTheme();

  return (
    <main className="min-h-dvh bg-frame px-4 py-10 text-foreground sm:px-6">
      <div className="mx-auto flex w-full max-w-4xl flex-col gap-8">
        <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-accent-text">acbooks</p>
            <h1 className="mt-1 text-[28px] leading-tight font-semibold">
              公共组件预览
            </h1>
            <p className="mt-2 text-sm text-muted-foreground">
              仅用于开发检查，不是业务页面；业务页面将在后续任务中实现。
            </p>
          </div>
          <div className="flex w-full flex-col items-start gap-2 sm:w-auto sm:shrink-0 sm:items-end">
            <SegmentedControl
              ariaLabel="外观模式"
              onValueChange={setTheme}
              options={THEME_OPTIONS}
              value={theme}
            />
            <span
              aria-live="polite"
              className="text-xs text-muted-foreground"
            >
              当前：{THEME_LABELS[theme]}（{resolvedTheme}）
            </span>
          </div>
        </header>

        <PreviewSection
          description="变体、尺寸、禁用、加载和图标按钮。"
          id="buttons"
          title="按钮"
        >
          <div className="flex flex-wrap items-center gap-3">
            <Button variant="soft">主要操作</Button>
            <Button>确认</Button>
            <Button variant="outline">次要操作</Button>
            <Button variant="ghost">取消</Button>
            <Button variant="danger">删除</Button>
            <Button variant="danger-confirm">确认删除</Button>
          </div>
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <Button size="sm">小按钮</Button>
            <Button size="md">标准按钮</Button>
            <Button size="lg">大按钮</Button>
            <Button disabled>禁用</Button>
            <Button loading>提交中</Button>
            <IconButton label="设置">
              <Settings2 aria-hidden="true" />
            </IconButton>
            <IconButton label="删除" variant="danger">
              <Trash2 aria-hidden="true" />
            </IconButton>
            <Button asChild variant="outline">
              <a href="#forms">跳转到表单预览</a>
            </Button>
          </div>
        </PreviewSection>

        <PreviewSection
          description="标签、说明、错误、必填和禁用状态。"
          id="forms"
          title="表单"
        >
          <div className="grid max-w-2xl gap-5 md:grid-cols-2">
            <Field description="认证表单统一使用 44px 高" label="用户名">
              <Input autoComplete="username" controlSize="auth" />
            </Field>
            <Field description="认证表单统一使用 44px 高" label="密码" required>
              <PasswordInput
                autoComplete="current-password"
                controlSize="auth"
              />
            </Field>
            <Field
              description="从受信任的宿主机获取"
              error="初始化码无效"
              label="初始化码"
            >
              <Input autoComplete="one-time-code" controlSize="auth" />
            </Field>
            <Field
              description="禁用状态保留尺寸、边框和可读文字"
              label="禁用输入"
            >
              <Input defaultValue="不可编辑" controlSize="auth" disabled />
            </Field>
          </div>
        </PreviewSection>

        <PreviewSection
          description="局部操作使用 Spinner，并保留可访问状态文本。"
          id="feedback"
          title="反馈"
        >
          <div className="flex items-center gap-3">
            <Spinner />
            <span className="text-sm text-muted-foreground">
              正在加载预览数据
            </span>
          </div>
        </PreviewSection>
      </div>
    </main>
  );
}
