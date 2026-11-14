# 元数据 API

上传和 NAS 扫描共用 `MetadataService` 提取 EPUB OPF、PDF XMP/Info 和可用封面。
上传补全已有书目的空缺字段，扫描为新书初始化字段。已关联书目重扫时保持元数据。
同名、ISBN 或哈希匹配仅产生待处理候选。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/v1/metadata/candidates?q=…` | ISBN 精确检索或按书名查找，至多返回 10 个候选 |
| POST | `/api/v1/books/{book_id}/metadata` | 采用候选，返回实际修改的 `fields` |
| POST | `/api/v1/metadata/jobs` | 提交 `bookIds`（1–20 项），返回 202 和 `jobId` |
| GET | `/api/v1/metadata/settings` | 读取服务端自动补全设置 |
| PATCH | `/api/v1/metadata/settings` | 保存 `autoFill`（默认关闭）和 `fields` |

读接口须登录，写接口另需 CSRF。在线查询源暂为 Open Library。ISBN 走版本级
查询，按书名搜索返回作品级候选，因此不从同一作品的多个版本里任取 ISBN。
简介和封面缺失或下载失败时保留空白。Open Library 缺少部分书目的译者或系列，
这些字段只有来源确实提供时才可补全。源站失败返回 `502 METADATA_UNAVAILABLE`。

字段包括 `title`、`authors`、`translators`、`series`、`description`、
`isbn`、`cover`。字段开关适用于自动和批量在线补全。手动采用候选仍只写入
空缺且未手动锁定的字段。书名仅更新扫描生成的文件名占位。
确认前后台会重查当前 ISBN、字段开关和手动编辑标记。
任务结果通过 `GET /api/v1/jobs/{jobId}` 获取，逐书状态为
`filled`、`unchanged`、`review`、`missing` 或 `failed`。

启用自动补全后，上传或新书扫描入库在同一短事务内排入后台任务。
网络访问和封面发布在数据库写事务外执行。手动搜索或批量补全会向数据源发送
ISBN 或书名，自动入库补全只发送 ISBN。

## 参考实现

- calibre-douban v2.3.0 的 Calibre Source 先查 ISBN，未命中时回退书名，再解析网页与封面。直连测试中公开书籍详情返回 200，插件使用的 JSON 搜索入口返回 403，因此当前环境未验证插件完整链路。见 [插件源码](https://github.com/fugary/calibre-douban/blob/deea474/src/__init__.py)。
- PoxenStudio MyBooks 将候选搜索与批量填充拆开，搜索带缓存和并发控制，批量任务处理限速与暂停。见 [候选查询](https://github.com/PoxenStudio/mybooks/blob/fdf982a/webserver/handlers/book.py#L891) 和 [批量服务](https://github.com/PoxenStudio/mybooks/blob/fdf982a/webserver/services/autofill.py)。
- helloactop/mybooks-server 的候选点击后先填入编辑表单，用户可继续修改再保存。ISBN 查询也包含 Open Library 的版本与作品查询。见 [Web 选用逻辑](https://github.com/helloactop/mybooks-server/blob/de29af5/public/js/common.js#L896) 和 [数据源适配](https://github.com/helloactop/mybooks-server/blob/de29af5/src/http/metadata-sources.js)。
