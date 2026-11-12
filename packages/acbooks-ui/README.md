# @acbooks/ui

acbooks Web 与 Windows 共享的 UI 基础包，包含语义主题令牌、主题切换和
shadcn/ui 风格基础组件。业务页面不放在此包内。

## 内容

- `src/styles/theme.css`：淡蓝浅色/深色语义令牌、Tailwind 主题映射、
  shadcn/ui 变量映射与基础样式
- `ThemeProvider` / `useTheme`：跟随系统、浅色、深色三种模式，偏好保存在本地，
  同步 `data-theme`、`color-scheme` 与浏览器 `theme-color`；
  密码输入框隐藏浏览器自带 reveal 控件，只保留可访问的可见性切换按钮
- `Button` / `IconButton`：`soft`、`default`、`outline`、`ghost`、`danger`、
  `danger-confirm` 变体及 loading 状态
- `Input` / `PasswordInput` / `Field`：认证表单基础控件与标签、说明、错误关联；
  `Field` 可通过 `invalid` 标记表单级错误，而不额外显示行内错误文案
- `Select`：保留原生选择语义的表单控件；`Combobox`：支持本地过滤、键盘选择、
  自定义输入和异步建议的搜索选择器
- `DropdownMenu`：锚定触发器的可访问操作菜单
- `SegmentedControl`：互斥选项选择器，提供默认胶囊与矩形圆角 `boxed` 变体，
  使用 radiogroup 语义并支持方向键、Home/End 和 roving focus
- `Spinner`
- `I18nProvider` / `useLanguage` / `createI18n`：简体中文与英语共享实例，
  支持跟随系统、显式选择、本地持久化、即时切换并同步 `<html lang>`
- `Spinner` / `PasswordInput`：内置可访问文案来自共享 `common` 资源，随语言更新

组件只消费 `ui.md` 的语义令牌，不请求业务 API。当前使用
Radix Slot（`asChild`）、Tailwind CSS、class-variance-authority、clsx、
tailwind-merge、Lucide React、i18next 与 react-i18next。依赖均为 MIT、ISC
或 Apache-2.0 许可，与项目的 GPL-3.0-only 许可证兼容。

## 使用

Web 应用在入口 CSS 中导入主题：

```css
@import "tailwindcss";
@import "@acbooks/ui/theme.css";
@source "../../../packages/acbooks-ui/src";
```

并在根节点包裹 `ThemeProvider`。首屏主题由 `apps/acbooks-web/index.html`
中的内联脚本在 React 挂载前设置，避免主题闪烁。

在同一棵根节点内包裹 `I18nProvider`，由应用传入业务资源；共享词条位于
`src/i18n/locales/<language>/common.json`：

```tsx
<ThemeProvider>
  <I18nProvider resources={appResources}>
    <App />
  </I18nProvider>
</ThemeProvider>
```

语言模式为 `system`、`zh-CN`、`en`，偏好保存在 `acbooks-language`。
组件内置文案通过 `common` 命名空间读取，业务文案使用应用自己的命名空间。
新增或修改词条必须同时更新两种语言。

## 检查

在仓库根目录执行：

```sh
pnpm --filter @acbooks/ui typecheck
pnpm --filter @acbooks/ui test
pnpm i18n:check
```

测试位于 `tests/`，按组件、主题和 i18n 分目录组织。测试包含浅色/深色语义令牌的
正文 4.5:1 与控件边界/焦点 3:1 对比度检查，以及主题、键盘、禁用状态、语言解析、
持久化和双语文案回归。
