# acbooks — Books Core V1 API

> 目标路径：`docs/api/books.md`
> 上位规范：[API 约定](conventions.md)、[后端架构](../architecture/backend.md)、
> [SQLite](../architecture/sqlite.md)、[安全](../architecture/security.md)。
> 本文记录 Books Core V1 的已实现契约；不定义新的表结构或业务模型。

## 1. 范围与鉴权

Books Core V1 提供书目创建、列表、详情和部分更新。所有端点位于
`/api/v1/books`，返回 `Cache-Control: no-store`。

| 操作 | 鉴权 |
| --- | --- |
| 列表 / 详情 | 有效 Web 会话 Cookie（`WebSession`） |
| 创建 / 更新 | 有效 Web 会话 Cookie + `X-CSRF-Token` + 可信 `Origin`/`Referer` |

- 未认证返回 `401 UNAUTHORIZED`；CSRF、来源或传输校验失败返回 `403 FORBIDDEN`。
- 书目删除不包含在 V1 中，`DELETE /api/v1/books/{bookId}` 返回
  `405 METHOD_NOT_ALLOWED`。

## 2. 数据与规范化

- `readingStatus` 取值为 `unread`、`reading`、`done`，默认 `unread`。
- 作者与译者分别写入 `book_contributors` 的 `author`、`translator` 角色；
  同一姓名复用同一条 `contributors` 记录。
- ISBN 写入 `book_identifiers(scheme="isbn")`。输入去掉空格和连字符、执行
  NFKC 规范化并转大写；格式须为 ISBN-10（末位可为 `X`）或 ISBN-13 数字。
  ISBN **不唯一**，相同 ISBN 的书目不会自动合并。
- 标签和书架按名称查找或创建；名称去除首尾空白，列表内按大小写不敏感去重。
  更新时使用提交的列表**整体替换**对应关联。名称查找、标签筛选和搜索使用
  Unicode casefold，与服务端写入保持同一匹配语义。
- 列表和详情只读返回副本、文件摘要；不返回 `cover_path`、存储根路径或内部
  文件路径。副本/文件本身由后续 Copies/Files 模块管理。
- 时间戳为 UTC Unix 毫秒整数。多作者排序按姓名排序后的第一个姓名。

## 3. 端点

### 3.1 创建书目

`POST /api/v1/books` → `201`

```json
{
  "title": "呐喊",
  "description": "短篇小说集",
  "authors": ["鲁迅"],
  "translators": [],
  "isbn": "978-7-02-000220-7",
  "readingStatus": "unread",
  "tags": ["文学"],
  "shelves": ["书房"]
}
```

- `title` 必填，1–500 字符。
- `description`、`authors`、`translators`、`isbn`、`tags`、`shelves` 可选；
  `authors`/`translators`/`tags`/`shelves` 各最多 20 项，名称最多 200 字符。
- 成功返回 `BookDetail`，并设置 `Location: /api/v1/books/{bookId}`。

### 3.2 列表查询

`GET /api/v1/books`

| 参数 | 说明 |
| --- | --- |
| `q` | 数据库级搜索：书名、作者/译者姓名、ISBN；最长 200 字符 |
| `readingStatus` | `unread` / `reading` / `done` |
| `tag` | 标签名称，大小写不敏感匹配 |
| `shelfId` | 书架 UUID |
| `mediaType` | `physical`（存在实体副本）或 `digital`（存在电子文件） |
| `format` | 电子文件格式，例如 `epub`、`pdf` |
| `filters` | 智能组合筛选规则 JSON，见 3.3 |
| `sort` | `title` / `author` / `createdAt` / `updatedAt` |
| `order` | `asc` / `desc` |
| `limit` | 1–100，默认 20 |
| `offset` | 0–1000000，默认 0 |

默认按 `createdAt` 降序，使用 `id` 升序作为稳定补充排序。返回：

```json
{
  "items": [
    {
      "id": "uuid",
      "title": "呐喊",
      "authors": ["鲁迅"],
      "translators": [],
      "isbn": "9787020002207",
      "readingStatus": "unread",
      "tags": ["文学"],
      "shelves": ["书房"],
      "copyCount": 0,
      "fileCount": 0,
      "formats": [],
      "createdAt": 1800000000000,
      "updatedAt": 1800000000000
    }
  ],
  "total": 1,
  "limit": 20,
  "offset": 0
}
```

