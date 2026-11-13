# acbooks — SQLite 设计规范

> 仓库位置：`docs/architecture/sqlite.md`。遵循 `docs/architecture/system.md`，仅细化数据库设计，不更改总架构。

## 1. 基本约定

- **数据库**：SQLite（WAL）+ SQLAlchemy 2.x + Alembic；仅 FastAPI 后端及其 Worker 访问，Web、Windows、App 均通过 API。
- **核心原则**：一条 `Book` 统一关联 `BookCopy[]`（实体副本）、`BookFile[]`（电子资源）和阅读数据；**实体书与电子书不分库**。
- **文件分离**：数据库只保存索引和元数据；EPUB、PDF、音频、封面等文件保存在 NAS/服务器文件系统。
- **命名**：表/字段 `snake_case`、ORM 类 `PascalCase`；主键为应用生成的 UUID 字符串（SQLite `TEXT`），外键必须显式声明。
- **时间**：统一使用 UTC Unix 毫秒整数（`INTEGER`）；前端根据用户时区显示。所有写入由应用服务生成时间。
- **单用户**：暂不引入多租户；设备仍有独立 `device_id` 以支持离线同步。

## 2. 数据模型

| 分组 | 表（主要字段） | 关系/职责 |
| --- | --- | --- |
| 书目 | `books(id, title, description, reading_status, cover_path, cover_updated_at, created_at, updated_at)` | 唯一书籍主记录；允许无实体副本、无电子文件 |
| 书目 | `contributors(id, name)`、`book_contributors(book_id, contributor_id, role)` | 作者、译者等；多对多 |
| 书目 | `book_identifiers(id, book_id, scheme, value)` | ISBN/外部 ID；**不按 ISBN 自动合并** |
| 分类 | `tags`、`book_tags`、`shelves`、`shelf_books` | 标签与书架，多对多 |
| 实体 | `locations(id, parent_id, name)` | 房间→书架→层等层级位置 |
| 实体 | `book_copies(id, book_id, location_id, acquired_at, note)` | 一书可有多册实体副本；位置可为空 |
| 存储 | `storage_roots(id, name, root_path, mode)` | NAS 挂载目录/应用管理目录；`mode=managed/external` |
| 存储 | `storage_objects(id, root_id, relative_path, size_bytes, sha256, file_status, mtime_ms)` | 物理文件索引；唯一 `(root_id, relative_path)` |
| 存储 | `book_files(id, book_id, storage_object_id, format)` | 一书多文件；单个存储对象默认只归属一条书籍 |
| 阅读 | `reading_sessions(id, book_id, book_file_id?, device_id?, started_at, ended_at?, active_seconds, source)` | 每次阅读；实体书手动记录时 `book_file_id=NULL` |
| 阅读 | `reading_positions(id, book_file_id, locator_json, percent, revision, updated_at)` | 每个文件一个当前阅读位置；`book_file_id` 唯一 |
| 阅读 | `bookmarks`、`annotations` | 关联书籍；文件定位信息可为空（实体书笔记） |
| 同步 | `devices(id, name, last_seen_at)` | 设备身份 |
| 同步 | `sync_changes(seq, entity_type, entity_id, operation, changed_at)` | 递增 `seq`，供增量拉取 |
| 同步 | `sync_mutations(id, device_id, mutation_id, created_at)` | 唯一 `(device_id, mutation_id)`，防重复提交 |
| 任务 | `background_jobs(id, type, status, payload_json, created_at, updated_at)` | 扫描、导入、解析、整理任务 |
| 认证 | `auth_accounts(id, singleton_slot, username, username_normalized, password_hash, password_changed_at, created_at, updated_at)` | 唯一管理员账号；`singleton_slot=1` 且唯一，不能创建第二账号 |
| 认证 | `auth_sessions(id, account_id, session_token_hash, created_at, expires_at, revoked_at?, last_seen_at?)` | Web Cookie 会话；服务端可撤销，原文 session token 绝不存库 |
| 认证 | `auth_tokens(id, account_id, token_hash, kind, scope, label, created_at, expires_at, revoked_at?, last_used_at?)` | 原生端 Bearer / OPDS、WebDAV 应用凭据；按类型与范围限制 |
| 配置 | `app_settings(key, value_json)` | 系统配置；Alembic 自带迁移版本表 |

### 2.1 单用户认证持久化约束（本次新增）

本小节是 `docs/architecture/security.md` 的**唯一数据库契约**；`security.md` 负责安全行为，API 入参/响应由 `docs/api/conventions.md` 负责。认证仅支持一个管理员，**不引入多租户、角色表或公开注册**。

