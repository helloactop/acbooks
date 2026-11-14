# acbooks — AI 开发工作规则

适用范围：`acbooks/` 全仓。此文件是 AI 的**任务入口**，不替代专项设计文档。项目为 **GPL-3.0-only** 的单用户自托管图书馆，优先 NAS 部署，兼容 Linux。

## 1. 阅读顺序与权威来源

开始开发前先阅读 `docs/architecture/system.md`；再按任务阅读相应规范，**不要一次复制全部规范到新文件**。

| 任务 | 必读文档 |
| --- | --- |
| 所有代码修改 | `docs/development/standards.md` |
| SQLite、ORM、迁移、同步数据表 | `docs/architecture/sqlite.md` |
| FastAPI、业务模块、后台 Worker | `docs/architecture/backend.md` |
| 客户端 UI、主题、设计令牌与动效 | [UI 设计规范](docs/development/ui.md) |
| 公共 UI 组件的实现、修改与复用 | [UI 规范 · 组件](docs/development/ui.md#6-组件) |
| 页面布局、组件组合与交互流程 | [UI 规范 · 应用框架与导航](docs/development/ui.md#7-应用框架与导航)、[UI 规范 · 页面](docs/development/ui.md#8-页面) |
| 图书详情页、简介折叠与编辑弹窗 | [UI 规范 · 书籍详情](docs/development/ui.md#86-书籍详情) |
| 管理面板 AppShell、导航、搜索与设置布局 | [UI 规范 · 应用框架与导航](docs/development/ui.md#7-应用框架与导航)、[UI 规范 · 设置](docs/development/ui.md#87-设置) |
| 测试计划、报告 | `docs/testing/` 中适用文档（存在时） |
| REST / OPDS / WebDAV | `docs/api/` 中适用文档（存在时） |
| NAS、Docker、备份与部署 | `docs/deployment/` 中适用文档（存在时） |

权威边界：`system.md` 定义产品和目录；`sqlite.md` 定义数据库；`backend.md` 定义后端职责；`standards.md` 定义开发与交付方式。UI 以 `docs/development/ui.md` 为唯一规范，按任务阅读对应章节。**发现冲突先指出，不另造一套规则**。尚未创建的专项文档不视为现有规范。

## 2. 不可破坏的项目约束

- 一个 `Book` 统一关联实体副本、多个电子文件及阅读数据；不得按实体书/电子书拆分书库，或因扫描不到文件而删除书目、笔记、阅读历史。
- 技术栈遵循总架构：Python/FastAPI + SQLAlchemy 2.x/Alembic + SQLite(WAL)，Web React/TypeScript/Vite，Windows Tauri，Android Kotlin/Compose；iOS 后续支持。
- 模块化单体：API、OPDS、WebDAV 与 Worker **复用业务 Service**；客户端只通过接口访问，不直连 SQLite。
- SQLite 表、外键、时间格式和存储模型以 `sqlite.md` 为唯一依据；结构变化必须增加 Alembic 迁移，禁止改写已发布迁移。
- `reading_sessions` 是阅读时长、次数、间隔和时间线的事实来源；离线同步需要设备标识、`mutation_id` 幂等和明确的冲突处理。
- 数据目录遵循 `/data/acbooks/`；活动 SQLite 数据库不可置于远程 SMB/NFS；已有 NAS 外部书库默认只读索引，不擅自移动、改名、覆盖原文件。

## 3. 代码实现规则

- **先查现有实现和测试，再改代码**。一次处理一个明确目标；优先最小修改，不做无关重构，不创建重复 Service、通用框架或空模块。
- 代码放在既定目录：`apps/acbooks-server/`、`apps/acbooks-web/`、`apps/acbooks-desktop/`、`apps/acbooks-android/`；可复用前端能力按需放 `packages/`。
- 后端调用方向：`Router / 协议适配器 / Worker → Service → Repository / 文件适配器`。Router 不写业务 SQL，Repository 不自行提交事务，跨模块经公开业务接口交互。
- Python 使用类型标注与 Pydantic 校验；TypeScript 使用 `strict`。客户端 DTO 以 FastAPI OpenAPI 为来源，生成的 API 客户端不可手改。
- 文件扫描、解析、哈希、网络请求、文件搬运在数据库写事务**外**；短事务落库；任务可重试、可恢复、可查询失败原因。
- 严格校验输入、权限、NAS 路径边界和符号链接；不得泄露凭证、内部路径、堆栈，或忽略异常伪装成功。
- 保持命名一致：项目标识 `acbooks`；文档 Markdown 用 `kebab-case`，Python 用 `snake_case` / `PascalCase`。

## 4. 测试、文档与交付

- 测试代码放各应用自己的测试目录；测试方案和报告放 `docs/testing/`。按改动运行**最小充分**的真实测试、类型检查与静态检查；涉及数据、文件、同步或认证须覆盖关键失败/恢复路径。
- 后端优先使用 pytest、Ruff、mypy；前端使用项目现有 ESLint、TypeScript 检查和测试脚本。**以仓库实际配置为准，不臆造命令**。
- API 变更同步维护 OpenAPI、生成客户端及 `docs/api/`；架构变更更新相应权威文档。新增依赖先检查 GPL-3.0-only 许可证兼容性。
- 不删除有效断言、不跳过失败、不伪造测试结果；未经明确要求，**不得提交、推送、合并或部署**。
- 交付时简述：**改了什么、涉及哪些文件、运行了哪些检查及结果、仍有哪些未验证事项**。

## 5. AI 执行流程

1. 读 `system.md` 和本任务对应规范，确认目标与禁止事项。
2. 定位现有代码、唯一职责归属和相关测试。
3. 按最小范围实现；涉及表结构先设计迁移，涉及 API 更新契约。
4. 执行受影响检查；如失败，修复原因而非绕过。
5. 汇报改动与证据；不自行扩大范围或执行发布操作。
