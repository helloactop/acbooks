# acbooks Web

React + TypeScript + Vite Web 客户端。共享主题令牌与基础组件来自
[`@acbooks/ui`](../../packages/acbooks-ui/README.md)。

当前只完成前端工程初始化，尚未实现初始化、登录、书库等业务页面。

## 环境

- Node.js >= 22.12
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
pnpm build
```

开发服务器默认监听 `http://127.0.0.1:5173`，预览构建产物使用
`pnpm preview:web`。

## 技术栈

- React 19、TypeScript strict、Vite
- Tailwind CSS v4（`@tailwindcss/vite`）
- shadcn/ui 组件约定（`components.json`、`cn`、cva）
- Radix Primitives（当前使用 Slot 支持 `asChild`）
- Lucide React 图标
- Vitest + Testing Library（共享组件测试）

所有颜色、圆角、尺寸和组件状态以
[UI 设计规范](../../docs/development/ui.md)与
[公共组件规范](../../docs/development/ui-components.md)为准。

当前前端依赖均为 MIT、ISC 或 Apache-2.0 许可，与项目的 GPL-3.0-only
许可证兼容。
