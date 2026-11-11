# acbooks — 开发规范

> 目标路径：`docs/development/standards.md`  
> 适用范围：整个 `acbooks/` 仓库；供开发者与 AI 编程工具执行。

## 1. 权威文档与原则

- `docs/architecture/system.md`：产品定位、技术栈、项目与数据目录。
- `docs/architecture/sqlite.md`：数据表、主外键、时间格式、事务与迁移。
- `docs/architecture/backend.md`：后端模块职责、调用关系与 Worker。
- 本文只规定**如何开发与交付**，不重新定义以上内容。发现冲突时先指出，修改对应权威文档，不自行发明第二套规则。
- **单用户、NAS 优先、Linux 服务器兼容、GPL-3.0-only**；以最小可靠实现为先，不预建微服务、Redis、通用插件框架或无用抽象。

## 2. 代码与文件位置

| 内容 | 放置位置 |
| --- | --- |
| 后端及测试 | `apps/acbooks-server/app/`、`apps/acbooks-server/tests/` |
| Web | `apps/acbooks-web/` |
| Windows | `apps/acbooks-desktop/` |
| Android / iOS（后续） | `apps/acbooks-android/`、`apps/acbooks-ios/` |
| Web/Windows 共用 UI、生成的 API 客户端 | `packages/acbooks-ui/`、`packages/acbooks-api-client/` |
| 架构 / 开发 / 测试文档 | `docs/architecture/`、`docs/development/`、`docs/testing/` |
| API / 部署文档 | `docs/api/`、`docs/deployment/` |
| 容器与脚本 | `deploy/`、`scripts/` |

- 项目和服务标识统一用 `acbooks`；目录及 Markdown 文件名使用英文小写，文档文件采用 `kebab-case`。
- Python 模块、函数、变量用 `snake_case`；类用 `PascalCase`；常量用 `UPPER_SNAKE_CASE`。
- TypeScript 开启 `strict`，明确输入输出类型；避免 `any`、无必要的非空断言与关闭类型检查。
- 一项能力只有一个实现归属；优先修改现有模块，不复制逻辑，不创建万能 `utils/services` 或空分层。共享代码必须有实际复用场景。

## 3. 后端代码写法

```text
HTTP Router / OPDS / WebDAV / Worker
                  ↓
        业务 Service（用例、授权、事务）
                  ↓
     Repository / 文件与外部服务适配器
                  ↓
             SQLite / NAS
```

- 模块放在 `apps/acbooks-server/app/modules/<capability>/`，**按需**建立 `router.py`、`schemas.py`、`service.py`、`repository.py`；复杂模块再分层。
- `Router`：使用 Pydantic 校验输入、调用 Service、返回明确 DTO/错误码；不写 SQL、不解析大文件。
- `Service`：实现业务规则、授权、事务与跨模块协调；不依赖 FastAPI `Request/Response`。
- `Repository`：使用 SQLAlchemy 2.x 类型化查询；不擅自 `commit()`，不把 ORM 对象直接返回给客户端。
- `Worker`：从既有 `background_jobs` 领取任务并调用相同的 Service；任务状态、幂等、取消/失败与重启恢复必须明确。
- 跨模块通过公开 Service/显式 DTO 调用，不直接修改其他模块的表；依赖单向，禁止循环依赖。
- 外部输入使用参数化查询和路径校验；异常保留原因，用户响应不暴露密钥、内部路径或堆栈，禁止空 `except` 和把失败当成功。

## 4. 数据、文件与同步边界

