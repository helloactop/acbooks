# 阅读数据 API

所有路径相对于 `/api/v1`。读取需要有效 Web 会话，写入还需要可信来源与会话绑定的 CSRF 令牌。

| 方法 | 路径 | 结果 |
| --- | --- | --- |
| GET | `/books/{bookId}/reading-sessions` | 会话列表，按开始时间倒序 |
| POST | `/books/{bookId}/reading-sessions` | 新增手动记录或自动会话 |
| PATCH / DELETE | `/books/{bookId}/reading-sessions/{itemId}` | 编辑时间和有效秒数，或删除会话 |
| GET | `/reading-positions/{bookFileId}` | 当前进度，尚未保存时返回 JSON `null` |
| PUT | `/reading-positions/{bookFileId}` | 校验版本并保存进度 |
| GET / POST | `/books/{bookId}/bookmarks` | 查询 / 创建书签 |
| PATCH / DELETE | `/books/{bookId}/bookmarks/{itemId}` | 编辑 / 删除书签 |
| GET / POST | `/books/{bookId}/annotations` | 查询 / 创建划线和笔记 |
| PATCH / DELETE | `/books/{bookId}/annotations/{itemId}` | 编辑 / 删除划线和笔记 |

列表接受 `limit`（默认 20，最多 100）和 `offset`，返回 `items`、`total`、`limit` 和 `offset`。书签及笔记按创建时间倒序，同一时间以 ID 排序。PATCH 成功返回更新后的资源，DELETE 成功返回 204。

- 创建及进度保存接受 UUID 格式的 `deviceId` 和 `mutationId`。首次成功返回 HTTP 200 和 `{status:"accepted",item}`。同一设备重试同一变更编号返回 `{status:"duplicate"}`。设备由首次成功提交登记。幂等回执、阅读数据及变更序列在同一事务写入，失败后可以用原编号重试。
- 手动会话需提供 `startedAt` 和 `endedAt`，可以省略电子文件。自动会话需指定 `bookFileId`，结束前 `endedAt` 可为空。时间使用 UTC Unix 毫秒，有效时长 `activeSeconds` 不得超过已结束会话的时间区间。会话创建后，所属书籍、文件、设备和来源保持固定。
- 阅读位置接受不透明的 JSON 对象 `locator`（最多 32 KiB）、可空 `percent`（0–100）及预期 `revision`。首次保存用版本 0，成功后版本加 1。版本不匹配返回 `409 REVISION_CONFLICT` 和 `current`，其中包含服务器当前记录或 `null`。
- 书签需提供非空 `label` 或 `locator`。划线及笔记分别使用 `quote` 和 `note`，至少一项有正文。书签、划线及笔记的 `bookFileId` 和 `locator` 可以为空。
- 指定的电子文件必须属于路径中的书籍。不存在的文件或跨书籍引用返回 `404 FILE_NOT_FOUND`。访问其他书籍的记录返回 `404 READING_RECORD_NOT_FOUND`。
