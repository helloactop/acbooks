# acbooks — Copies & Locations API

> 目标路径：`docs/api/copies.md`
> 上位规范：[API 约定](conventions.md)、[后端架构](../architecture/backend.md)、
> [SQLite](../architecture/sqlite.md)。
> 本文记录实体副本（BookCopy）与存放位置（Location）的契约，不定义新的表结构。

## 1. 范围与鉴权

提供实体副本增删改查与房间→书架→层等多级存放位置管理。`locations`（实体存放
位置）与虚拟分类 `shelves` 是不同模型，不得混用。

| 操作 | 鉴权 |
| --- | --- |
| 位置列表 / 副本列表 | 有效 Web 会话 Cookie（`WebSession`） |
| 位置与副本的创建 / 更新 / 删除 | 有效 Web 会话 Cookie + `X-CSRF-Token` + 可信 `Origin`/`Referer` |

未认证返回 `401 UNAUTHORIZED`；CSRF、来源或传输校验失败返回 `403 FORBIDDEN`。
写响应统一 `Cache-Control: no-store`；创建返回 `Location` 头。

## 2. 数据模型

- `Location`：`id`（UUID）、`parentId`（父位置 UUID，顶层为 `null`）、`name`
  （去除首尾空白，1–200 字符）、`copyCount`（**直接**存放在该位置的副本数）。
- `BookCopy`：`id`（UUID）、`bookId`、`locationId`（可为空）、`locationName`
  （关联位置名，未设置时为 `null`）、`acquiredAt`（购入时间，UTC 毫秒，可空）、
  `note`（备注，最多 2000 字符，可空）。
- 一本书可拥有多个实体副本；副本可没有存放位置。

## 3. 位置端点

### 3.1 位置列表

`GET /api/v1/locations` → `200 LocationNode[]`（扁平静态列表，含 `parentId`）。

### 3.2 创建位置

`POST /api/v1/locations` → `201 LocationNode`

```json
{ "name": "书房", "parentId": null }
```

- `name` 必填；`parentId` 可为空（顶层）或必须指向已存在的位置，否则
  `404 LOCATION_NOT_FOUND`。
- 成功设置 `Location: /api/v1/locations/{locationId}`。

### 3.3 更新位置

`PATCH /api/v1/locations/{locationId}` → `200 LocationNode`

- 未提交字段保持不变；`parentId` 传 `null` 移动到顶层。
- 空对象返回 `422 VALIDATION_ERROR`；`name` 不接受 `null`。
- 父位置不存在返回 `404 LOCATION_NOT_FOUND`；将位置设为自身或其后代作为父级会
  形成循环，返回 `422 LOCATION_CYCLE`。

### 3.4 删除位置

`DELETE /api/v1/locations/{locationId}` → `204`

- 位置仍有子位置时返回 `409 LOCATION_HAS_CHILDREN`，需先删除或移动子位置。
- 删除叶子位置时，引用该位置的副本 `locationId` 经外键 `SET NULL` 置空，
  副本与书目保留。
- 不存在返回 `404 LOCATION_NOT_FOUND`。

## 4. 副本端点

### 4.1 副本列表

`GET /api/v1/books/{bookId}/copies` → `200 BookCopy[]`
书目不存在返回 `404 BOOK_NOT_FOUND`。

### 4.2 创建副本

`POST /api/v1/books/{bookId}/copies` → `201 BookCopy`

```json
{ "locationId": "uuid", "acquiredAt": 1700000000000, "note": "精装" }
```

- `locationId`、`acquiredAt`、`note` 均可选；返回值包含 `locationName`。
- 书目不存在返回 `404 BOOK_NOT_FOUND`；`locationId` 不存在返回
  `404 LOCATION_NOT_FOUND`。
- 成功设置 `Location: /api/v1/books/{bookId}/copies/{copyId}`。

### 4.3 更新副本

`PATCH /api/v1/books/{bookId}/copies/{copyId}` → `200 BookCopy`

- 未提交字段保持不变；`locationId`、`acquiredAt`、`note` 传 `null` 清空。
- 空对象返回 `422 VALIDATION_ERROR`。
- 副本不存在或不属于该书目返回 `404 COPY_NOT_FOUND`；新位置不存在返回
  `404 LOCATION_NOT_FOUND`。

### 4.4 删除副本

`DELETE /api/v1/books/{bookId}/copies/{copyId}` → `204`
副本不存在或不属于该书目返回 `404 COPY_NOT_FOUND`。

## 5. 内联副本与位置筛选

- 创建书目可通过 `BookCreate.copies` 在同一事务内建立实体副本，语义见
  [Books API §3.1](books.md#31-创建书目)。
- 高级筛选 `location` 字段按位置子树匹配，见 [Books API §3.3](books.md#33-智能组合筛选)。

## 6. 错误响应

`application/problem+json`，`code` 为稳定业务码：

| 状态 | code | 场景 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 缺少或过期 Web 会话 |
| 403 | `FORBIDDEN` | CSRF、来源或传输校验失败 |
| 404 | `BOOK_NOT_FOUND` | 书目不存在 |
| 404 | `COPY_NOT_FOUND` | 副本不存在或不属于该书目 |
| 404 | `LOCATION_NOT_FOUND` | 位置不存在（含引用的父位置或副本位置） |
| 409 | `LOCATION_HAS_CHILDREN` | 删除仍有子位置的位置 |
| 422 | `LOCATION_CYCLE` | 父级设置会形成循环 |
| 422 | `VALIDATION_ERROR` | 输入、查询参数或空 PATCH 不合法 |
| 500 | `INTERNAL_ERROR` | 服务端异常；通过 `requestId` 关联日志 |

## 7. OpenAPI 与客户端类型

操作 ID：`listLocations`、`createLocation`、`updateLocation`、`deleteLocation`、
`listBookCopies`、`createBookCopy`、`updateBookCopy`、`deleteBookCopy`。
TypeScript 类型由 `openapi-typescript` 写入 `packages/acbooks-api-client/schema.d.ts`，
不得手改（见 [Books API §6](books.md#6-openapi-与客户端类型)）。
