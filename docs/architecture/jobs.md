# acbooks — 后台任务设计规范

> **目标路径**：`docs/architecture/jobs.md`  
> **用途**：指导 AI 实现 NAS 扫描、导入、元数据处理、封面生成和文件操作的后台执行。  
> **上位规范**：`docs/architecture/system.md`、`sqlite.md`、`backend.md`、`storage.md`、`docs/api/conventions.md`、`docs/development/standards.md`。本文只规定**任务执行机制**，不另建业务模型、数据表或 API。发现冲突先修改对应权威规范。

## 1. 总体规则

- 使用 **FastAPI + 独立 Worker 进程 + SQLite `background_jobs`**，属于同一模块化单体、同一 NAS/Linux Docker Compose 部署；首版**不引入 Redis、Celery、微服务或多 Worker 集群**。
- API、文件监听器、定时调度器只负责**持久化入队**；耗时工作由 Worker 处理。**业务行为仍由所属模块的 Service 执行**，Worker 不复制扫描、导入、文件移动或元数据逻辑。
- 首版仅允许**一个活动 Worker 实例**；可对哈希和文件解析使用有限的计算并发，SQLite 写入保持短事务、受控提交。任务处理按**至少一次执行**设计：允许重试，副作用必须幂等，不承诺“恰好执行一次”。
- 既有 `background_jobs(id, type, status, payload_json, created_at, updated_at)` 是唯一任务持久化来源；任务内部细节可放入受校验的 `payload_json`，**不默认增加新表/列**。如确需独立租约、进度、错误字段，须先更新 `sqlite.md` 并增加 Alembic 迁移。

## 2. 任务类型与状态

| `type`（建议标识） | 业务执行者 | 任务内容 |
| --- | --- | --- |
| `library_scan` | `modules/imports` | 增量/全量扫描、索引与缺失核对 |
| `file_import` | `modules/imports` + `modules/files` | 上传后导入、候选关联、索引 |
| `metadata_extract` | `modules/metadata` | 格式解析、元数据/封面提取 |
| `cover_generate` | `modules/metadata` | 派生缩略图/封面缓存 |
| `file_operation` | `modules/files` | **经明确授权**的移动、整理等文件操作 |

**状态只采用现有 API 约定**：`queued → running → succeeded`；可从 `running → queued`（可重试）或 `running → failed`（终止/耗尽重试）。仅成功完成全部约定步骤才能标记 `succeeded`；有部分结果但整体失败时记为 `failed` 并保留安全的错误和已完成摘要。取消能力若要暴露新状态或端点，必须先同步修改 `api/conventions.md`。

```text
POST 扫描/导入等 → 短事务写 background_jobs(status=queued) → 202 + jobId
                                        ↓
                              Worker 领取 → running
                                        ↓
                        调用所属 Service（短事务 + 有界 I/O）
                             ↙                     ↘
                       succeeded            queued（延迟重试）/ failed
                                        ↓
                              GET /api/v1/jobs/{jobId}
```

## 3. 入队、领取与调度

1. **入队**：验证身份、目标根目录、任务类型和参数后，在短事务中创建 UUID 任务；`payload_json` 只存已校验的 JSON 参数与必要运行信息，**不得存密码、令牌、任意 Python 函数或绝对路径执行命令**。
2. **去重**：同一 `root_id` 同时最多一个有效扫描；重叠扫描范围合并或排队，运行中收到的新变化必须安排后续核对。事件监听只触发扫描，不能直接删除/合并书籍。
3. **领取**：Worker 从 `queued` 按 `created_at, id` 稳定顺序取任务，使用 SQLAlchemy 条件更新 `WHERE id = ? AND status = 'queued'`，确认恰好更新一行后才执行；不要用“先查询后无条件更新”。
4. **单实例**：启动时持有部署本机文件系统上的排他进程锁（例如 `/data/acbooks/database/.worker.lock`）；加锁失败则拒绝启动第二个 Worker。锁文件不得在运行中清除；不要以进程内布尔变量充当跨进程锁。
5. **调度**：周期扫描和维护由 Worker 侧轻量调度器调用**同一个入队服务**；防止重启后同一时间窗口重复入队。监听不可靠的 NAS 挂载仍需定期核对，不能只依赖 watchdog 事件。
6. **任务数据**：在现有 `payload_json` 中为内部执行信息保留明确的版本化结构（如 `input`、`runtime.attempt`、`runtime.not_before_ms`、`runtime.progress`、`runtime.checkpoint`、`runtime.error_code`）；读写必须通过统一的 Pydantic 模型校验，不将其当作对外 API 契约。

## 4. 重试、崩溃恢复与数据安全

