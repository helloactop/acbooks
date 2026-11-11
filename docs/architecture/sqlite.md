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
| 书目 | `books(id, title, description, reading_status, cover_path, created_at, updated_at)` | 唯一书籍主记录；允许无实体副本、无电子文件 |
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
| 配置 | `app_settings(key, value_json)` | 系统配置；Alembic 自带迁移版本表 |

**删除与扫描规则**：扫描不到文件时将 `storage_objects.file_status` 标为 `missing`，不自动删除 `Book`、笔记或阅读历史；移动文件尽量更新存储索引并保持书籍/文件 ID 稳定。ISBN、书名、文件哈希只用于候选匹配，歧义需人工确认。

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
- **迁移**：模型变更 → 生成 Alembic revision → 审查 SQL/SQLite batch 重建 → 在旧数据副本测试升级 → 提交迁移。禁止改写已发布 migration。
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

**实现原则：先完成最小关系模型和正确性测试，再加搜索缓存、统计汇总或复杂并发。不得为了新增功能破坏 `docs/architecture/system.md` 的统一书库及目录约定。**
