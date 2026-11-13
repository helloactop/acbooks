# acbooks — Files API

> 目标路径：`docs/api/files.md`
> 上位规范：[文件存储与扫描规范](../architecture/storage.md)、[API 约定](conventions.md)、
> [SQLite](../architecture/sqlite.md)、[后端架构](../architecture/backend.md)。
> 本文记录电子书文件（EPUB/PDF）后端的已实现契约；不定义新的表结构，不含扫描与阅读器。

## 1. 范围与鉴权

提供图书电子文件的列表、上传、流式下载与删除，复用 `storage_roots → storage_objects →
book_files` 模型；一本书可关联多个文件。实体副本与书目接口不在本文范围。

| 操作 | 鉴权 |
| --- | --- |
| 文件列表 / 内容下载 | 有效 Web 会话 Cookie（`WebSession`） |
| 上传 / 删除 | 有效 Web 会话 Cookie + `X-CSRF-Token` + 可信 `Origin`/`Referer` |

写响应 `Cache-Control: no-store`；上传 `201` 返回 `Location`。未认证 `401 UNAUTHORIZED`，
CSRF/来源失败 `403 FORBIDDEN`。

## 2. 数据与安全规则

- 默认 **managed** 根目录由 `ACBOOKS_LIBRARY_DIR` 配置（默认 `/data/acbooks/library`），
  在首次上传时按需创建目录并登记 `storage_roots(mode=managed)`；不接受客户端绝对路径。
- 最终文件名由服务端生成（`{bookId}/{随机}.{扩展名}`），忽略客户端文件名，从根源杜绝
  路径穿越；发布使用同文件系统**无覆盖**链接，冲突或重复不会静默覆盖。
- 上传先写同盘临时文件，流式计算大小与 SHA-256，校验通过后再发布；大小上限 2 GiB。
- 仅接受真实内容为 PDF 或 EPUB（扩展名不作数）：PDF 须以 `%PDF-` 开头且尾部包含
  `%%EOF`；EPUB 须为合法 ZIP、含 `mimetype=application/epub+zip` 与
  `META-INF/container.xml`，并限制解压总量（4 GiB）、条目数（10000）与元数据单文件
  读取（1 MiB），防止 ZIP 炸弹与资源耗尽。
- 上传在 HTTP 接收层先按 `Content-Length` 早拒超大请求（上限 2 GiB 加少量开销），
  流式写入时再次按累计字节限额，双重防护。
- 同一本书已存在相同 SHA-256 的文件时拒绝重复上传。
- `external` 根的文件只读：下载允许，删除拒绝；不修改 NAS 原始文件。

## 3. 端点

### 3.1 文件列表

`GET /api/v1/books/{bookId}/files` → `200 BookFile[]`

- `BookFile`：`id`、`format`（`epub`/`pdf`）、`fileStatus`（`present`/`missing`）、`sizeBytes`。
- 书目不存在返回 `404 BOOK_NOT_FOUND`。

### 3.2 上传

`POST /api/v1/uploads`（`multipart/form-data`，字段 `bookId` + `file`）→ `201 BookFile`

- 流式写入临时文件并计算 SHA-256、大小与实际格式；超出上限 `413 FILE_TOO_LARGE`；
  非 PDF/EPUB `422 FILE_FORMAT_UNSUPPORTED`；书目不存在 `404 BOOK_NOT_FOUND`；
  同书同内容 `409 FILE_DUPLICATE`。
- 成功后设置 `Location: /api/v1/files/{bookFileId}/content`。
- 重复发布不覆盖；临时文件与失败发布在异常时清理。

### 3.3 下载

`GET /api/v1/files/{bookFileId}/content` → `200` 流式内容

- 响应头：`Accept-Ranges: bytes`、`Content-Length`、`Content-Disposition: attachment;
  filename="{bookId}.{format}"`、`ETag`（`sha256` 或 `size-mtime`）。
- 支持单段 `Range`，命中返回 `206 Partial Content` 与 `Content-Range`；非法/越界
  `416` 并带 `Content-Range: bytes */{size}`。
- 文件内容类型为 `application/pdf` / `application/epub+zip`，不启用 gzip，避免破坏
  `Range`/`Content-Length`。
- 文件记录或物理文件缺失、或路径越出根目录返回 `404 FILE_NOT_FOUND`。

### 3.4 删除

`DELETE /api/v1/files/{bookFileId}?purge=<bool>` → `204`

- `purge=true`（默认）**永久删除原件**：仅允许 `managed` 文件，同一事务删除
  `reading_positions`、`book_files`、`storage_objects`，提交后清理物理文件；`external`
  返回 `409 FILE_EXTERNAL_PROTECTED`，不碰原件。
- `purge=false` **仅解除书目关联**：删除 `reading_positions` 与 `book_files`，保留
  `storage_objects` 记录与原始文件（`external` 同样允许），供后续扫描重新索引。
- 不存在返回 `404 FILE_NOT_FOUND`；物理清理失败不回滚数据库。

## 4. 一致性与清理

1. 流式写临时文件（同盘）、计算大小/SHA-256、校验真实格式。
2. 发布到最终路径（同盘、无覆盖）。
3. 短事务写入 `storage_objects` 与 `book_files`；任一步失败删除已发布文件与临时文件。
4. 删除先提交数据库，再清理物理文件；文件与 SQLite 无跨系统原子事务，按可恢复步骤处理。

## 5. 错误响应

`application/problem+json`，`code` 为稳定业务码：

| 状态 | code | 场景 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 缺少或过期 Web 会话 |
| 403 | `FORBIDDEN` | CSRF、来源或传输校验失败 |
| 404 | `BOOK_NOT_FOUND` | 书目不存在 |
| 404 | `FILE_NOT_FOUND` | 文件记录缺失、物理文件缺失或路径越界 |
| 409 | `FILE_DUPLICATE` | 同一书目已存在相同内容文件 |
| 409 | `FILE_EXTERNAL_PROTECTED` | 删除 `external` 根文件 |
| 413 | `FILE_TOO_LARGE` | 上传超过大小上限 |
| 416 | `REQUESTED_RANGE_NOT_SATISFIABLE` | `Range` 非法或越界 |
| 422 | `FILE_FORMAT_UNSUPPORTED` | 实际内容不是 PDF/EPUB |
| 422 | `VALIDATION_ERROR` | 输入或参数不合法 |
| 500 | `INTERNAL_ERROR` | 服务端异常；通过 `requestId` 关联日志 |

## 6. OpenAPI 与客户端类型

操作 ID：`listBookFiles`、`uploadBookFile`、`getBookFileContent`、`deleteBookFile`。
TypeScript 类型由 `openapi-typescript` 写入 `packages/acbooks-api-client/schema.d.ts`，
不得手改（见 [Books API §6](books.md#6-openapi-与客户端类型)）。
