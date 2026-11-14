# 阅读统计 API

以下路径相对于 `/api/v1`，需要有效 Web 会话，响应使用 `Cache-Control: no-store`。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/stats/overview` | 全库汇总 |
| GET | `/books/{bookId}/stats` | 单书汇总 |
| GET | `/stats/timeline` | 期间内相交的会话，支持可选 `bookId` |

三个接口都要求 `startDate`（含）、`endDate`（不含）与 IANA `timeZone`。范围为 1–366 个本地日，起止年份为 1970–9998。例如 `?startDate=2026-11-01&endDate=2026-12-01&timeZone=Asia%2FShanghai`。范围或时区无效返回 422，指定书籍不存在返回 404。

汇总返回日期范围、`activeSeconds`、`sessionCount`、`averageGapSeconds`、每日 `days`、按当地 0–23 点排列的 `hourlySeconds` 和分类 `typeSeconds`。无数据的日期保留零值，无可用间隔返回 `null`。年度热力图请求所选自然年的范围。

- 事实来源为已结束且有效秒数大于零的 `reading_sessions`。每条会话计一次，归入开始日。窗口开始前已开始的会话可以贡献时长并进入时间线。
- 有效时长按会话区间均匀分摊，按当地日与小时边界裁剪。全局和单书分别去重，重叠区段按最大活跃比例计时，比例相同优先较早结束，再按 ID 排序。分类采用该区段胜出的会话。全局时长可小于各单书时长之和。
- 分类按当前关联确定。有电子文件为 `electronic`，无文件且为手录为 `manual`，其余为 `unknown`。时长分类之和为该范围总时长。日和小时的拆分是估算，夏令时回拨合并同名当地小时。
- 间隔按同书计算，用本次开始减去此前已开始的有效会话的最晚结束时间。只聚合正间隔，归于本次开始日，包含周期开始前的上一段会话作为参考。总平均按间隔数量加权。
- 时间线使用 `limit`（默认 20，最多 100）和 `offset`，按开始时间倒序、ID 升序。返回 `items`、`total`、`limit` 和 `offset`。同时返回当前 `bookTitle` 与可空的 `fileFormat`。行中的起止时间和有效秒数为会话原值。会话管理使用[阅读数据 API](reader.md)。

汇总直接读取当前会话及其书籍归属。编辑、删除、文件重新关联和书目合并后，重新查询即可取得当前结果。
