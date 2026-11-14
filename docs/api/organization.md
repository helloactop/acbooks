# 书库整理 API

所有路径相对于 `/api/v1/organization`。读取需要有效会话，写入及合并预览使用现有写权限和 Cookie CSRF 校验。列表支持 `limit`（1–100，默认 20）和 `offset`，返回 `items`、`total`。

| 方法 | 路径 | 请求与结果 |
| --- | --- | --- |
| GET | `/{kind}` | 分页列出条目 ID、名称和关联书籍数 |
| GET | `/{kind}/{entry_id}/books` | 按条目 ID 分页查询关联书目 |
| PATCH | `/{kind}/{entry_id}` | `{name}`，成功返回 204 |
| POST | `/{kind}/{entry_id}/merge` | `{targetId}`，保留目标条目，成功返回 204 |
| DELETE | `/{kind}/{entry_id}` | 移除该条目及对应关系，成功返回 204 |
| GET | `/duplicates` | 返回去重后的候选书目对及匹配依据 |
| PATCH | `/files/{file_id}/book` | `{sourceBookId,targetBookId}`，成功返回 204 |
| POST | `/books/merge-preview` | `{sourceBookId,targetBookId}`，返回冲突值、迁移数量与 `revision` |
| POST | `/books/merge` | 预览入参及 `revision`、`useSource`，成功返回 204 |

`kind` 为 `tag`、`shelf`、`author`、`translator`。作者和译者共享姓名表。同一条姓名被多种角色使用时，单角色重命名会拆出一个新条目 ID，其他角色保留原名。合并和删除只处理所选角色。重名或合并到自身返回 `409 ORGANIZATION_CONFLICT`。

候选依据包括按 Unicode 大小写折叠后相同的书名、ISBN、同命名空间外部 ID，以及已入库的 SHA-256。共享作者作为附加依据。候选查看不会修改书目。

合并默认采用保留书目的元数据。`useSource` 可为指定字段选择来源书目的值，支持书名、简介、系列、阅读状态、封面、作者、译者和 ISBN。标签、书架及非 ISBN 标识取并集。预览版本不符时返回 `409 ORGANIZATION_STALE`，客户端需重新预览。文件重新关联使用预期来源校验，文件已在目标时可安全重试。操作在独立 SQLite 写事务中完成。文件 ID、存储对象、阅读位置及其版本保留。来源书目的文件、实体副本、阅读会话、书签和笔记迁往保留书目后才移除来源书目。合并操作只写数据库，舍弃的封面文件暂留应用的封面目录。

NAS 扫描会将同一存储根、同一目录、同名但扩展名分别为 EPUB 和 PDF 的文件列为待关联候选，即使两份文件的内嵌书名不同。用户可选择关联到现有书目或另建书目。

## 实现参考

- [Calibre 图形界面](https://manual.calibre-ebook.com/gui.html) 的多格式书目和集中分类管理。
- [Find Duplicates](https://github.com/kiwidude68/calibre_plugins/blob/main/find_duplicates/CHANGELOG.md) 的书名、作者、标识和文件内容匹配。
- [BookLore](https://github.com/booklore-app/booklore) 的书架组织方式。
- [ermao-library 工程规范](https://github.com/GMD170629/ermao-library/blob/main/docs/engineering-standards.md) 的业务分层与事务约定。
