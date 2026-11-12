# acbooks Web

React + TypeScript + Vite Web 客户端。共享主题令牌与基础组件来自
[`@acbooks/ui`](../../packages/acbooks-ui/README.md)。

当前完成初始化/登录页、“全部书籍”界面与公共组件预览；书籍 API、书库详情与
其他业务页面尚未实现。

## 页面

- `/`：实例尚未初始化时显示初始化表单，否则显示登录表单；已有有效会话时跳转 `/library`。
- `/library`：需要会话的“全部书籍”界面，使用设计稿的 12 本示例书，本地搜索、筛选、
  排序与网格/列表切换；书籍 API 尚未实现，未完成入口保持禁用。
- `/preview`：公共组件预览，仅用于开发检查，不要求登录。

初始化与登录使用同一 `AuthPanel`，通过 `GET /api/v1/setup/status` 判断入口。
会话保存在 HttpOnly Cookie 中，写请求先获取 `GET /api/v1/auth/csrf` 的
`csrfToken` 并放入 `X-CSRF-Token`，不把认证令牌写入 `localStorage`。

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

开发服务器默认监听 `http://localhost:5173`，预览构建产物使用
`pnpm preview:web`。开发服务器将 `/api` 代理到 `http://127.0.0.1:8000`。

## 本地认证联调

启动后端时把可信来源设为前端来源，使 Origin/CSRF 校验与浏览器一致：

```sh
ACBOOKS_PUBLIC_ORIGIN=http://localhost:5173 python -m uvicorn app.main:create_app --factory --no-access-log
```

数据库迁移和初始化码生成见
[后端运行说明](../../apps/acbooks-server/README.md#首次管理员初始化)。

## 国际化

界面文案位于 `src/i18n/locales/<language>/auth.json` 与
`preview.json`；公共词条由 `@acbooks/ui` 的 `common` 资源提供。`main.tsx` 在根节点包裹
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
- Vitest + Testing Library（共享组件、预览页与认证流程测试）

所有颜色、圆角、尺寸和组件状态以
[UI 设计规范](../../docs/development/ui.md)与
[公共组件规范](../../docs/development/ui-components.md)为准。

当前前端依赖均为 MIT、ISC 或 Apache-2.0 许可，与项目的 GPL-3.0-only
许可证兼容。
