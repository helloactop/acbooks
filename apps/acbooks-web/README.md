# acbooks Web

React + TypeScript + Vite Web 客户端。共享主题令牌与基础组件来自
[`@acbooks/ui`](../../packages/acbooks-ui/README.md)。

当前完成前端工程初始化与公共组件预览，尚未实现初始化、登录、书库等业务页面。

## 环境

- Node.js 22.13 或 24（LTS）
- pnpm 10（仓库根目录 `corepack enable` 后使用）

## 命令

在仓库根目录执行：

```sh
pnpm install
pnpm dev:web
```

其他检查：

```sh
pnpm lint
pnpm typecheck
pnpm test
pnpm i18n:check
pnpm build
```

开发服务器默认监听 `http://127.0.0.1:5173`，预览构建产物使用
`pnpm preview:web`。

## 国际化

界面文案位于 `src/i18n/locales/<language>/preview.json`；公共词条由
`@acbooks/ui` 的 `common` 资源提供。`main.tsx` 在根节点包裹
`I18nProvider` 并传入 `appResources`。支持跟随系统、简体中文和 English，
偏好保存在 `localStorage` 的 `acbooks-language`，切换即时生效并同步
`<html lang>`，不改变 API、路由或业务数据。

新增或修改文案必须同时更新 `zh-CN` 与 `en`，并运行 `pnpm i18n:check`。
该检查同时校验命名空间、键集合和插值参数，并阻止跨包重复命名空间。

品牌图标位于 `public/`：`favicon.ico`、`icons/` 下的多尺寸 PNG、
`apple-touch-icon.png` 及 `site.webmanifest`，由 `index.html` 引用。

## 技术栈

- React 19、TypeScript strict、Vite
- Tailwind CSS v4（`@tailwindcss/vite`）
- shadcn/ui 组件约定（`components.json`、`cn`、cva）
- Radix Primitives（当前使用 Slot 支持 `asChild`）
- Lucide React 图标
- i18next + react-i18next（与 `@acbooks/ui` 共用语言机制）
- Vitest + Testing Library（共享组件与预览页双语测试）

所有颜色、圆角、尺寸和组件状态以
[UI 设计规范](../../docs/development/ui.md)与
[公共组件规范](../../docs/development/ui-components.md)为准。

当前前端依赖均为 MIT、ISC 或 Apache-2.0 许可，与项目的 GPL-3.0-only
许可证兼容。
