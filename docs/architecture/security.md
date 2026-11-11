# acbooks — 安全与认证规范

> 目标路径：`docs/architecture/security.md`  
> 范围：首次初始化、账号密码登录、会话/令牌、接口鉴权、文件与部署安全。  
> 依赖：`architecture/system.md`、`sqlite.md`、`backend.md`、`storage.md`、`jobs.md`，`api/conventions.md`、`development/standards.md`、`deployment/docker.md`。本文只规定安全行为；数据库结构以 `sqlite.md`、接口形状以 `api/conventions.md` 为准。

## 1. 认证方案

- **单用户账号密码**：acbooks 只有一个管理员账号。**Web、Windows、Android 均通过账号密码登录**；不支持匿名书库、公开注册、默认账号或默认密码。首版不接入 OAuth/OIDC。
- **Web**：用户在登录页面输入账号密码，成功后获得服务端可验证、可撤销的会话，浏览器以 `HttpOnly; Secure; SameSite=Lax/Strict` Cookie 保存会话标识；不把访问令牌放在 `localStorage`，也不要求每次请求重新输入密码。
- **Windows/Android**：使用同一账号密码登录；成功后获取有有效期、可撤销的 Bearer 凭据，保存在操作系统安全存储中。不得在客户端保存明文密码。
- **OPDS/WebDAV**：使用独立、可撤销且默认只读的应用凭据；兼容客户端的 Basic 认证仅允许 HTTPS，不可直接复用浏览器会话 Cookie。阅读数据同步仍使用自有 REST API。
- 单用户不等于无需授权：所有敏感 API、WebDAV、OPDS、后台任务提交和文件下载都检查凭据与实际操作权限。

## 2. 首次访问初始化

1. **空数据库 / 未创建账号**：首次打开 Web 自动进入「初始化 acbooks」页面，填写**用户名、密码、确认密码**及**一次性初始化码**；不展示书库或私人文件。
2. **初始化码**：安装时在 `/data/acbooks/secrets/` 生成随机、高熵、仅宿主管理员可读取的初始化码文件（权限 `0600`）；由部署管理员通过宿主机或受信任的容器命令获取并输入网页。**不得写入日志、镜像、默认环境变量或公开 API 响应。**
3. **原子创建**：后端同时验证初始化码与“尚无管理员账号”，在 SQLite 单一写事务及唯一约束下创建唯一管理员；并发初始化请求只能成功一个。前端显示状态不能代替服务端校验。
4. **初始化完成**：立即使初始化码失效并关闭公开创建账号能力；后续访问进入登录页。重复初始化返回稳定冲突/禁止错误，不得覆盖现有密码或创建第二管理员。
5. **异常恢复**：初始化中途失败时不得产生半初始化的可登录状态。忘记密码仅允许通过具有宿主机文件/容器管理权限的**本地恢复命令**重置；不得重新开放首次安装入口。

> **已与权威规范同步**：`GET /api/v1/setup/status`、`POST /api/v1/setup` 及登录/登出等端点以 `docs/api/conventions.md §4` 为准；唯一管理员、Web 会话、客户端/应用令牌的数据库模型以 `docs/architecture/sqlite.md §2.1` 为准。实现时必须通过 Alembic 正式迁移，并更新真实 OpenAPI；不得在本文另造表或修改已发布 migration。

## 3. 密码、会话与请求安全

- 密码使用 **Argon2id**（参数按运行平台基准测试配置）加随机盐单向哈希；绝不明文、可逆加密或记录密码。用户名规范化后唯一；登录失败统一提示，避免泄露账号是否存在。
- 登录/初始化采用限速、失败次数退避和日志审计；禁止无上限尝试。修改密码或执行受保护的恢复操作后，撤销旧会话和令牌；登出应使当前会话立即失效。
- Web Cookie 鉴权的**所有状态修改请求**实施 CSRF 防护及 `Origin`/`Referer` 校验，登录与初始化也需防跨站请求；不采用关闭 CSRF 的项目配置。`Secure` Cookie 要求 HTTPS，除本机受控开发环境外不允许明文传输密码。
- CORS 使用明确来源白名单，不使用带凭据的 `*`；反向代理只信任显式列出的代理 IP 和转发头。Cookie 会话与 Bearer 凭据采用分别验证的认证适配器，最终归一为同一身份/权限上下文。
- 凭据只在创建时展示必要内容，数据库中保存可验证摘要、撤销/过期状态；对登录、退出、Token 撤销和初始化添加真实测试。

