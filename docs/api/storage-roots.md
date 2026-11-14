# acbooks — Storage Roots & Scan API

> 目标路径：`docs/api/storage-roots.md`
> 上位规范：[文件存储与扫描规范](../architecture/storage.md)、[后台任务](../architecture/jobs.md)、
> [API 约定](conventions.md)、[SQLite](../architecture/sqlite.md)。
> 本文记录存储根注册与 NAS 扫描的已实现契约。

## 1. 范围与鉴权

注册服务端允许的存储根并对 NAS 书库执行扫描。复用
`storage_roots → storage_objects → book_files` 与 `background_jobs`，不新增表。

| 操作 | 鉴权 |
| --- | --- |
| 存储根列表 / 任务状态 | 有效 Web 会话 Cookie（`WebSession`） |
| 注册 / 删除存储根、发起扫描 | 有效 Web 会话 Cookie + `X-CSRF-Token` + 可信来源 |

## 2. 存储根

`storage_roots(id, name, root_path, mode)`；`mode` 为 `managed` 或 `external`。

### 2.1 列表

`GET /api/v1/storage-roots` → `200 StorageRootNode[]`（含 `fileCount`）。

### 2.2 注册

`POST /api/v1/storage-roots` → `201 StorageRootNode`

```json
{ "name": "NAS 书库", "rootPath": "/mnt/books", "mode": "external" }
```

- `rootPath` 必须为**绝对路径**，指向存在、可读的目录；否则 `422 STORAGE_ROOT_INVALID`。
- 不得与既有存储根重叠（相等、祖先或后代），也不得位于/包含受保护目录
  （`database/`、`covers/`、`secrets/`、`backups/`）；否则 `409 STORAGE_ROOT_OVERLAP`。
- 成功设置 `Location: /api/v1/storage-roots/{rootId}`。

### 2.3 删除

`DELETE /api/v1/storage-roots/{rootId}` → `204`

- 仅删除登记，**不删除磁盘文件**；若仍有 `storage_objects` 引用则
  `409 STORAGE_ROOT_IN_USE`；不存在 `404 STORAGE_ROOT_NOT_FOUND`。

## 3. 扫描

### 3.1 发起扫描

`POST /api/v1/storage-roots/{rootId}/scan?deep=<bool>` → `202 ScanAccepted`

```json
{ "jobId": "uuid" }
```

- 短事务写入 `background_jobs(type=library_scan, status=queued)` 后返回 `jobId`；由 Worker 执行。
- 同一存储根同时最多一个有效扫描（`queued`/`running`）：重复请求返回同一 `jobId`。
- `deep=false`（默认）增量扫描：按 `(root,path)` + 大小 + 修改时间判断，仅对新增/变更文件计算
  SHA-256；`deep=true` 深度扫描对全部重新哈希。

### 3.2 任务状态

`GET /api/v1/jobs/{jobId}` → `200 JobStatus`

`{ id, type, status, createdAt, updatedAt, result?, errorCode? }`；`status` 取
`queued|running|succeeded|failed`，`result` 为扫描计数
（`scanned/created/updated/unchanged/missing/skipped/errors`）。不存在 `404 JOB_NOT_FOUND`。

## 4. 扫描语义（遵循 storage.md / jobs.md）

- **多级遍历**：任意深度；跳过隐藏文件/目录、临时文件（`.part/.tmp/...`）与符号链接；
  仅接受 `.pdf`、`.epub`（内容校验：PDF `%PDF-`+`%%EOF`，EPUB ZIP+`mimetype`+container）。
- **变更识别**：增量以路径+大小+mtime 判定；内容变化更新存储对象并重新哈希，不重建书目。
- **新建书目**：未索引的新文件新建独立 `Book` + `BookFile`（标题取文件名）；同名/同哈希
  **不自动合并**。
- **幂等**：`(root_id, relative_path)` 唯一，重复扫描不产生重复记录。
- **缺失核对**：仅当根目录可达且完整枚举成功时，才把未见且此前存在的对象标为 `missing`；
  **从不删除** `Book`、`BookFile`、实体副本或阅读历史。根目录掉线/枚举失败时任务 `failed`，
  不改任何对象状态。
- **只读**：外部目录只读取/哈希，不移动、不改名、不修改原件。哈希与遍历在数据库写事务之外，
  逐文件短事务落库。

## 5. 错误响应

| 状态 | code | 场景 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 缺少或过期 Web 会话 |
| 403 | `FORBIDDEN` | CSRF、来源或传输校验失败 |
| 404 | `STORAGE_ROOT_NOT_FOUND` | 存储根不存在 |
| 404 | `JOB_NOT_FOUND` | 任务不存在 |
| 409 | `STORAGE_ROOT_OVERLAP` | 与已有根或受保护目录重叠 |
| 409 | `STORAGE_ROOT_IN_USE` | 存储根仍索引文件，无法删除 |
| 422 | `STORAGE_ROOT_INVALID` | 路径非绝对、不存在或不可读 |
| 422 | `VALIDATION_ERROR` | 输入不合法 |
| 500 | `INTERNAL_ERROR` | 服务端异常；通过 `requestId` 关联日志 |

## 6. OpenAPI 与客户端类型

操作 ID：`listStorageRoots`、`createStorageRoot`、`deleteStorageRoot`、
`startStorageScan`、`getJob`。类型由 `openapi-typescript` 写入
`packages/acbooks-api-client/schema.d.ts`，不得手改。

## 7. 扫描元数据与未关联文件

扫描新 EPUB/PDF 时读取内嵌元数据。EPUB 支持 OPF Dublin Core 及 EPUB 2/3
声明的封面。PDF 优先读取 XMP，再回退到文档信息字典，封面取首面已有的 JPEG
缩略图。缺标题时用文件名，缺其他字段时留空。扫描不渲染 PDF 首页、不联网，
也不会用后来提取的值回填已有书目。新文件命中候选时仅写入存储索引，
任务结果的 `pending` 统计本次新增的待处理文件。

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| GET | `/api/v1/storage-roots/{rootId}/unlinked-files` | 按相对路径分页列出当前存在且无 BookFile 的文件。支持 `limit`（1–100，默认 20）及 `offset`（默认 0） |
| GET | `/api/v1/unlinked-files/{objectId}` | 核验文件并返回解析后的书名、作者、简介、ISBN 和最多 20 个候选。候选含 `bookId`、`title` 和匹配原因 `title`、`isbn`、`sha256` |
| POST | `/api/v1/unlinked-files/{objectId}/resolve` | 传 `{"bookId":"…"}` 明确关联已有书目，传 `{}` 或 `{"bookId":null}` 新建独立书目。返回 `bookId`、`fileId` |

读取需要会话，提交需要会话及 CSRF。关联现有书目只增加文件关系。缺失或已处理
的文件返回 404，磁盘文件和索引不一致返回 `409 IMPORT_FILE_CHANGED`，应重扫再试。
未关联状态由 `StorageObject` 与 `BookFile` 的关系推导，复用已有数据表。
生成客户端操作 ID 为 `listUnlinkedFiles`、`previewUnlinkedFile`、`resolveUnlinkedFile`。
