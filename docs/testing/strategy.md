# 自动化检查

GitHub Actions 在分支推送、Pull Request 或手动触发时运行
[CI 工作流](../../.github/workflows/ci.yml)。后端在 Ubuntu 和 Windows
上以最低支持版本 Python 3.12 运行相同的检查；Web 在两个平台使用 Node.js 22.13
与 24，配合仓库锁定的 pnpm 版本运行相同的检查。两个平台分别报告结果，
某个平台失败时，另一个平台继续运行。

## 本地运行

在独立的 Python 3.12 虚拟环境中，从 `apps/acbooks-server/` 执行以下命令。

```sh
python -m pip install -e ".[dev]"
python -m ruff check .
python -m mypy app tests
python -m pytest
```

Web 与共享 UI 从仓库根目录执行：

```sh
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm i18n:check
pnpm test
pnpm build
```

## 当前覆盖范围

| 检查 | 覆盖范围 |
| --- | --- |
| Ruff | 后端 Python 代码的错误、未使用导入和导入排序 |
| mypy | 后端应用及测试代码的严格类型检查 |
| pytest | FastAPI 配置、健康检查、OpenAPI、业务错误码、500 脱敏诊断和请求 ID |
| pytest | SQLite 临时文件库上的 Alembic 升降级、模型一致性、异常回滚重试、连接参数、外键和唯一约束、任务和文件状态校验、并发写入及在线备份恢复 |
| pytest | 首次管理员初始化的码文件生成与权限、来源和 CSRF 验证、密码校验与 Argon2id 存储、限速、双应用实例并发、事务失败重试及清理失败后的重启 |
| pytest | Web 密码登录与限速、Cookie 属性、会话摘要与过期、重启持久化、会话轮换、CSRF 来源与绑定、单会话注销 |
| ESLint | Web 与共享 UI 的 TypeScript/React 代码、Hooks 规则和导入问题 |
| tsc | Web 与共享 UI 的 TypeScript strict 类型检查 |
| i18n:check | 共享与 Web 翻译资源的语言目录、命名空间、键集合和插值参数一致性 |
| Vitest | 共享 UI 的按钮变体与加载、表单标签/错误关联、禁用输入与密码切换、主题解析/本地保存/浏览器 theme-color、分段选择器键盘操作、浅深色令牌及组件级对比度（Soft/Default/Outline、分段选中、输入框、禁用态）、语言解析/回退/持久化/系统语言变化、Spinner 与密码控件双语文案 |
| Vitest | Web 预览页中英即时切换、`<html lang>` 与本地存储更新、刷新恢复语言、系统语言跟随及切换不丢失输入 |
| Vite | Web 生产构建、共享 UI 源码解析与 Tailwind 样式生成 |

测试使用 pytest 的临时目录创建数据库，不需要真实书库、NAS 挂载或服务凭据。
前端测试使用 jsdom，不连接真实 API。Windows 客户端和 Android 工程初始化后，
再接入各自的构建与检查。

## 验收要求

| 改动范围 | 验收条件 |
| --- | --- |
| API 和错误处理 | 核对 HTTP 状态、问题响应类型、业务码、请求 ID 和有语义的响应头。对 500 同时检查响应与内部日志，验证请求 ID 能关联诊断，异常原文、凭据及内部路径未泄露。契约变化时同步检查 OpenAPI 和生成客户端。 |
| 数据库 | 在空库和上一版本的数据副本上升级，检查版本、数据和外键。测试非法写入、失败回滚和重试。降级在可丢弃的副本上验证，使用在线备份执行恢复检查。 |
| 文件与后台任务 | 使用临时库与隔离文件目录模拟重复执行、权限失败、挂载失联和处理中断。验收恢复后的任务状态、错误摘要、文件关系与原件保留。NAS 挂载和容器权限在目标环境单独验证。 |
| 认证与多端同步 | 对已实现功能覆盖未授权、过期、撤销、重复提交和冲突，分别验收接口行为与数据库结果。 |

交付记录应注明受测提交、平台和运行命令，记录通过、失败及未执行的场景。
新功能纳入验收时，至少覆盖成功路径和相关的失败或恢复路径。

## Docker 部署验收

CI 在 Ubuntu AMD64 和 ARM64 上运行真实 Docker Compose 集成测试。测试使用独立的项目、
临时端口和空数据卷，并在结束后清理容器与数据卷。可在 Docker Engine 运行时从仓库根目录复现。

```sh
python apps/acbooks-server/tests/compose/smoke.py
```

- 验证迁移权限错误阻止 API 和 Worker 启动，修复权限后检查 Alembic 版本、非 root 用户和 SQLite WAL。
- 通过启用证书校验的 Caddy HTTPS 代理完成管理员初始化、登录及登出，验证 Cookie、一次性初始化码和代理信任边界。
- 用共享数据卷启动第二个 Worker，验证排他锁和正常关闭，并验证未就绪的 Worker 无法通过 `up --wait`。
- 重建容器后验证管理员 ID 保留，已使用的初始化码没有重新生成。

目标 NAS 的实际绑定目录、域名和代理配置仍需按 [Docker 部署说明](../deployment/docker.md)验收。