## 4. 授权、文件与运行环境

- **Service 层检查授权**，Router、Worker、OPDS、WebDAV 不得绕过。危险操作（文件删除、移动、覆盖、配置变更、备份恢复）需再次核对目标、模式和用户意图。
- `storage_roots.mode=external` 始终只读；`managed` 才允许经确认的写操作。限制目录根、路径穿越和符号链接逃逸；文件操作前后复核真实对象身份，拒绝跨根访问。
- 上传限制大小、类型、解压规模和处理时间；格式解析与元数据来源均视为不可信输入。外部元数据 URL 防 SSRF（拒绝内网、环回、链路本地与云元数据地址及重定向绕过）；不信任客户端 MIME/文件名。
- Docker 使用非 root 运行用户与最小权限；不挂载 Docker Socket、不递归更改 NAS 原书库权限。SQLite 数据库在可靠本地文件系统，外部 NAS 目录默认只读挂载。
- 密钥、数据库、备份与 `secrets/` 不纳入 Git/镜像；日志仅记请求 ID、操作、结果和必要的安全上下文，不记密码、Cookie、Token、完整初始化码及私人书籍内容。对外错误遵循 `application/problem+json`，不泄露路径、SQL 或堆栈。

## 5. 必须验证

- 首次进入进入初始化页；**无初始化码不能抢占账号**；并发初始化只能创建一个管理员；初始化后入口永久关闭，重启不重开。
- 正确账号密码可登录 Web；错误密码、限速、登出、会话过期、改密撤销旧凭据正常；CSRF 与来源伪造请求被拒绝。
- Windows/Android Bearer 与 OPDS/WebDAV 独立凭据可撤销；只读凭据不能写入或获取未授权资源。
- `external` 无法写入，越界路径/符号链接、恶意上传、SSRF、未授权任务调用均被拒绝；NAS 掉线不触发批量删除。
- HTTPS/代理/容器权限/敏感日志/备份恢复场景按真实部署验证；测试失败不得跳过，未测试不得宣称通过。

## 6. 开源借鉴（只借鉴机制，不直接复制代码）

| 参考项目 | 实际做法 | acbooks 的取舍 |
| --- | --- | --- |
| [Komga — ClaimController](https://github.com/gotson/komga/blob/master/komga/src/main/kotlin/org/gotson/komga/interfaces/api/rest/ClaimController.kt) | 首次访问领取服务器、创建管理员 | 使用**一次性初始化码 + 数据库原子约束**，防止公开部署被抢先初始化 |
| [BookLore — SetupController](https://github.com/booklore-app/booklore/blob/develop/booklore-api/src/main/java/org/booklore/controller/SetupController.java) | 初始化状态查询、首位管理员创建 | 初始化后禁用创建入口，Web 自动切换初始化/登录页 |
| [Ermao — PasswordAuthentication](https://github.com/GMD170629/ermao-library/blob/main/apps/api-python/app/modules/auth/application/password_authentication.py) | 密码校验与登录限速分离 | 认证用例独立于 FastAPI、校验失败统一回应 |
| [Tome — Security](https://github.com/bndct-devops/tome/blob/main/backend/core/security.py) | 密码哈希、Bearer/API 凭据与撤销检查 | Web Cookie 与原生端令牌分开；不照搬 URL 查询参数传 Token |
| [Kavita — TokenService](https://github.com/Kareadita/Kavita/blob/develop/Kavita.Services/TokenService.cs) | 访问与刷新凭据生命周期 | 仅引入所需的过期和撤销机制，不预建复杂多用户系统 |

**核心原则：首次 Web 初始化账号密码；以后所有客户端账号密码登录。先保护唯一管理员、会话与 NAS 文件，再扩展高级认证能力。**