- **严格遵循 `sqlite.md`**：ORM 模型只定义一次；变更结构必须新增 Alembic 迁移，不修改已发布迁移；时间统一 UTC Unix 毫秒。
- SQLite 数据库固定在 `/data/acbooks/database/acbooks.db`，位于后端本机可靠文件系统；不通过 SMB/NFS 远程共享打开活动数据库。
- 扫描、解压、哈希、网络请求和文件搬运在数据库写事务**外**完成；事务尽量短，提交/回滚责任明确。
- `Book` ID 稳定；实体副本和电子文件均可选。文件缺失仅标记状态，**不得自动删除**书籍、笔记、阅读记录；同名/同 ISBN/同哈希不自动合并。
- NAS 已有目录可原地扫描；`external` 根目录默认不改写，`managed` 根目录才允许经过校验的文件操作。上传需临时写入、验证、受控发布；数据库与文件操作失败应可恢复，不宣称跨系统原子性。
- 服务端核验存储根目录、路径穿越、符号链接、文件权限及资源归属；不信任客户端传来的路径、文件名、MIME 或权限声明。
- 阅读统计仅以 `reading_sessions` 为事实来源；设备上传用 `mutation_id` 幂等，阅读位置与冲突规则遵循 `sqlite.md`/`backend.md`。

## 5. API 与各端协作

- FastAPI 的 Pydantic 模型和 OpenAPI 是 **API 契约唯一来源**；客户端类型/SDK 从 OpenAPI 生成，生成文件不手改。
- 所有列表查询必须分页且排序稳定；API 返回必要字段，错误有稳定代码；破坏性接口变更须明确版本或迁移方案。
- Web（React + TypeScript + Vite）与 Windows（Tauri + React）优先复用 UI 和 API 客户端；页面不直接操作数据库，也不复制后端业务规则。
- Android（Kotlin + Compose）负责原生阅读、离线存储与同步；iOS 按总架构后续实施。
- OPDS 仅适配书籍发现/下载；WebDAV 适配授权文件访问；阅读进度、笔记及会话使用**自有 REST 同步 API**。三者共用后端业务层。

## 6. 测试与交付

- 先检查相关代码与三份架构规范，明确改动范围；一次实现一个清晰功能，不混入无关重构。
- 修改业务逻辑须补真实断言：正常、失败与关键边界；数据库修改验证迁移与外键；文件操作验证中断、越界和不误删；同步验证重复上传/冲突；阅读统计验证重叠时间不重复计数。
- 后端使用 **pytest、Ruff、mypy**；前端使用 **ESLint、TypeScript 类型检查及单元测试**。按受影响模块运行已有脚本；提交/发布时由 CI 检查相应质量门禁。不要为通过测试而删除断言、跳过失败或假造结果。
- 测试**代码**留在对应应用目录；测试计划、验收步骤与报告放 `docs/testing/`，具体矩阵另由该目录规范维护。
- API 变化同步更新 `docs/api/` 与生成客户端；架构或部署变化同步更新对应文档。密钥放受控运行环境，不提交仓库；新增依赖须核对 GPL-3.0-only 兼容性。
- 提交使用清晰的 Conventional Commits（如 `feat(books): ...`、`fix(imports): ...`）；保留小范围、可审查的提交。**未经用户明确要求，不自动提交、推送、合并或部署。**
- 交付说明必须包含：修改内容、影响路径、执行的检查及结果、未验证的风险。未运行的检查不得声称通过。

## 7. AI 执行顺序

1. 阅读 `system.md`，并按任务读取 `sqlite.md`、`backend.md` 和相关专项文档。
2. 查找现有模块及测试，确认唯一实现归属；发现规范冲突先报告。
3. 仅改动必要代码；涉及数据结构先准备迁移，涉及接口同步更新 OpenAPI 契约。
4. 运行最小充分的真实测试与静态检查；失败先定位原因，不绕过检查。
5. 汇报改动、验证证据和剩余问题；不自行扩大任务范围。

## 参考项目（参考工程实践，不直接复制代码）

- [Ermao Library · 工程规范](https://github.com/GMD170629/ermao-library/blob/main/docs/engineering-standards.md)：模块边界、短事务、测试选择。
- [BookLore · CONTRIBUTING](https://github.com/booklore-app/booklore/blob/develop/CONTRIBUTING.md)：小范围提交、测试证据、迁移不可改写。
- [Tome · 扫描实现](https://github.com/bndct-devops/tome/blob/main/backend/services/scanner.py)：文件处理和数据库写入分离。
- [Komga · TaskProcessor](https://github.com/gotson/komga/blob/master/komga/src/main/kotlin/org/gotson/komga/application/tasks/TaskProcessor.kt)：后台任务生命周期。
