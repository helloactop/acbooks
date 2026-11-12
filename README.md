# acbooks

[English](README.en.md)

acbooks 是一款面向个人的自托管图书馆应用，优先面向 NAS 部署，也计划支持 Linux 服务器。它把实体书和电子书放在同一个书库里，让你在一条书目下管理实体副本的存放位置、多个电子书文件和阅读记录。

项目目前处于早期开发阶段，计划提供 Web 书库管理和阅读、Windows 批量管理及 Android 阅读，并支持离线阅读、跨设备同步和阅读统计。

## 仓库结构

| 路径 | 用途 |
| --- | --- |
| `apps/acbooks-server/` | FastAPI 服务端，源码放在 `app/`，测试放在 `tests/` |
| `apps/acbooks-web/` | React、TypeScript 和 Vite Web 客户端 |
| `apps/acbooks-desktop/` | Tauri Windows 客户端 |
| `apps/acbooks-android/` | Kotlin 和 Jetpack Compose Android 客户端 |
| `packages/acbooks-ui/` | Web 和 Windows 共享 UI |
| `packages/acbooks-api-client/` | 从服务端 OpenAPI 生成的客户端 |
| `docs/` | 架构、开发、API、测试和部署文档 |
| `deploy/`、`scripts/`、`.github/workflows/` | 容器部署、工程脚本和 CI 工作流 |

后端启动及检查命令见[后端开发说明](apps/acbooks-server/README.md)，Web 工程命令见
[Web 开发说明](apps/acbooks-web/README.md)，共享 UI 组件见
[acbooks UI](packages/acbooks-ui/README.md)。各端按[系统架构](docs/architecture/system.md)
中的开发顺序加入，iOS 留待后续阶段。开发前请阅读[开发规范](docs/development/standards.md)。

## 前端开发

需要 Node.js >= 22.12 与 pnpm 10。在仓库根目录执行：

```sh
pnpm install
pnpm dev:web
```

检查命令：

```sh
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

NAS 和 Linux 上的 API、Worker 与数据库迁移见 [Docker Compose 部署](docs/deployment/docker.md)。

项目采用 GPL-3.0-only 许可证。[许可证说明](LICENSE.md)