| 表 | 必须满足的 SQLite 约束与行为 |
| --- | --- |
| `auth_accounts` | `id TEXT PRIMARY KEY`（UUID）；`singleton_slot INTEGER NOT NULL UNIQUE CHECK(singleton_slot = 1)`；`username_normalized TEXT NOT NULL UNIQUE`；`username` 与 `password_hash` 非空；时间字段 `INTEGER NOT NULL`。用户名在应用层去除首尾空格、规范化并生成大小写无关的 `username_normalized`；不允许插入第二个管理员。 |
| `auth_sessions` | `id TEXT PRIMARY KEY`（UUID）、`account_id TEXT NOT NULL REFERENCES auth_accounts(id) ON DELETE RESTRICT`；`session_token_hash TEXT NOT NULL UNIQUE`；`created_at`、`expires_at` 为非空 UTC 毫秒，且 `expires_at > created_at`；`revoked_at`、`last_seen_at` 可空。仅 Web Cookie 使用。 |
| `auth_tokens` | `id TEXT PRIMARY KEY`（UUID）、`account_id TEXT NOT NULL REFERENCES auth_accounts(id) ON DELETE RESTRICT`；`token_hash TEXT NOT NULL UNIQUE`；`kind TEXT NOT NULL CHECK(kind IN ('device','application'))`；`scope TEXT NOT NULL CHECK(scope IN ('full','read'))`；`label TEXT NOT NULL`；时间规则同会话，`revoked_at`、`last_used_at` 可空。首版应用凭据 `kind='application'` 必须 `scope='read'`，用 `CHECK(kind <> 'application' OR scope = 'read')` 固定；`device` 凭据可以为 `full`。 |

- `password_hash` 必须为**含参数与随机盐的 Argon2id 编码字符串**；不能存明文、可逆密文或 SHA-256 密码摘要。参数由目标 NAS 性能测试决定，并符合适用的 OWASP 最低要求。
- Web 会话与客户端/应用令牌必须使用密码学安全随机生成的 **至少 256 位不透明凭据**；数据库只存凭据的 SHA-256 摘要供等值查找。摘要不是密码哈希；不得把原始凭据、Cookie、初始化码写入 `payload_json`、日志或数据库。
- 登录创建会话或令牌；请求验证时同时检查关联账号、`expires_at` 与 `revoked_at`；登出撤销当前会话或令牌；修改/本地重置密码须在事务中**撤销该账号全部现存会话和令牌**。
- **初始化状态只由 `auth_accounts` 是否存在唯一记录判定**，不用 `app_settings` 再存一份 `initialized` 标志；安装码是 `/data/acbooks/secrets/` 内的权限 `0600` 文件，不进入 SQLite。`POST /setup` 在一个 `BEGIN IMMEDIATE` 或等效显式 SQLite 写事务中校验初始化前提并插入管理员；唯一约束保证并发请求只成功一个。初始化事务提交后废止安装码；即使废止文件失败，已有账号也永久阻止公开重试。
- 单用户**不等于**使用全局超级令牌：应用凭据只读、与登录凭据隔离，支持过期、手动撤销；`devices` 仍只表达阅读同步的设备身份，不替代 `auth_tokens`。
- `auth_sessions` 与 `auth_tokens` 应定期清理已过期、已撤销的历史行，但不得删当前有效凭据；当前实现在每次成功登录的写事务中同时清理两类凭据，密码变更和撤销要在下一次请求时立即生效。索引以唯一 token hash 为主，不引入不必要的会话缓存。
- 首次建库通过**初始 Alembic revision** 纳入这三表；如果项目已存在已发布 schema，必须**新增** revision，绝不改写已发布 migration。测试需覆盖“旧库升级、空库初始化、并发抢注、账号唯一性、撤销及恢复”。

**封面存储**：封面文件保存在 `ACBOOKS_COVERS_DIR`（默认 `/data/acbooks/covers`），
`books.cover_path` 只保存服务端生成的文件名，`cover_updated_at` 为替换版本（UTC 毫秒）。
迁移 `0005` 仅用 `ALTER TABLE` 增加 `cover_updated_at`，保留既有行与触发器。
上传/替换/删除封面由 Books 服务完成：上传/替换使用独立版本文件名，先写新文件再提交
数据库，提交成功后清理旧文件，数据库失败时删除新文件并保留原封面；删除封面在提交
成功后清理文件。不得留下半写文件，也不得把客户端文件名写入数据库。

**显式删除**：用户确认删除书目时，同一事务内删除标签/书架/作者/标识关联、实体副本、
`reading_positions`、电子文件索引、阅读会话、书签、划线和笔记，再删除书目；
`reading_positions` 必须在对应 `book_files` 之前删除，因为外键为 RESTRICT。
`storage_objects` 与 NAS 原始文件保留，封面文件在提交后清理。批量删除任一 ID
不存在时整体回滚。

