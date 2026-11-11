# acbooks — API 设计规范

> 目标路径：`docs/api/conventions.md`  
> 用途：供 AI 与开发者直接实现、测试和维护 acbooks API。  
> 上位规范：`docs/architecture/system.md`、`sqlite.md`、`backend.md`、`docs/development/standards.md`。本文只定义**对外接口契约**，不重新定义表结构、模块职责或部署目录；冲突时先核对上位规范，禁止自行建立另一套实现。

## 1. 基本原则

- 后端：**FastAPI + Pydantic**；REST API 统一前缀 **`/api/v1`**，以自动生成的 **OpenAPI** 为唯一机器接口契约。
- 单用户、统一 `Book`：实体副本、多个电子文件及阅读记录都归属同一书籍；客户端均通过 API，不直连 SQLite。
- **Router → Service → Repository/Adapter**；API、Worker、OPDS、WebDAV 复用业务 Service，Router 不写 SQL、不执行长任务。
- URI 使用**复数资源名 + kebab-case**；HTTP 方法表达行为。路径标识为稳定 ID，不使用书名、ISBN 或文件路径作资源身份。
- SQLite 表/列保持 `snake_case`；**JSON 字段使用 `camelCase`**，由 Pydantic DTO 显式转换；不直接输出 ORM 对象。
- 瞬时时间以 **UTC Unix 毫秒整数**传输；纯日期使用 `YYYY-MM-DD`；阅读日历按指定的 IANA 时区聚合。

## 2. 路由归属与版本

| 功能 | 规范端点示例 |
| --- | --- |
| 书籍 | `GET/POST /api/v1/books`、`GET/PATCH /api/v1/books/{bookId}` |
| 实体副本 | `GET/POST /api/v1/books/{bookId}/copies` |
| 数字文件 | `GET /api/v1/books/{bookId}/files`、`GET /api/v1/files/{bookFileId}/content` |
| 上传/扫描 | `POST /api/v1/uploads`、`POST /api/v1/storage-roots/{rootId}/scan` |
| 后台任务 | `GET /api/v1/jobs/{jobId}` |
| 阅读会话 | `GET/POST /api/v1/books/{bookId}/reading-sessions` |
| 阅读位置 | `GET/PUT /api/v1/reading-positions/{bookFileId}` |
| 笔记划线 | `GET/POST /api/v1/books/{bookId}/annotations` |
| 统计 | `GET /api/v1/stats/overview`、`GET /api/v1/books/{bookId}/stats` |
| 增量同步 | `GET /api/v1/sync/changes`、`POST /api/v1/sync/mutations` |
| 鉴权 | `POST /api/v1/auth/login`、`POST /api/v1/auth/logout` |
| 开放协议 | `/opds`（OPDS）、`/webdav`（WebDAV）；**不套用 JSON REST 响应格式** |

- 此表是资源命名和实现入口，**不是要求第一阶段一次实现所有接口**；仅实现已排期功能。
- 首版使用 `/api/v1`；在 v1 内只做向后兼容的添加。删除/改名字段、改变语义或响应形状须明确版本迁移，不静默破坏客户端。
- 同一业务能力只实现一次；OPDS、WebDAV、REST 仅为不同传输适配层。

## 3. HTTP、数据与分页

- `GET` 查询、`POST` 创建/提交、`PATCH` 部分更新、`PUT` 整体替换或幂等设置、`DELETE` 明确删除；禁止用 GET 触发扫描或写入。
- 状态码：`200` 查询/更新、`201` 创建（含 `Location`）、`202` 已受理异步任务、`204` 成功且无内容；错误使用 `400`、`401`、`403`、`404`、`409`、`413`、`416`、`422`、`429`、`500` 等适当状态。
- 单资源直接返回**类型化 JSON 对象**，不额外嵌套 `data`/`success`；`204` 不返回响应体。
- 列表统一 `?limit=20&offset=0`，`1 ≤ limit ≤ 100`；返回 `items`、`total`、`limit`、`offset`。排序字段白名单、顺序稳定，末位按 `id` 补齐；过滤与分页都在数据库执行。
- 查询参数用 `camelCase`。每个端点只返回需要的字段；大批量请求和上传须有大小、条数和耗时上限。

```json
{
  "items": [{"id": "uuid", "title": "三体", "readingStatus": "reading"}],
  "total": 1,
  "limit": 20,
  "offset": 0
}
```

