# acbooks — 文件存储与扫描规范

> **仓库路径**：`docs/architecture/storage.md`  
> **适用范围**：NAS/Linux 文件索引、上传、扫描、导入、移动、删除与恢复。  
> **上位规范**：`architecture/system.md`（产品与目录）、`architecture/sqlite.md`（表与约束）、`architecture/backend.md`（模块职责）、`docs/api/conventions.md`（HTTP 契约）、`docs/development/standards.md`（编码与交付）。**本文件只定义文件行为，不增加数据库表或改写 API 约定。**冲突时先核对上位规范。

## 1. 核心原则

- **统一书籍**：`Book` 是稳定书目身份；`BookCopy`、`BookFile` 均为可选关联。文件路径、扫描状态改变，不得自动删除书籍、实体副本、书签、笔记或阅读历史。
- **数据与原件分离**：SQLite 仅存索引和元数据；原始 EPUB/PDF/漫画/音频保留在文件系统。客户端只能通过 FastAPI/受控协议访问。
- **目录不决定书籍身份**：允许书库根目录下直接放文件或多级嵌套；不强制用户改名、按作者/系列整理，也不按文件名/ISBN/哈希擅自合并书目。
- **安全优先**：外部 NAS 目录默认原地、只读索引；所有耗时 I/O 在数据库写事务之外；文件与 SQLite 没有跨系统原子事务，操作必须可重试和恢复。

## 2. 存储结构与权限

```text
/data/acbooks/
├── database/acbooks.db    # SQLite；须位于后端本机可靠文件系统
├── library/              # acbooks 托管的文件（managed）
├── covers/               # 派生封面/缩略图
├── cache/                # 可重建的临时缓存
├── backups/              # 备份
├── logs/                 # 日志
└── secrets/              # 密钥；不入 Git

/mnt/books/                # 示例：已有 NAS 藏书，作为 external 根挂载
```

| `storage_roots.mode` | 读取/扫描 | 上传/移动/改名/删除原件 |
| --- | --- | --- |
| `managed` | 允许 | **经认证、校验及明确操作后**允许；仅使用支持安全写入语义的本地文件系统 |
| `external` | 允许 | **禁止**；不得修改源文件或原目录；派生封面只写到 `/data/acbooks/covers/` |

- 根目录由服务端注册、校验和挂载，**不接受客户端任意绝对路径**；校验目录存在、可读、实际挂载及目录之间的重叠/重复，拒绝把 `database/`、`secrets/`、`backups/` 纳入扫描。
- 远程 SMB/NFS/FUSE 挂载的藏书默认注册为 `external`；**不把活动 SQLite 放到网络共享**。容器须按权限挂载，`external` 建议使用 Docker `:ro` 双重保护。
- 文件路径在数据库中表示为 `storage_roots → storage_objects(root_id, relative_path) → book_files(book_id, storage_object_id)`；`(root_id, relative_path)` 唯一，`BookFile` ID 与 `StorageObject` ID 不因普通路径变更而重建。

## 3. 扫描流程（Worker 执行）

```text
REST 手动扫描 / 监听事件 / 定时校验
                 ↓
    background_jobs 持久化任务（同根去重）
                 ↓
  预检根目录可达、权限、扫描范围及排除项
                 ↓
  枚举文件 → 轻量比较 → 文件稳定性验证
                 ↓
  变更文件：格式检查 → 元数据/哈希提取
                 ↓
  短事务：更新 storage_objects / book_files
                 ↓
  成功完成的扫描范围才核对“文件缺失”
                 ↓
  记录统计、失败原因；客户端查询 jobId
```