**位置层级与副本归属**：`locations.parent_id` 自引用形成房间→书架→层等多级位置；
更新父级须防止循环（不得设为自身或后代）。删除位置前若存在子位置则拒绝；删除叶子
位置时 `book_copies.location_id` 经外键 `SET NULL` 置空，副本与书目保留。
`locations`（实体存放位置）与虚拟分类 `shelves` 是不同模型，不得混用。

**删除与扫描规则**：扫描不到文件时将 `storage_objects.file_status` 标为 `missing`，不自动删除 `Book`、笔记或阅读历史；移动文件尽量更新存储索引并保持书籍/文件 ID 稳定。ISBN、书名、文件哈希只用于候选匹配，歧义需人工确认。

**状态值约束**：`storage_objects.file_status` 允许 `present`（默认）和 `missing`；
`background_jobs.status` 允许 `queued`、`running`、`succeeded`、`failed`，状态流转按 [后台任务规范](jobs.md) 执行。
迁移 `0002` 用 SQLite 触发器检查新增与更新，保留原表及其外键。旧库存在非法值时，
升级会回滚。应先备份，按真实业务状态修正旧记录，再重试迁移。

**阅读状态与有效时长**：`books.reading_status` 仅允许 `unread`、`reading`、`done`；
`reading_sessions` 在 `ended_at` 非空时要求 `active_seconds × 1000 ≤ ended_at − started_at`。
迁移 `0004` 先校验旧数据，再用 SQLite 触发器约束新增与更新；旧库存在非法值时升级回滚，
应先备份、按真实业务状态修正记录，再重试迁移。

**列表查询索引**：迁移 `0004` 增加 `books(created_at DESC, id)`、
`books(updated_at DESC, id)`、`books(title COLLATE NOCASE, id)`、
`books(reading_status, created_at DESC, id)`、`book_copies(book_id)`、
`book_copies(location_id)` 与 `book_contributors(book_id, role, contributor_id)`，
覆盖默认/标题/更新时间排序、阅读状态筛选、实体副本查询与作者排序的稳定分页。
新增查询应通过 `EXPLAIN QUERY PLAN` 验证是否命中索引，避免无界全表扫描与临时排序。

**阅读记录归属**：`reading_sessions`、`bookmarks` 和 `annotations` 的
`book_file_id` 有值时，必须与关联文件的 `book_id` 一致。迁移 `0003` 校验旧库数据，
禁止写入跨书关联及修改已被阅读记录引用的文件归属。删除文件仍按原外键规则清空
`book_file_id`，保留阅读记录和书目关联。旧库出现不一致时，先备份并确认实际归属，
修正后再升级。

## 3. SQLAlchemy 代码规范

路径：`apps/acbooks-server/app/db/models/`；每个业务模块拥有自己的模型文件，统一从 `app/db/models/__init__.py` 导入，以供 Alembic 发现。

```python
# app/db/base.py
from sqlalchemy.orm import DeclarativeBase

class Base(DeclarativeBase):
    pass
```

```python
# app/db/models/library.py（示例：关键关系和约束）
from sqlalchemy import BigInteger, CheckConstraint, ForeignKey, Index, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column
from app.db.base import Base

class Book(Base):
    __tablename__ = "books"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    title: Mapped[str] = mapped_column(String(500), nullable=False)
    reading_status: Mapped[str] = mapped_column(String(20), default="unread", nullable=False)
    created_at: Mapped[int] = mapped_column(BigInteger, nullable=False)
    updated_at: Mapped[int] = mapped_column(BigInteger, nullable=False)

class StorageRoot(Base):
    __tablename__ = "storage_roots"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    root_path: Mapped[str] = mapped_column(String(2048), nullable=False)
    mode: Mapped[str] = mapped_column(String(20), nullable=False)  # managed / external

class StorageObject(Base):
    __tablename__ = "storage_objects"
    __table_args__ = (UniqueConstraint("root_id", "relative_path"),)
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    root_id: Mapped[str] = mapped_column(ForeignKey("storage_roots.id"), nullable=False)
    relative_path: Mapped[str] = mapped_column(String(2048), nullable=False)
    sha256: Mapped[str | None] = mapped_column(String(64))
    file_status: Mapped[str] = mapped_column(String(20), default="present", nullable=False)

class BookFile(Base):
    __tablename__ = "book_files"
    __table_args__ = (Index("ix_book_files_book", "book_id"),)
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    book_id: Mapped[str] = mapped_column(ForeignKey("books.id", ondelete="RESTRICT"), nullable=False)
    storage_object_id: Mapped[str] = mapped_column(
        ForeignKey("storage_objects.id", ondelete="RESTRICT"), unique=True, nullable=False
    )
    format: Mapped[str] = mapped_column(String(20), nullable=False)  # epub / pdf / m4b ...

class ReadingSession(Base):
    __tablename__ = "reading_sessions"
    __table_args__ = (
        Index("ix_sessions_book_time", "book_id", "started_at"),
        Index("ix_sessions_time", "started_at"),
        CheckConstraint("active_seconds >= 0"),
        CheckConstraint("ended_at IS NULL OR ended_at >= started_at"),
    )
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    book_id: Mapped[str] = mapped_column(ForeignKey("books.id", ondelete="RESTRICT"), nullable=False)
    book_file_id: Mapped[str | None] = mapped_column(ForeignKey("book_files.id", ondelete="SET NULL"))
    device_id: Mapped[str | None] = mapped_column(ForeignKey("devices.id", ondelete="SET NULL"))
    started_at: Mapped[int] = mapped_column(BigInteger, nullable=False)
    ended_at: Mapped[int | None] = mapped_column(BigInteger)
    active_seconds: Mapped[int] = mapped_column(default=0, nullable=False)
    source: Mapped[str] = mapped_column(String(20), nullable=False)  # automatic / manual
```

