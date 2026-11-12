# @acbooks/ui

acbooks Web 与 Windows 共享的 UI 基础包，包含语义主题令牌、主题切换和
shadcn/ui 风格基础组件。业务页面不放在此包内。

## 内容

- `src/styles/theme.css`：淡蓝浅色/深色语义令牌、Tailwind 主题映射、
  shadcn/ui 变量映射与基础样式
- `ThemeProvider` / `useTheme`：跟随系统、浅色、深色三种模式，偏好保存在本地；
  密码输入框隐藏浏览器自带 reveal 控件，只保留可访问的可见性切换按钮
- `Button` / `IconButton`：`soft`、`default`、`outline`、`ghost`、`danger`、
  `danger-confirm` 变体及 loading 状态
- `Input` / `PasswordInput` / `Field`：认证表单基础控件与标签、说明、错误关联
- `SegmentedControl`：主题等互斥选项的中性胶囊选择器
- `Spinner`

组件只消费 `ui.md` 的语义令牌，不请求业务 API。当前使用
Radix Slot（`asChild`）、Tailwind CSS、class-variance-authority、clsx、
tailwind-merge 与 Lucide React。依赖均为 MIT、ISC 或 Apache-2.0 许可，
与项目的 GPL-3.0-only 许可证兼容。

## 使用

Web 应用在入口 CSS 中导入主题：

```css
@import "tailwindcss";
@import "@acbooks/ui/theme.css";
@source "../../../packages/acbooks-ui/src";
```

并在根节点包裹 `ThemeProvider`。首屏主题由 `apps/acbooks-web/index.html`
中的内联脚本在 React 挂载前设置，避免主题闪烁。

## 检查

在仓库根目录执行：

```sh
pnpm --filter @acbooks/ui typecheck
pnpm --filter @acbooks/ui test
```