- **重试**：仅对暂时性故障（挂载暂不可达、超时、SQLite 锁忙等）进行有限次数、带退避的重试；无效参数、越权、格式不支持、目标冲突等直接 `failed`。尝试次数、下次执行时间和最终失败原因持久化；不无限重试或吞异常。
- **崩溃恢复**：获取单实例锁后检查遗留 `running` 任务；先根据文件/索引/检查点核对已完成效果，再安全地重排或标记失败。**严禁不核对就重复移动、覆盖或创建书籍**。Worker 停止时完成当前安全检查点并释放资源；意外终止由下次启动恢复。
- **幂等**：使用 `jobId`、稳定 `Book/BookFile/StorageObject` ID、文件身份和唯一路径约束验证结果；重复扫描/导入不能创建重复书籍或覆盖现有用户数据。
- **文件步骤**：遵循 `storage.md` 的 `staging → validated → published → indexed`；文件 I/O、哈希和外部网络调用都在数据库写事务外，**SQLite 与文件系统不存在跨系统原子提交**；未完成发布必须能够重试/清理，不可删除未确认的原件。
- **扫描缺失**：只有根目录可达、权限正常、扫描范围完整成功时才标记文件 `missing`；任务失败、挂载断开、取消或异常空目录均不得批量标缺失，更不能删除 `Book`、实体副本或阅读历史。
- **进度和心跳**：长任务按有界批次保存进度、检查点及 `updated_at`；API 可报告当前状态与安全的错误摘要，日志记录 `jobId`、任务类型、耗时和异常原因。UI 若发现长时间无更新可提示“可能中断”，不得凭超时自行宣称成功。
- **资源上限**：设置扫描根/文件大小、同时解析数、队列批次、网络超时和内存上限；优先维护单 Worker 的可靠性，不在 SQLite 上开启无界并行写事务。

## 5. 代码落点与开发约束

```text
apps/acbooks-server/app/
├── workers/
│   ├── main.py       # Worker 启停、排他锁、异常隔离、恢复
│   ├── queue.py      # background_jobs 入队/原子领取/状态写回
│   ├── registry.py   # 白名单 type → 既有业务 Service
│   └── scheduler.py  # 定时触发；统一入队与去重
├── modules/
│   ├── imports/      # 扫描/导入的真实业务实现
│   ├── files/        # 文件操作的真实业务实现
│   └── metadata/     # 解析/封面的真实业务实现
└── db/models/        # 复用 sqlite.md 规定的 background_jobs ORM
```

- `Router → Service → Repository`、`Worker → 同一 Service → Repository/文件适配器`。领取/状态更新统一归 `workers/queue.py`；业务服务不自行复制队列实现。`queue.py` 通过 SQLAlchemy 2.x 类型化查询与明确事务访问既有模型。
- 任务处理器按 `type` 通过显式注册表调用，未知类型安全失败并记录；外部用户不能提交任意任务类型或未经授权的文件动作。
- 仅 `GET /api/v1/jobs/{jobId}` 查询任务状态；扫描接口沿用 `POST /api/v1/storage-roots/{rootId}/scan` 返回 `202 + jobId`。其他接口及错误形状严格遵循 `docs/api/conventions.md`，不得擅自新增同义端点。

## 6. 必须测试与验收

- `pytest` 测试代码放 `apps/acbooks-server/tests/`；测试设计/报告放 `docs/testing/`。覆盖：同根扫描去重、原子领取、第二 Worker 启动被拒、任务状态顺序、有限重试与最终失败。
- 模拟任务执行中进程被杀、发布前/后中断、SQLite 暂时锁忙、NAS 掉线/恢复、文件并发改动；确认恢复后**不会重复创建 Book/File、错误标缺失、覆盖或删除原件**。
- 验证部分成功的错误报告、超时/资源限制、重复调度、监听突发事件、文件路径越界、`external` 只读以及 API/Worker 权限一致性。
- 交付时说明实际执行的测试及失败；未验证的恢复场景必须明确列出，不得声称完成。**未经用户要求不得提交、推送、合并或部署。**

## 7. 开源研究依据（借鉴机制，不复制代码）

| 项目 | 对照源码 | 借鉴重点 |
| --- | --- | --- |
| **Komga** | [TaskProcessor](https://github.com/gotson/komga/blob/master/komga/src/main/kotlin/org/gotson/komga/application/tasks/TaskProcessor.kt) · [TasksRepository](https://github.com/gotson/komga/blob/master/komga/src/main/kotlin/org/gotson/komga/application/tasks/TasksRepository.kt) | 任务入队/处理解耦、启动清理旧归属、受控并发 |
| **Ermao Library** | [Worker](https://github.com/GMD170629/ermao-library/blob/main/apps/api-python/app/worker/main.py) · [QueueHeartbeatPump](https://github.com/GMD170629/ermao-library/blob/main/apps/api-python/app/modules/system/infrastructure/queue_runtime.py) | Worker 独立生命周期、心跳、停止与失败隔离 |
| **BookLore** | [TaskService](https://github.com/booklore-app/booklore/blob/develop/booklore-api/src/main/java/org/booklore/service/task/TaskService.java) · [TaskHistoryService](https://github.com/booklore-app/booklore/blob/develop/booklore-api/src/main/java/org/booklore/service/task/TaskHistoryService.java) | 任务历史、调度配置、进度和取消入口 |
| **Kavita** | [TaskScheduler](https://github.com/Kareadita/Kavita/blob/develop/Kavita.Services/TaskScheduler.cs) · [ScanJobQueue](https://github.com/Kareadita/Kavita/blob/develop/Kavita.Services/Scanner/ScanJobQueue.cs) | 扫描排队去重、延迟调度、失败重试 |

**核心原则：任务只执行已有业务，重试前先核对副作用；先让单 Worker 可靠恢复，再考虑多 Worker 和复杂队列。**