> 示例仅展示关键关系。其他表依此实现；已发布数据库只通过 Alembic 迁移，禁止使用 `create_all()` 更新结构。

## 4. 连接、事务与迁移

- **数据库位置**：`/data/acbooks/database/acbooks.db`；只能放后端所在设备的可靠本地文件系统，不能放远程 SMB/NFS 网络共享。
- **连接初始化**：每个连接设置 `PRAGMA foreign_keys=ON`、`PRAGMA busy_timeout=5000`；初始化数据库时启用 `journal_mode=WAL`，`synchronous=FULL` 默认优先数据可靠性。
- **事务**：API 和 Worker 共用业务服务；数据库写入使用 SQLAlchemy `Session` 的显式事务。扫描、网络、解压、哈希、文件搬运在事务**外**完成，再短事务保存结果。
- **迁移**：模型变更 → 生成 Alembic revision → 审查 SQL/SQLite batch 重建 → 在旧数据副本测试升级 → 提交迁移。禁止改写已发布 migration。新增迁移后同步更新 `app/db/version.py` 的 `SCHEMA_HEAD_REVISION`，就绪探针据此比较 `alembic_version`。
- **触发器**：SQLite batch 重建会删除原表触发器。涉及状态或阅读归属校验的表时，
  升级和降级均须在同一事务中恢复目标版本仍需的触发器，并测试合法及非法的写入。Alembic 在提交前按
  已应用版本核对必需触发器的名称与所属表，缺失时回滚迁移。
- **备份**：使用 SQLite Online Backup API 或一致性快照；不要在运行中只复制 `.db` 而忽略 WAL；同时备份配置与必要密钥。
- **安全**：由服务端校验文件真实路径始终位于允许的 `storage_root` 内；不跟随越界符号链接；路径与 SQL 均不直接拼接用户输入。

## 5. 查询与同步约定

- 列表和搜索必须**分页**；书名、作者、ISBN 建普通索引，全文检索需要时再加 SQLite FTS5。
- `reading_sessions` 是阅读次数、有效时长、日历热力图、时间线、阅读间隔的**事实来源**；统计从会话计算，不在 `books` 重复累加总时长。
- 阅读间隔 = 本次 `started_at` − 上次 `ended_at`；活跃时长只统计前台实际阅读，暂停和后台不计时，重叠会话按统计规则去重。
- 阅读进度按 `book_file_id` 保存；保存变更使用设备 ID、唯一 `mutation_id` 和 `revision`，保证重试幂等并定义冲突策略。
- OPDS 只提供发现与下载；WebDAV 提供授权文件访问；阅读进度、笔记和统计由专用 REST 同步 API 管理，不通过 OPDS/WebDAV 同步。

## 6. 必须测试

测试代码：`apps/acbooks-server/tests/`；测试计划/报告：`docs/testing/`。

1. 一本书同时关联多个电子文件和实体副本；实体书可无文件，电子书可无副本。
2. 相同书名/ISBN/哈希不会自动合并；同一根目录内重复路径被唯一约束拒绝。
3. 文件消失、扫描中断、NAS 路径变化时，不误删书目与阅读数据。
4. 阅读会话能够计算时长、次数、间隔；跨设备重叠与重复上传不重复统计。
5. 离线同步重试幂等；位置冲突处理有确定结果；API/WebDAV/OPDS 不绕过权限。
6. 外键与迁移有效；从旧数据库升级、异常中断和在线备份恢复均通过验证。
7. 认证表唯一管理员约束有效；并发初始化只能一人成功，Web 会话/原生令牌在过期、登出和改密后立刻失效，旧库升级无数据丢失。

**实现原则：先完成最小关系模型和正确性测试，再加搜索缓存、统计汇总或复杂并发。不得为了新增功能破坏 `docs/architecture/system.md` 的统一书库及目录约定。**