1. **扫描输入**：根目录 `root_id` + 可选子目录范围；支持手动全量、事件触发的局部扫描、定期全量核对。监听只产生扫描请求，**不直接决定删除/合并**。
2. **枚举**：单次受限遍历，支持文件位于根目录及任意深度；按格式白名单、忽略规则筛选（隐藏临时文件、回收站、`.part`、应用 staging 等）；不遍历越界符号链接，不访问未授权根目录。
3. **增量判断**：先比较 `(root_id, relative_path)` 与大小、`mtime_ms`；仅对新增/疑似变更文件做分块 SHA-256 和元数据解析。普通扫描不重复哈希全部书库；深度扫描可重新校验文件内容。
4. **写入稳定性**：文件大小和修改时间连续检查稳定后再解析；读取前后复核文件身份/大小/时间，若变化则延期重试。扩展名只是候选，仍需验证实际内容；损坏文件单独报错，不阻断全库。
5. **落库**：先确认物理 `storage_object`；新文件可新建独立 `Book + BookFile`，或在用户明确指定 `bookId` 时关联已有书籍。相同标题、ISBN、哈希仅用于**提示匹配候选**，不擅自合并；不自动覆盖用户编辑的元数据。
6. **移动/修改**：同根移动或改名，只有在来源已缺失且内容身份匹配**唯一、无歧义**时才自动复用既有 ID；存在副本、冲突或不确定时保留原记录并请求确认。文件内容更换后须重新检查阅读 locator 兼容性，不能静默假设进度仍有效。
7. **缺失核对**：**只有根目录可达且对应扫描范围完整成功**，才能把未见文件的 `storage_objects.file_status` 标为 `missing`；失败、权限不足、根目录掉线、扫描取消或异常空目录，不得批量标缺失。标缺失不等于删除数据库关系或物理文件。
8. **任务恢复**：同一根目录同时只执行一项有效扫描；合并频繁监听事件；中断可重试，逐文件处理可幂等。任务状态、进度、失败通过已有 `background_jobs` 暴露，状态词与字段遵循 API/后端规范。

## 4. 上传与导入

- `POST /api/v1/uploads`：鉴权、限制上传大小/格式和目标 `managed` 根；流式写入同目标文件系统上的**随机命名临时文件**，不把整本书读入内存。
- 校验大小、SHA-256、实际格式及上传文件名；拒绝绝对路径、`..`、控制字符、路径分隔符与越界符号链接；限制压缩包解压规模及解析器资源消耗。
- 校验通过后在**同一文件系统**内无覆盖地发布到最终位置，冲突则改名或报告冲突，**绝不静默覆盖**。跨挂载点时先复制到目标临时文件、校验再发布，不能假定跨卷 rename 原子。
- 文件发布与数据库写入使用可恢复步骤：`staging → validated → published → indexed`（**流程阶段，不要求新增数据表/状态列**）。崩溃重试先核对文件和现有索引，不重复制造 Book/File。
- 导入已有 NAS 文件只建立索引，**不默认移动/复制**；来源路径不支持写回时也可正常扫描和阅读。

## 5. 文件操作与数据保护

| 场景 | 必须行为 |
| --- | --- |
| 重命名/移动 `managed` 原件 | 事先检查权限、来源文件身份、目标冲突及磁盘空间；无覆盖发布；完成后更新索引，保留 Book/File 稳定 ID |
| 跨文件系统移动 | 复制 → 校验 → 发布 → 更新索引；仅明确授权后移除原件；中断可恢复 |
| `external` 文件被用户从 NAS 移走 | 下次扫描标记 `missing`，不删除书籍、笔记、会话、进度 |
| 移除书籍与文件的关联 | 先校验关联及阅读数据依赖；不等于物理删除，不因解绑自动清除阅读历史 |
| 用户明确要求删除原件 | **仅限 `managed`**；二次确认目标与影响，优先可恢复的同卷隔离/回收方式；不得以扫描结果触发永久删除 |
| 封面/缓存损坏或丢失 | 可从原件重新生成；不得修改外部原件 |

- 文件操作必须二次校验真实路径、文件身份（避免检查后被替换的 TOCTOU）和目录级符号链接，优先使用相对目录句柄/不跟随链接的操作。
- 文件系统操作与 SQLite 提交分步执行并记录可恢复任务；不得靠一个数据库事务宣称文件也已原子变更。未完成操作须有清晰的错误、补偿或重新执行路径。
- WebDAV、REST、Worker 均调用**同一个文件业务 Service**，不建立其他绕过权限或只读限制的实现。

**已实现（文件后端）**：`app/modules/files/` 提供 `GET /api/v1/books/{bookId}/files`、
`POST /api/v1/uploads`（流式写临时文件、SHA-256、真实格式校验、同盘无覆盖发布）、
`GET /api/v1/files/{bookFileId}/content`（流式、`Range`/`206`/`416`、`ETag`）与
`DELETE /api/v1/files/{bookFileId}`（仅 `managed`，`external` 拒绝）。默认 managed 根位于
`ACBOOKS_LIBRARY_DIR`（默认 `/data/acbooks/library`），首次上传时登记。契约见
[Files API](../api/files.md)。扫描与阅读器尚未实现。

