# acbooks — API 设计规范

> 目标路径：`docs/api/conventions.md`  
> 用途：供 AI 与开发者直接实现、测试和维护 acbooks API。  
> 上位规范：`docs/architecture/system.md`、`sqlite.md`、`backend.md`、`security.md`、`docs/development/standards.md`。本文只定义**对外接口契约**，不重新定义表结构、模块职责或部署目录；冲突时先核对上位规范，禁止自行建立另一套实现。

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
| 首次初始化 | `GET /api/v1/setup/status`、`POST /api/v1/setup` |
| Web 登录 | `GET /api/v1/auth/csrf`、`POST /api/v1/auth/login`、`POST /api/v1/auth/logout`、`GET /api/v1/auth/me` |
| 原生端登录 | `POST /api/v1/auth/device-login`；设备使用同一管理员账号密码，返回可撤销 Bearer |
| 凭据管理 | `PATCH /api/v1/auth/password`、`GET/POST /api/v1/auth/tokens`、`DELETE /api/v1/auth/tokens/{tokenId}` |
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
- Service 通过 `BusinessError` 和 `BusinessErrorCode` 表达已定义的业务失败，由 HTTP 层统一映射为状态码和固定展示文案。同一业务码的语义保持稳定，即使多个错误共享同一个 HTTP 状态码。
- 已知重试等待时间时，`RATE_LIMITED` 可通过 `retry_after` 提供非负整数秒，HTTP 429 响应将其写入 `Retry-After` 响应头。等待时间未知时省略该响应头，问题响应正文结构保持一致。
- HTTP 500 对外使用 `INTERNAL_ERROR` 和通用文案。服务端按请求 ID 关联异常类型、函数位置和行号，诊断日志省略异常原文、请求数据、源码及内部文件路径。

## 4. 首次初始化、账号密码登录与鉴权（本次明确契约）

**唯一身份：** acbooks 仅有一个管理员，Web、Windows、Android 均用**用户名 + 密码**登录；不允许匿名书库、公开注册或默认密码。账号、会话、Token 的真实数据库模型以 `sqlite.md §2.1` 为准，安全行为以 `security.md` 为准。

### 4.1 初始化与登录路由

| 方法和路径 | 访问条件 | 输入 / 成功响应 | 失败与安全要求 |
| --- | --- | --- | --- |
| `GET /api/v1/setup/status` | 公开 | `200 {"initialized": false}`（或 `true`） | 仅返回布尔值；`Cache-Control: no-store`，不得返回安装码或账号资料 |
| `GET /api/v1/auth/csrf` | 公开（初始化/登录前）、登录后可用 | `200 {"csrfToken":"..."}`；对未认证浏览器设置短期安全的预认证挑战 Cookie；认证后令牌绑定当前会话 | `Cache-Control: no-store`；CSRF 令牌与挑战必须防伪、短期有效，不能存 URL/日志；可用有服务端密钥签名的双提交方案，无需新增表 |
| `POST /api/v1/setup` | 仅 `initialized=false` 且验证安装码 | JSON `{username,password,confirmPassword,setupCode}`；`201 {"id":"uuid","username":"ac"}`，`Location: /api/v1/auth/me` | 必须带 `X-CSRF-Token` 和可信来源校验；安装码错误 `403 SETUP_CODE_INVALID`；已初始化 `409 SETUP_ALREADY_INITIALIZED`；限速；服务端原子检查且只创建一个账号；成功后**仍须单独登录** |
| `POST /api/v1/auth/login` | 公开；**Web Cookie 登录** | JSON `{username,password}`；`200 {"account":{"id":"uuid","username":"ac"}}` + `Set-Cookie` | 必须带 `X-CSRF-Token`、可信来源校验；错误统一 `401 INVALID_CREDENTIALS`，限速；**响应不返回 Cookie 原始值或 Bearer** |
| `POST /api/v1/auth/device-login` | 公开；**Windows/Android 登录** | JSON `{username,password,deviceName?}`；`200 {"accessToken":"仅创建时返回","tokenType":"Bearer","tokenId":"uuid","expiresAt":1800000000000}` | 不产生 Cookie；密码错误同 `INVALID_CREDENTIALS`；受限速/HTTPS保护；Bearer 仅返回一次、不得记录；**无 Cookie 时不需要 CSRF** |
| `GET /api/v1/auth/me` | 需要会话或 Bearer | `200 {"id":"uuid","username":"ac"}` | 未认证 `401`，不返回 `passwordHash`、凭据或内部角色 |
| `POST /api/v1/auth/logout` | 当前有效 Web 会话 / Bearer | `204` 无响应体，撤销**当前**会话或 Token；Web 清除 Cookie | Cookie 认证必须校验 CSRF 和来源，撤销后下一请求 `401` |
| `PATCH /api/v1/auth/password` | 已认证管理员 | JSON `{currentPassword,newPassword}`；`204` | 校验旧密码；强制撤销**全部**会话、Bearer、应用凭据，客户端重新登录；Cookie 请求须 CSRF |

