# acbooks — 后端设计规范

> 仓库位置：`docs/architecture/backend.md`  
> **上位规范**：`docs/architecture/system.md`（产品与部署）、`docs/architecture/sqlite.md`（表结构与数据库约束）。本文仅补充后端实现，不重复定义数据库模型；出现歧义时以上位规范为准。

## 1. 定位与技术

- **产品**：单用户个人图书馆；实体藏书、电子文件、阅读数据统一归属 `Book`。
- **运行**：NAS 优先，也可部署于普通 Linux 服务器；Docker Compose，支持 AMD64 / ARM64。
- **后端**：Python + FastAPI + Pydantic + SQLAlchemy 2.x + Alembic。
- **数据库**：SQLite（WAL）；仅后端 API 和 Worker 访问。
- **结构**：模块化单体，API 与 Worker 共享业务逻辑；首版不强制 Redis、消息队列或微服务。
- **接口**：REST + OpenAPI；OPDS 负责书籍发现/下载，WebDAV 负责授权文件访问，自有 REST API 负责阅读数据同步。

## 2. 后端目录

```text
apps/acbooks-server/
├── app/
│   ├── main.py              # 应用创建、路由注册、生命周期
│   ├── api/                 # 通用 HTTP 依赖、异常响应
│   ├── core/                # 配置、认证、日志、安全、错误
│   ├── db/
│   │   ├── base.py          # SQLAlchemy Base
│   │   ├── session.py       # Engine、Session、SQLite PRAGMA
│   │   ├── models/          # ORM 模型；按 sqlite.md 定义
│   │   └── alembic/         # 数据库迁移
│   ├── modules/             # 按业务能力组织（见下文）
│   └── workers/             # 持久化后台任务领取与执行
├── tests/                   # 单元、API、数据库及集成测试
└── pyproject.toml
```

**模块内部按需采用** `router.py`（HTTP）、`schemas.py`（Pydantic）、`service.py`（业务）、`repository.py`（查询/持久化）；复杂模块可继续拆分，不创建无用空层级。

## 3. 业务模块职责

| 模块 | 唯一职责 |
| --- | --- |
| `books` | `Book`、作者/译者、标识符、标签和书架；统一书籍身份 |
| `copies` | 实体副本及房间/书架/层级位置 |
| `library` | 书库配置、存储根目录及管理范围 |
| `files` | 文件索引、上传、下载、校验与受控文件操作 |
| `imports` | NAS 扫描、文件发现、导入、去重候选与任务编排 |
| `metadata` | EPUB/PDF/音频元数据提取、封面、外部元数据来源 |
| `reader` | 阅读状态、会话、位置、书签、划线、笔记 |
| `stats` | 根据 `reading_sessions` 计算时长、次数、间隔、日历和趋势 |
| `sync` | 设备登记、增量变更、`mutation_id` 幂等及冲突策略 |
| `opds` | 将书库能力适配为 OPDS，不维护第二套书籍数据 |
| `webdav` | 受权限控制的文件访问，变更走统一文件业务层 |
| `backup` | SQLite 一致性备份、恢复校验及必要配置备份 |

## 4. 代码调用规则

```text
HTTP Router / OPDS / WebDAV / Worker
                 ↓
           Application Service
                 ↓
      Repository / File Adapter
                 ↓
         SQLite / 文件系统
```

1. **Router**：校验请求、取得身份、调用业务服务、返回明确的响应；不直接写 SQL、不处理大文件。
2. **Service**：负责业务规则、授权、事务边界、任务状态与跨模块协调；不依赖 FastAPI 的 `Request`/`Response`。
3. **Repository**：只负责 SQLAlchemy 数据访问；不可隐藏提交事务；模型不得直接作为跨端 API 契约。
4. **Worker**：调用与 API 相同的 Service，不复制业务逻辑；任务状态持久化到现有 `background_jobs`。
5. **跨模块**：通过公开 Service/明确数据类型交互；禁止绕过所属模块直接改写其他模块的数据。
6. 使用 Python 类型标注、Pydantic 输入/输出模型、依赖注入及明确错误码；API 变更同步更新 OpenAPI。

## 5. 文件与数据库边界

- **数据库结构、外键、索引、时间格式、迁移与备份细则以 `sqlite.md` 为准**，不得另建同义表。
- `books.id` 是稳定书籍身份；实体副本和电子文件均为可选关联。扫描、移动或删除文件**不得自动删除**书籍、阅读历史与笔记。
- 文件采用 `storage_roots → storage_objects → book_files` 模型；只保存路径、哈希及元数据，原始文件不入 SQLite。
- `managed` 目录允许经授权的文件操作；`external` 目录以原地索引为主，默认不改名、不移动、不覆盖源文件。
- 目录扫描先校验文件稳定性、解析格式与哈希；书名、ISBN、哈希仅生成匹配候选，不自动合并存在歧义的书籍。
- 解析、哈希、网络、文件搬运在数据库**写事务外**完成；短事务只负责必要验证和数据提交。文件与数据库不宣称跨系统原子事务，任务应可恢复并正确报告部分完成。
- 路径必须限制在已配置存储根目录内；拒绝路径穿越及越界符号链接。

## 6. 阅读统计与同步

- `reading_sessions` 是阅读统计的事实来源；记录实际活跃时间，暂停、后台与长时间无操作不计时。实体书可手动补录会话，`book_file_id` 可为空。
- 阅读次数、累计时长、相邻阅读间隔、热力图和时间线从会话计算；跨设备重叠数据避免重复计时；首版不另建统计汇总表。
- 阅读位置按 `book_file_id` 保存；同步使用设备 ID、`mutation_id`、`revision`，保证重试幂等并明确定义冲突结果。
- 多端同步通过分页快照与增量追赶初始化，离线提交复用书库、文件及 Reader Service。文件内容沿用认证下载与上传，冲突与删除语义见[同步 API](../api/sync.md)。
- OPDS/WebDAV **不替代**阅读进度、笔记、阅读会话的专用 REST 同步接口。

## 7. 后台任务与部署

- 扫描、导入、元数据解析、缩略图等耗时任务由 Worker 处理；API 提交任务后返回任务 ID，提供状态/错误查询。
- Worker 基于 SQLite 中的 `background_jobs` 领取任务；重启时识别中断任务，避免重复发布文件及重复建立书籍记录。
- API 和 Worker 可作为同一 Docker Compose 部署单元的两个进程；均使用 `/data/acbooks/` 下的持久化目录。
- 数据库路径固定为 `/data/acbooks/database/acbooks.db`；数据库必须位于运行后端的可靠本地文件系统，不放在远程 SMB/NFS 上。
- 首版为单用户设计，仍必须有登录认证、凭证安全存储、API/OPDS/WebDAV 授权和访问日志。

## 8. 测试与文档

- **测试代码**：`apps/acbooks-server/tests/`；**测试方案/报告**：`docs/testing/`；**开发规范**：`docs/development/`；**API 文档**：`docs/api/`。
- 必测：统一 Book 关系、文件丢失/移动、重复导入、任务中断恢复、数据库升级、路径越界、接口鉴权、离线同步幂等、阅读时间统计、SQLite 一致性备份恢复。
- 任何修改都必须保持 `system.md`、`sqlite.md` 与本规范一致；新接口需同步更新 OpenAPI 和相关测试。

**实现顺序**：书籍及文件模型 → 上传/扫描/导入 → Web API 与元数据 → OPDS/WebDAV → 阅读、同步与统计；iOS 和插件功能按总架构后续阶段实施。