## 6. 代码归属与接口

```text
apps/acbooks-server/app/
├── modules/
│   ├── library/   # 注册/校验 storage_roots
│   ├── files/     # 文件路径、索引、传输、受控文件操作
│   ├── imports/   # 枚举、增量/深度扫描、候选与导入编排
│   └── metadata/  # EPUB/PDF/音频解析、封面生成
├── db/models/     # 仅遵循 sqlite.md 现有 ORM 模型
└── workers/       # background_jobs 领取与恢复
```

- `Router → Service → Repository/文件适配器`；按需建 `router.py`、`schemas.py`、`service.py`、`repository.py`，不为目录形式生成空层级。扫描器只返回**已校验的文件事实/候选**，书籍关联策略由应用 Service 决策。
- HTTP 契约**沿用** `POST /api/v1/storage-roots/{rootId}/scan` → `202 + jobId`、`GET /api/v1/jobs/{jobId}`、`POST /api/v1/uploads`、`GET /api/v1/files/{bookFileId}/content`（流式、按需 Range）；其他协议复用相同授权与文件业务。
- 扫描/哈希/解析可采用**有上限**的线程或进程并发；SQLite 写操作短事务、单一受控写入路径，避免 N+1 查询及无界内存占用。

## 7. 必须验证（pytest；测试代码放 `apps/acbooks-server/tests/`）

1. 根文件/多层子目录、大小写扩展名、隐藏/损坏/不支持格式、符号链接与路径穿越。
2. 首次扫描、增量扫描、深度扫描、重复扫描均幂等；同名、同 ISBN、同 SHA-256 不错误合并书籍。
3. 复制一半的文件不能被当成完整文件；扫描途中持续写入、删除、移动或重命名不会产生错乱关联。
4. NAS 挂载消失、权限被拒、局部失败、异常空目录：**原有书籍/关联/阅读数据不被删除或错误标缺失**。
5. `external` 根不可写；上传、改名、跨卷移动、冲突、无空间、程序崩溃后的恢复与不覆盖。
6. 既有 `Book`/`BookFile` 的 ID 在安全移动后保持；手动删除原件不级联清除阅读会话与笔记。
7. API、Worker、WebDAV 使用同一授权规则；扫描任务可查询进度及失败，不触发长 HTTP 阻塞。

## 8. 开源借鉴（仅参考设计，不复制代码）

| 项目 | 源码/文档 | 借鉴点 |
| --- | --- | --- |
| **BookLore** | [BookDrop 与网络存储说明](https://github.com/booklore-app/booklore) · [事件处理](https://github.com/booklore-app/booklore/blob/develop/booklore-api/src/main/java/org/booklore/service/watcher/LibraryFileEventProcessor.java) | 入库审查、文件稳定性、监听去抖、网络挂载写回限制 |
| **Komga** | [书库扫描设置](https://komga.org/docs/guides/libraries/) · [文件扫描器](https://github.com/gotson/komga/blob/master/komga/src/main/kotlin/org/gotson/komga/domain/service/FileSystemScanner.kt) | 目录排除、增量与深度扫描、可选哈希 |
| **Kavita** | [扫描说明](https://wiki.kavitareader.com/guides/scanner/) · [挂载轮询](https://github.com/Kareadita/Kavita/blob/develop/Kavita.Services/Scanner/FolderPollerService.cs) | 不可靠监听的轮询回退、不可达时保留旧状态 |
| **Ermao Library** | [事件合并](https://github.com/GMD170629/ermao-library/blob/main/apps/api-python/app/modules/imports/infrastructure/library_scan_watcher.py) · [安全发布](https://github.com/GMD170629/ermao-library/blob/main/apps/api-python/app/modules/imports/infrastructure/uploaded_file_publication.py) | 局部扫描合并、文件身份复核、临时文件发布 |
| **Tome** | [scanner.py](https://github.com/bndct-devops/tome/blob/main/backend/services/scanner.py) | 元数据/哈希与 SQLite 写入分离、限制并发 |

**执行原则：先可靠索引、不误删、不误合并，再考虑自动整理和批量写回。新增表、状态字段或端点前，先更新对应权威规范并添加迁移/契约测试。**
