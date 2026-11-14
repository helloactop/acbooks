# 多端同步 API

所有路径相对于 `/api/v1`。读取需要 Web 会话或原生客户端 Bearer Token。Cookie 写入需来源校验及 CSRF；Bearer 写入沿用原生客户端认证。同步响应使用 `Cache-Control: no-store`。应用只读凭据不用于此 API。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/sync/snapshot` | 首次初始化及重新初始化 |
| GET | `/sync/changes` | 增量拉取和删除同步 |
| POST | `/sync/mutations` | 提交离线操作并取得逐条回执 |

## 初始化与增量拉取

两个 GET 接口接受 `after`（排除，默认 0）、`through`（包含，首次请求省略）和 `limit`（默认 50，最多 100）。响应为 `{items, nextCursor, through, hasMore}`。超出服务端序列范围或起止倒置返回 422。游标是服务器数据库内的序列号，恢复历史备份或切换服务端后须重新初始化。

1. 拉取第一批快照并记录返回的 `through`；后续快照页带上同一个 `through`，将 `nextCursor` 用作下一页的 `after`。
2. `hasMore=false` 后，从快照的 `through` 开始拉取增量。快照分页过程中再次变更的对象由后续增量补齐，快照与增量合起来完成初始化。先写入临时副本，完成追赶后再替换客户端镜像。
3. 增量翻页也固定当次 `through`，完成本轮后下一轮重新取得上界。服务端合并同一实体的历史变更，只返回读取事务内可见的最新版本。若最新版本高于本轮上界，则留给下一轮；客户端始终以 `nextCursor` 推进，空页面也需要保存游标。
4. 每批收到的变更与游标在同一个本地事务保存。按 `(entityType, entityId)` 处理，只应用比本地更新的版本。读取顺序是变更序列顺序；使用本地外键的客户端应先暂存变更，再按依赖关系投影。

每条记录为 `{entityType, entityId, revision, operation, data}`。UPSERT 的 `operation` 为 `upsert`，返回完整当前记录；删除为 `delete`，`data=null`，应清理本地记录及其派生缓存。书目、电子文件、阅读位置、会话、书签和划线/笔记都在同步范围内。书目包含作者、译者、标识符、标签、书架和实体副本等详情。电子文件记录包含所属书目、格式、大小、可用状态和 SHA-256；文件字节与封面沿用现有上传和下载 API。电子文件的同步数据不包含服务器存储路径。

阅读位置以 `bookFileId` 作为 `entityId`。同步记录外层的 `revision` 用于同步写入，位置数据内层的 `revision` 用于常规 Reader API。删除书目或解除电子文件关联时，也会同步相应的删除记录和保留对象的关联变化。

## 离线批量提交

请求体为 `{deviceId, mutations}`，每批 1–50 条，按提交顺序执行。客户端持久保存自己的 UUID 设备号，以及每条变更的 UUID `mutationId`。每条操作包含 `action`、`entityId`、`baseRevision`，有内容的操作使用类型化的 `data`。会话、书签和划线/笔记还需提供 `bookId`。本地新建对象使用客户端生成的 UUID 和 `baseRevision=0`，保留 UUID 供后续关联。创建电子文件使用已有的[上传接口](files.md)，上传完成后使用返回的文件 ID。

| 对象 | 操作 |
| --- | --- |
| 书目 | `book.create`、`book.update`、`book.delete` |
| 电子文件 | `book_file.move`（含 `sourceBookId`、`targetBookId`）、`book_file.delete`（解除关联并保留原文件） |
| 阅读位置 | `reading_position.save`（含 `locator` 与可选 `percent`）、`reading_position.delete` |
| 阅读会话 | `reading_session.create`、`reading_session.update`、`reading_session.delete` |
| 书签 | `bookmark.create`、`bookmark.update`、`bookmark.delete` |
| 划线及笔记 | `annotation.create`、`annotation.update`、`annotation.delete` |

各条操作独立提交，变更、日志与对应回执原子写入。一条业务失败不会撤销其他成功的操作。请求结构校验失败时整批返回 422；连接中断后可以用原请求重试。

- `accepted` 返回操作后的 `current`（删除时为墓碑）。同设备同变更 ID、相同内容的重试返回原回执。
- `conflict` 返回 `REVISION_CONFLICT` 和当时的服务器记录、墓碑或 `null`。客户端合并或选择版本后，使用新的变更 ID 与最新外层版本提交。已删除的书目、会话、书签与笔记的 UUID 不可重用。阅读位置在明确引用其删除版本后可以重新创建。
- `rejected` 返回业务错误码。同一设备变更 ID 被用于不同内容时，返回 `MUTATION_ID_REUSED`。历史 Reader API 已接受但没有存储完整回执的变更 ID 返回 `duplicate`。

需要用上一次操作的新版本继续修改同一实体时，先读取它的回执再构造下一条操作。记录的服务器权威值通过回执和增量同步取得。