搜索中的 `%`、`_` 和 `\` 按字面量处理，不作为 SQL 通配符。

### 3.3 智能组合筛选

`GET /api/v1/books?filters=<JSON>` 在数据库层执行全库组合筛选。规则由
`combinator` 与 `conditions` 组成，最多 30 条条件：

```json
{
  "combinator": "ALL",
  "conditions": [
    { "field": "tag", "operator": "equals", "value": "文学" },
    { "field": "copyCount", "operator": "greater_or_equal", "value": "1" }
  ]
}
```

- `combinator`：`ALL` 同时满足全部条件，`ANY` 满足任意一条。
- 字段定义固定为：`title`、`author`、`translator`、`tag`、`shelf`、
  `isbn`、`description`、`format`、`readingStatus`、`copyCount`、
  `fileCount`、`createdAt`、`updatedAt`、`hasCover`。
- 文本运算符：`contains`、`not_contains`、`equals`、`not_equals`、
  `starts_with`、`ends_with`、`is_empty`、`is_not_empty`。
- 选择运算符：`equals`、`not_equals`、`is_empty`、`is_not_empty`。
- 数值运算符：`equals`、`not_equals`、`greater_than`、`greater_or_equal`、
  `less_than`、`less_or_equal`、`between`、`is_empty`、`is_not_empty`。
- 日期运算符：`equals`、`not_equals`、`after`、`on_or_after`、`before`、
  `on_or_before`、`between`、`is_empty`、`is_not_empty`。日期使用
  `YYYY-MM-DD`，按 UTC 日界解释。
- 布尔运算符：`is_true`、`is_false`，用于 `hasCover`。
- `between` 的 `value` 为两个字符串组成的数组；无值运算符不提交 `value`。
- 名称、标题、标签、书架、格式匹配使用 Unicode casefold；否定关系条件表示
  书目不存在匹配行。`copyCount`/`fileCount` 基于关联表真实计数。
- 规则非法或字段/运算符不支持时返回 `422 INVALID_BOOK_FILTER`。

`GET /api/v1/books/filter-schema` 返回前端渲染条件所需的字段、类型、运算符、
静态选项和 `maxConditions`。动态选项通过：

`GET /api/v1/books/filter-options?source=authors&query=...&limit=20`

`source` 取 `authors`、`translators`、`tags`、`shelves`、`formats`，
返回带书库计数的选项；`query` 最长 100 字符，`limit` 为 1–50。
这些元数据端点仅用于读取，不表示新的数据表。

### 3.4 书目详情

`GET /api/v1/books/{bookId}` → `200 BookDetail`

`BookDetail` 在列表字段之外包含 `description`、`identifiers`、`copies`、`files`：

- `identifiers`: `{"scheme": "isbn", "value": "9787020002207"}`
- `copies`: `id`、`locationId`、`locationName`、`acquiredAt`、`note`
- `files`: `id`、`format`、`fileStatus`、`sizeBytes`

不存在时返回 `404 BOOK_NOT_FOUND`。

### 3.5 部分更新

`PATCH /api/v1/books/{bookId}` → `200 BookDetail`

- 未提交的字段保持不变。
- `description` 或 `isbn` 传 `null` 时清空；其他字段不接受 `null`。
- `authors`、`translators`、`tags`、`shelves` 使用数组整体替换，空数组清空。
- 空对象 `{}` 返回 `422 VALIDATION_ERROR`。
- 更新不会删除副本、文件、阅读会话、书签、划线或笔记。

## 4. 错误响应

所有错误使用 `application/problem+json`，`code` 为稳定业务码：

| 状态 | code | 场景 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 缺少或过期 Web 会话 |
| 403 | `FORBIDDEN` | CSRF、来源或传输校验失败 |
| 404 | `BOOK_NOT_FOUND` | 书目不存在 |
| 405 | `METHOD_NOT_ALLOWED` | 使用 V1 未提供的删除方法 |
| 422 | `VALIDATION_ERROR` | 输入、查询参数或空 PATCH 不合法 |
| 422 | `INVALID_BOOK_FILTER` | `filters` JSON、字段或运算符不合法 |
| 500 | `INTERNAL_ERROR` | 服务端异常；通过 `requestId` 关联日志 |

## 5. 删除策略

V1 不开放书目删除。后续实现删除时必须满足：

1. 先检查实体副本、电子文件、阅读会话、书签、划线和笔记等关联；
2. 存在关联时返回 `409`，不隐式级联删除；
3. 删除书目元数据不得删除 NAS 文件、阅读历史或阅读数据；
4. 文件缺失、扫描失败或 NAS 掉线不得触发书目删除。

## 6. OpenAPI 与客户端类型

实现由 FastAPI `/openapi.json` 发布，操作 ID：

- `listBooks`、`createBook`、`getBookFilterSchema`、`getBookFilterOptions`、
  `getBook`、`updateBook`

TypeScript 类型由生成工具写入 `packages/acbooks-api-client/schema.d.ts`，不得手改：

```sh
npx --yes openapi-typescript@7.10.1 http://127.0.0.1:8000/openapi.json \
  -o packages/acbooks-api-client/schema.d.ts
```
