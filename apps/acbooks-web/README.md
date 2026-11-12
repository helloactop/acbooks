# acbooks Web

React + TypeScript + Vite Web 客户端。共享主题令牌与基础组件来自
[`@acbooks/ui`](../../packages/acbooks-ui/README.md)。

当前完成初始化/登录、统一已登录 AppShell、响应式侧栏与移动抽屉、全库搜索、
首页最近加入、书库与图书详情、个人设置（账户/外观/语言）及公共组件预览；
上传、扫描、元数据刮削、阅读进度和笔记尚未实现。

## 页面

- `/`：实例尚未初始化时显示初始化表单，否则显示登录表单；已有有效会话时跳转 `/home`。
- `/home`：需要会话的首页，展示真实书库数量与最近加入书籍，复用添加书籍流程。
- `/library`：需要会话的书库，数据来自 Books Core V1 API；页面内搜索、阅读状态、
  类型、标签、文件格式、排序和分页在服务端执行；更多筛选在工具栏下方展开，并显示
  已启用条件数量。侧栏“全部书籍/实体书/电子书”使用真实 `mediaType` 视图，
  进入详情前的查询、筛选、排序、分页、视图与滚动位置保存在当前会话中，返回时恢复。
- `/library/books/:bookId`：图书详情页，使用真实 `GET /api/v1/books/{bookId}` 数据，
  提供横竖屏自适应头部、三行简介与展开/收起、阅读状态更新、编辑弹窗以及信息分页；
  作者可跳转到关键词查询，书架仅展示真实名称；阅读与笔记分页明确标注尚未开放。
- `/settings`：个人设置，复用现有账户、退出、主题与语言状态；`?section=account`、
  `appearance`、`language` 可直接打开对应分类。
- `/preview`：公共组件预览，仅用于开发检查，不要求登录。

已登录页面共享 `AppShell`、侧栏、移动抽屉、全库搜索和底部账户入口；侧栏在
≥1024px 固定显示，窄屏改为抽屉。布局说明见
[管理面板布局规范](../../docs/development/management-layout-spec.md)，详情见
[图书详情页规范](../../docs/development/book-detail-spec.md)。

初始化、登录、书库写操作和退出登录统一使用 HttpOnly Cookie 与 CSRF：
写请求先获取 `GET /api/v1/auth/csrf` 的 `csrfToken` 并放入 `X-CSRF-Token`，
不把认证令牌写入 `localStorage`。会话失效时返回登录页并显示提示。

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

## 本地前后端联调

启动后端时把可信来源设为前端来源，使 Origin/CSRF 校验与浏览器一致：

```sh
ACBOOKS_PUBLIC_ORIGIN=http://localhost:5173 python -m uvicorn app.main:create_app --factory --no-access-log
```

数据库迁移、初始化码生成及 Books API 分别见
[后端运行说明](../../apps/acbooks-server/README.md#首次管理员初始化)与
[Books API](../../docs/api/books.md)。

## 国际化

界面文案位于 `src/i18n/locales/<language>/auth.json`、`library.json` 与
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
- Vitest + Testing Library（共享组件、预览页、认证及书库 API 联调测试）

所有颜色、圆角、尺寸和组件状态以
[UI 设计规范](../../docs/development/ui.md)与
[公共组件规范](../../docs/development/ui-components.md)为准。

当前前端依赖均为 MIT、ISC 或 Apache-2.0 许可，与项目的 GPL-3.0-only
许可证兼容。