- **Web 会话**：Cookie 使用 `HttpOnly; Secure; SameSite=Lax`（可采用 `Strict`）、`Path=/` 且不设置 `Domain`；正式 HTTPS 可使用 `__Host-` 前缀。服务端检查会话摘要、过期和撤销；前端不把任何认证令牌放在 `localStorage`。开发环境仅允许受控 localhost 例外。
- **CSRF**：`POST /setup`、`POST /auth/login` 及所有基于 Cookie 的写请求，必须同时校验 CSRF 与 `Origin` / `Referer` 来源；使用有绑定的预认证挑战处理“尚未登录”的初始化/登录，不可仅依赖 `SameSite` 或 CORS；缺失/伪造的挑战请求拒绝。原生端只有显式 `Authorization: Bearer`，不能接受会话 Cookie 旁路权限判断。
- **初始化码**：安装时安全生成并放在 `/data/acbooks/secrets/`（`0600`），仅本地部署管理员可取得；不由公开 API 提供、不写入日志、永不作为日常登录凭据；初始化状态只由唯一管理员记录决定。忘记密码走**本地维护命令**，不可重开公开 setup。

首次初始化已开放上述两个 setup 接口及预认证 `GET /api/v1/auth/csrf`。
调用时先获取 `csrfToken` 并保留挑战 Cookie，再从配置的可信来源提交
`POST /api/v1/setup`，把该值放在 `X-CSRF-Token` 请求头。挑战有效期为 10 分钟。
服务端对用户名执行 Unicode NFKC 规范化并去除首尾空白，要求规范化结果为
1–128 个字符。密码须为 12–1024 个字符，与确认密码完全一致。
输入不合规返回 `422 VALIDATION_ERROR`。缺少有效挑战或来源不可信返回
`403 FORBIDDEN`。初始化码验证采用进程级滚动限额，每分钟最多 5 次，失败后退避，
限速时返回 `429 RATE_LIMITED` 和 `Retry-After`。安装命令和服务来源配置见
[后端运行说明](../../apps/acbooks-server/README.md#首次管理员初始化)。

Web 登录先调用 `GET /api/v1/auth/csrf` 获取预认证挑战，再携带挑战 Cookie 和
`X-CSRF-Token` 调用 `POST /api/v1/auth/login`。成功后保存服务端设置的
`__Host-acbooks-session` Cookie，并重新获取与当前会话绑定的 CSRF 令牌。
受控的本机 HTTP 开发环境使用 `acbooks-session`。登录失败对未知账号和错误密码
均返回 `401 INVALID_CREDENTIALS`。每个 API 进程每分钟最多接受 10 次密码校验，
失败会退避，超限返回 `429 RATE_LIMITED` 和 `Retry-After`。

`GET /api/v1/auth/me` 使用会话 Cookie 查询当前账号。
`POST /api/v1/auth/logout` 同时要求会话 Cookie、会话绑定的 `X-CSRF-Token`
和可信来源，成功后清除 Cookie 并撤销当前会话。会话到期或撤销后，两接口返回
`401 UNAUTHORIZED`。有效会话重新登录时会轮换当前 Cookie 并撤销旧会话。
API 进程重启后，现有会话继续有效，客户端通过 `GET /api/v1/auth/csrf` 获取新的 CSRF 令牌。

- **有效期**：建议首版 Web 会话最长 7 天、原生 Bearer 最长 30 天、应用凭据最长 90 天；允许部署方配置更短有效期，严禁永不过期的默认凭据。绝对到期后需要重新登录/重新签发；首版不必设计刷新令牌系统。

### 4.2 OPDS / WebDAV 凭据管理

| 方法和路径 | 要求 |
| --- | --- |
| `GET /api/v1/auth/tokens` | 已认证管理员；分页返回 `id, kind, scope, label, createdAt, expiresAt, revokedAt` 等**元数据**，绝不返回 token 值或 hash |
| `POST /api/v1/auth/tokens` | 已认证管理员（Cookie 请求需 CSRF）；请求 `{label,scope:"read"}`，服务端创建 `kind=application`、`scope=read` 的凭据；`201 {"id":"uuid","token":"仅一次显示","scope":"read","expiresAt":1800000000000}`，`Location` 指向对应 Token 资源 |
| `DELETE /api/v1/auth/tokens/{tokenId}` | 已认证管理员（Cookie 请求需 CSRF）；`204` 撤销指定凭据，下一次请求立即失效；不存在返回 `404` |

- OPDS/WebDAV 客户端使用**单独签发的只读应用凭据**，不能复用管理员密码或 Web Cookie。确需 HTTP Basic 时，仅在 HTTPS 上将独立应用 token 作为凭据校验；受控文件写入仍需明确授权且首版不通过只读凭据开放。
- 按 `kind`、`scope`、过期与撤销状态鉴权；客户端通过安全存储保存 Bearer；**不得在 URL 查询串、Cookie 以外的日志、异常或 OpenAPI 示例中放真实令牌**。
- `GET /auth/tokens` 中的 `kind=device` 代表原生登录后签发的凭据；删除它可以使相应设备立即退出登录。这不改变阅读同步的 `devices` 表。

### 4.3 统一安全响应

- 登录名统一规范化查找，不区分“用户不存在”和“密码错误”；`401 INVALID_CREDENTIALS`、`403 SETUP_CODE_INVALID`、`409 SETUP_ALREADY_INITIALIZED`、`429 RATE_LIMITED` 均遵循本文件 §3 的 **`application/problem+json`** 格式，必要时携带 `Retry-After`。
- 未认证请求 `401`，已认证但无权访问 `403`；`404` 不泄漏私有文件是否存在。初始化与登录接口严格限速，公开状态接口不能透露账号名、安装码或服务器路径。
- CORS 仅允许明确来源，不开放带凭据的 `*`；反向代理只信任配置的转发来源，所有路径、文件、任务及协议权限统一由 Service 复核。

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
- API 发生变化时同步更新 OpenAPI、生成客户端及 `docs/api/`；测试代码存各应用 `tests/`，测试计划/报告存 `docs/testing/strategy.md`。**本文件定义目标契约，不表示接口已实现；实现时将本节端点、DTO 与状态码纳入实际 `/openapi.json`。**
- 必测：安装码不能通过 API 获取、抢先初始化/并发初始化只能创建一个账号、初始化后禁用 setup、Web Cookie 登录与 CSRF、原生账号密码登录/撤销、改密后全部凭据失效、401/403/404/422 错误结构、分页排序、上传/Range 下载、路径越界、扫描异步状态、重复请求与幂等、冲突处理、同步游标、阅读统计不重复计时、OPDS/WebDAV 不越权。
- 使用 **pytest + FastAPI TestClient/httpx** 做接口与契约测试；变更必须通过受影响测试及 Ruff/mypy。未经用户要求，不自动提交、推送、合并或部署。

## 8. 开源参考（取思路，不复制代码）

- [Komga API / OpenAPI](https://komga.org/docs/openapi/komga-api/)：REST 版本、鉴权与可机器读取契约。
- [Kavita API](https://wiki.kavitareader.com/guides/api/)：第三方访问密钥、Swagger/OpenAPI、OPDS 分层。
- [Ermao Reader v5](https://github.com/GMD170629/ermao-library/blob/main/apps/api-python/app/modules/reader/presentation/v5.py)：阅读位置、变更 ID 与进度契约。
- [BookLore ReadingSessionController](https://github.com/booklore-app/booklore/blob/develop/booklore-api/src/main/java/org/booklore/controller/ReadingSessionController.java)：独立阅读会话资源、分页与 202 接收。
- [Tome API](https://github.com/bndct-devops/tome/tree/main/backend/api)：轻量 FastAPI 资源路由和同步接口。

**执行原则：先实现稳定的 REST + OpenAPI 契约；协议适配与客户端生成复用同一业务逻辑。缺少证据时不自行扩表、改变 API 形状或添加第二套状态。**