**错误响应**统一使用 `application/problem+json`（RFC 9457），由全局异常处理器输出；`code` 用于程序判断，`detail` 只用于展示，绝不泄漏内部路径、SQL、堆栈或密钥。

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "code": "REVISION_CONFLICT",
  "detail": "阅读位置已被其他设备更新",
  "requestId": "request-id"
}
```

- Pydantic 校验错误统一映射为 `422` 问题响应，必要时包含结构化 `errors`；业务代码不能依赖错误文案判断。
- 请求 ID 随响应返回（`X-Request-ID`）；服务日志记录请求 ID、耗时和可安全记录的资源 ID，不记录口令、令牌及原始书籍内容。

## 4. 鉴权与安全

- 首次使用必须设置凭证，不存在默认密码。除登录及明确开放的健康检查外，资源接口均要求认证，**单用户不代表无鉴权**。
- Web：受保护的 `HttpOnly`、`Secure`、`SameSite` 会话 Cookie；写请求实施 CSRF 防护及来源校验。原生客户端：`Authorization: Bearer <token>`；令牌可撤销、按需过期并安全保存。
- OPDS/WebDAV 为第三方客户端提供独立、可撤销的访问凭据；如需 HTTP Basic，仅在 **HTTPS** 下使用。禁止在 URL 查询串、日志中存放令牌或长期口令；需要新增凭据持久化表时先更新 `sqlite.md` 并添加迁移。
- CORS 仅允许明确配置的来源；按资源执行授权，不信任客户端提供的设备、路径、文件名、MIME 或归属信息。
- 所有文件路径以配置的 `storage_root` 为边界，规范化并拒绝路径穿越、越界符号链接。`external` 目录默认只读，不能因 API 调用绕过文件管理规则。

## 5. 文件、异步任务与阅读数据

- 上传使用 `multipart/form-data` 或流式传输，先写临时文件、校验稳定性和格式，再交给统一导入服务；不在请求中同步扫描整个书库。上传请求只能指定允许的存储根，不能指定任意服务器绝对路径。
- 下载通过 `GET /api/v1/files/{bookFileId}/content` 流式发送原文件，按需支持 `Range` / `206 Partial Content`、`Content-Length`、`ETag`、`Content-Disposition`；范围无效返回 `416`。不得将完整大文件加载进内存。
- 扫描、导入、解析等异步任务返回 `202` 和 `jobId`；使用现有 `background_jobs` 持久化状态，客户端按 `GET /api/v1/jobs/{jobId}` 查询 `queued/running/succeeded/failed` 与必要错误信息。重启/重试不重复导入。
- 阅读会话可指定 `bookId`、可选 `bookFileId`（实体书手工记录允许为空）、`deviceId`、`startedAt`、`endedAt`、`activeSeconds`、`source`；有效时长与重叠处理遵循 `sqlite.md`。
- 阅读位置按 `bookFileId` 存取，使用**不透明 locator**、`revision` 与 `mutationId`；不能根据百分比猜测精确位置。统计接口仅从 `reading_sessions` 计算，不向 `books` 反写累计时长。

## 6. 离线同步约定

- 同步使用设备 ID、全局递增变更序列及唯一 `mutationId`；客户端保存**最后成功确认**的游标。
- `GET /api/v1/sync/changes?afterSeq=123&limit=100` 按 `seq` 升序返回变化与 `nextSeq`，包括必要的删除标记；不得使用 `updatedAt` 代替同步游标。
- `POST /api/v1/sync/mutations` 批量提交客户端离线操作；每条操作包含 `mutationId`、`deviceId`、目标及期望版本；返回逐条 `accepted/duplicate/conflict/rejected`，不能把部分成功伪装为全部成功。
- 重复 `mutationId` 返回 `duplicate` 且**不重复写入阅读会话**；版本冲突不静默覆盖，提供最新服务端版本与明确冲突信息。单项写入冲突可返回 `409`；若需完整回执持久化字段，先按 `sqlite.md` 增加迁移。
- OPDS 仅负责发现/下载，WebDAV 负责授权文件访问；二者不替代阅读进度、笔记与阅读会话的专用同步 API。

## 7. FastAPI 实现与验证

- 每个业务模块按 `backend.md` 放置 `router.py`、`schemas.py`、`service.py`、`repository.py`（按需创建）；`router` 显式声明 `response_model`、状态码、鉴权依赖和 OpenAPI `tags`/稳定 `operation_id`。
- Pydantic DTO 提供 `camelCase` 映射并严格校验输入；API 文档、客户端类型来自 FastAPI **`/openapi.json`**，生成代码存放 `packages/acbooks-api-client/`，禁止手改生成文件。
- API 发生变化时同步更新 OpenAPI、生成客户端及 `docs/api/`；测试代码存各应用 `tests/`，测试计划/报告存 `docs/testing/`。
- 必测：鉴权、401/403/404、422 错误结构、分页排序、上传/Range 下载、路径越界、扫描异步状态、重复请求与幂等、冲突处理、同步游标、阅读统计不重复计时、OPDS/WebDAV 不越权。
- 使用 **pytest + FastAPI TestClient/httpx** 做接口与契约测试；变更必须通过受影响测试及 Ruff/mypy。未经用户要求，不自动提交、推送、合并或部署。

## 8. 开源参考（取思路，不复制代码）

- [Komga API / OpenAPI](https://komga.org/docs/openapi/komga-api/)：REST 版本、鉴权与可机器读取契约。
- [Kavita API](https://wiki.kavitareader.com/guides/api/)：第三方访问密钥、Swagger/OpenAPI、OPDS 分层。
- [Ermao Reader v5](https://github.com/GMD170629/ermao-library/blob/main/apps/api-python/app/modules/reader/presentation/v5.py)：阅读位置、变更 ID 与进度契约。
- [BookLore ReadingSessionController](https://github.com/booklore-app/booklore/blob/develop/booklore-api/src/main/java/org/booklore/controller/ReadingSessionController.java)：独立阅读会话资源、分页与 202 接收。
- [Tome API](https://github.com/bndct-devops/tome/tree/main/backend/api)：轻量 FastAPI 资源路由和同步接口。

**执行原则：先实现稳定的 REST + OpenAPI 契约；协议适配与客户端生成复用同一业务逻辑。缺少证据时不自行扩表、改变 API 形状或添加第二套状态。**
