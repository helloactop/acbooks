# acbooks Android 架构规范

> 路径：`docs/architecture/android-reader.md`<br>
> 工程：`apps/acbooks-android/`<br>
> 本文为开发规范，未实现的功能按阶段开发。

## 1. 产品定位

acbooks Android 是 acbooks 生态的独立、本地优先阅读器。

- 不登录即可选择文件导入、授权本地文件夹批量导入、管理和阅读电子书，使用书签、笔记、阅读进度、统计和备份功能。
- 登录已有 acbooks 账号后，通过服务端 HTTP API 浏览书库、下载文件、上传选定书籍和同步阅读数据。
- 首次启动直接进入首页（本地阅读概览）；服务器离线、登录失效或退出登录均不影响本地阅读。
- Android 负责阅读；Web 负责全局书库管理；Server 负责共享书库、认证及同步。
- Android 与 acbooks Server 仅通过 API 交互，不依赖服务端的部署环境或文件系统结构。不另建后端，不把 Web 管理端完整移植到 App。

## 2. 参考项目

| 项目 | 参考内容 |
| --- | --- |
| [mybooks-android](https://github.com/helloactop/mybooks-android) | 主要参考：账号登录、设备 Token、API 请求、增量同步、冲突处理、断点续传 |
| [Readest](https://github.com/readest/readest) | 阅读功能、阅读引擎隔离和多端体验 |
| [Readium Kotlin](https://github.com/readium/kotlin-toolkit) | EPUB 阅读引擎、阅读定位 |
| [KOReader](https://github.com/koreader/koreader) | 离线阅读和文件管理 |

服务器接入重点阅读 mybooks-android 的 `data/sync/`、`MyBooksConnectionStore.kt` 和 [同步协议](https://github.com/helloactop/mybooks-android/blob/78888d0bcd3e9201dd2021986334b3b19bd11b9e/devdocs/mybooks-sync-protocol.md)。借鉴实现方式，不照搬旧 API、旧数据库和服务器 ID 主键设计。

授权文件夹参考 mybooks-android 的 [`AuthorizedStorageManager.kt`](https://github.com/helloactop/mybooks-android/blob/78888d0bcd3e9201dd2021986334b3b19bd11b9e/app/src/main/java/com/huangder/lumibooks/util/AuthorizedStorageManager.kt)、`AuthorizedFolderSnapshotStore.kt`、`ui/settings/AuthorizedFoldersViewModel.kt` 和 [Android SAF 官方规范](https://developer.android.com/training/data-storage/shared/documents-files)。

## 3. 技术栈与架构

Kotlin + Jetpack Compose + Miuix；ViewModel + StateFlow；Hilt；Room/SQLite；DataStore；OkHttp/Retrofit；WorkManager；Readium。PDF 先验证渲染、文本选择和标注能力，再确定引擎。首期支持 EPUB/PDF，不自研排版引擎。

```text
Compose UI / ViewModel
        ↓
Domain / Repository
        ↓
Data ── Room + 本地文件 + ReaderEngine
        ↑
Sync ── API Client ── acbooks Server
```

Android 应用界面直接使用 Miuix 组件和默认主题。阅读正文由现有引擎渲染。界面范围见 [Android UI 设计规范](../ui/android-ui.md)。

UI 只观察本地数据。导入、阅读和编辑先在本地提交；网络任务独立执行，成功后更新本地数据。阅读器引擎通过统一接口打开文件、定位、获取进度，不依赖登录或同步模块。

## 4. 工程目录

沿用 acbooks Monorepo，Android 子工程独立构建 APK。

```text
acbooks/
├── apps/
│   ├── acbooks-server/
│   ├── acbooks-web/
│   ├── acbooks-desktop/
│   └── acbooks-android/
│       ├── app/              # Application、Activity、导航、DI
│       ├── core/
│       │   ├── model/        # 业务模型、UUID、Locator
│       │   ├── domain/       # Repository 接口、业务规则
│       │   ├── database/     # Room、DAO、迁移
│       │   ├── storage/      # SAF、目录扫描、导入、文件校验
│       │   ├── datastore/    # 用户偏好
│       │   ├── reader/       # EPUB/PDF 引擎适配
│       │   ├── network/      # Auth、API、DTO、凭据、HTTP
│       │   ├── data/         # Repository 实现
│       │   ├── sync/         # 同步队列、合并、Worker、传输
│       │   └── designsystem/ # 主题、通用组件
│       ├── feature/
│       │   ├── home/         # 首页、继续阅读、最近阅读、阅读目标
│       │   ├── library/      # 本地书库、文件夹浏览、批量导入、搜索
│       │   ├── bookdetail/   # 书籍详情、文件关联
│       │   ├── reader/       # 阅读页、目录、阅读设置
│       │   ├── annotations/  # 笔记、划线、书签
│       │   ├── statistics/   # 统计、日历、时间线
│       │   ├── acbooks/      # 登录、远程书库、同步状态
│       │   └── settings/     # 偏好、授权文件夹、备份、存储
│       ├── gradle/           # 版本目录和 wrapper
│       ├── settings.gradle.kts
│       └── build.gradle.kts
└── docs/architecture/android-reader.md
```

目录表示目标边界，按阶段建立实际需要的模块，不创建大量空壳。每个模块使用标准 `src/main`、`src/test` 等目录。feature 内使用 Screen、ViewModel、UiState、components；UI 不直接调用 DAO 或 HTTP，不允许循环依赖。

## 5. 本地存储

```text
filesDir/
├── library/
│   ├── objects/<sha256>.<ext>  # App 管理的电子书副本
│   ├── covers/                # 封面
│   └── fonts/                 # 用户字体
└── transfers/                 # .part 文件与续传元数据
cacheDir/
└── reader/                    # 可重建的渲染资源和临时正文
Room: acbooks-reader.db         # Android databases 目录
```

### 5.1 文件来源

| 来源 | 存储与读取方式 |
| --- | --- |
| 选择文件导入（支持多选） | SAF 选择文件，默认复制到 `library/objects/` |
| 授权本地文件夹 | 默认引用原文件 `content://` URI，批量建立书目，不复制正文；支持主动复制到本地库 |
| acbooks 下载 | 校验完成后保存至 `library/objects/` |

统一 FileStorage 接口按来源打开正文；阅读引擎需要可寻址文件而 URI 不支持时，使用校验过的临时副本。流式校验格式、大小及 SHA-256；受管理文件原子发布后写数据库。相同正文可复用内容对象，不自动合并不同书目。清缓存不删除正式副本。外部原件默认只读。

已实现的手动导入通过 Android 系统文件选择器读取 EPUB/PDF，逐本提交。重复选择同一来源和相同内容时跳过；不同来源的相同内容先提示，确认后才另建书目并共用同一份正文。EPUB 优先使用内嵌书名、作者和可解码的内嵌封面；PDF 使用文件名和占位封面。正文及封面按内容哈希存储，Room 的虚拟书架只记录书目关系。导入策略参考 [Readest ingestService](https://github.com/readest/readest/blob/main/apps/readest-app/src/services/ingestService.ts)。

### 5.2 授权文件夹与批量导入

- 书库提供“导入文件”“授权文件夹”入口；设置可管理多个已授权目录。用 SAF `ACTION_OPEN_DOCUMENT_TREE` 选择目录，持久化实际授予的读取权限（`takePersistableUriPermission`），不申请全盘文件权限。
- 用 `DocumentsContract` / `ContentResolver` 递归扫描子目录，展示 EPUB/PDF 文件及扫描结果；支持全选、多选批量导入。可浏览原始目录层级；虚拟书架与物理目录分离。
- 扫描和元数据解析在后台分批执行，可取消、显示进度；记录来源 URI、文档 ID、目录、文件大小、修改时间与哈希。重复扫描同一来源不重复创建书目；重叠授权目录按 provider/document ID 去重，哈希相同但不同来源只提示关联候选。
- 首次扫描后支持手动刷新；再次打开目录可刷新，新增文件显示待导入，用户可开启自动导入新增文件。不得依赖 `FileObserver` 监听 SAF URI。扫描失败、权限失效或存储离线不得视为文件被删除。
- 已成功扫描且确认原文件缺失时标记 `missing`；授权失效标记 `permission_required`。保留书目、进度、笔记与历史，允许重新授权、重新关联文件；内容哈希变化按新文件版本处理，不覆盖旧定位。
- 从书库移除引用或撤销目录授权不删除原文件；移除导入项后记录忽略状态，避免扫描时立即重新导入，允许手动恢复。复制到本地库的副本不受授权撤销影响。
- Android 11+ 的部分目录（如存储卷根目录、Download 根目录、Android/data、Android/obb）不可通过目录授权访问；遵循系统限制，提示选择可授权子目录或使用单文件导入。

备份包含本地数据和受管理正文；外部引用正文可由用户选择纳入备份。还原后重新检查授权，不假设旧 URI 权限仍有效；排除登录凭据。

## 6. 本地数据模型

| 表 | 用途 |
| --- | --- |
| `books` | 本地书目 |
| `file_objects`、`book_files` | 正文内容身份、哈希及书目关联 |
| `file_sources` | 正文来源（managed / external / downloaded），应用私有相对路径或 SAF URI、授权目录 ID、状态及更新时间 |
| `authorized_folders`、`folder_entries` | 持久授权目录、扫描索引、文档身份、导入/忽略状态 |
| `reading_positions` | 每个文件的进度及 Locator |
| `reading_sessions` | 每次阅读的起止时间、有效秒数、设备 |
| `bookmarks`、`annotations` | 书签、划线、笔记 |
| `shelves`、`shelf_books` | 本地书架 |
| `server_profiles` | 服务器身份、账号及设备 ID，不含明文凭据 |
| `remote_catalog`、`remote_catalog_files` | 远程目录缓存 |
| `remote_book_links`、`remote_file_links` | 本地与服务器书籍及文件 ID 映射 |
| `sync_outbox`、`sync_state`、`sync_baselines`、`sync_conflicts` | 待同步操作、游标、版本基线及冲突 |
| `transfers` | 传输状态 |

本地 UUID 永久不变；上传后仅新增远程映射，不替换本地主键。远程映射按服务器实例和账号隔离。一本书允许多个文件，进度和标注绑定具体文件版本；不同正文不得盲目套用旧 Locator。阅读位置用版本化 Locator JSON，百分比只用于展示。Room 必须提供迁移，不允许破坏性清库。

目前 EPUB 使用 Readium Navigator，PDF 使用 Android `PdfRenderer` 逐页显示。应用通过 Room 按 `bookFileId` 保存 Readium Locator 或 PDF 页索引。阅读器从应用私有书库读取文件，无需登录。

Room v4 增加本地阅读会话、书签和划线笔记。时间使用 UTC Unix 毫秒，有效阅读时长使用秒，进度百分比使用 0–100。`revision` 随本地位置实际变化递增，后续同步需维护独立的服务端版本基线。旧 Locator 原样保留，未知百分比为 `NULL`，旧版本与未知更新时间使用 0。会话 DAO 接受自动和手动记录，现有读取路径会保存进度并从已完成会话汇总本地统计。

Room v5 扩展书目元数据和首选文件，新增贡献者、外部标识、标签和实体副本关系。迁移时每本书的旧作者字符串作为一条作者记录保留，旧字段仍可读取。同名、同 ISBN 或同内容的书目保持各自 UUID。文件解除关联时保留书目级阅读记录，书籍显式删除时清理其从属关系。

Room v6 按 SHA-256 与格式关联多个来源，保留 `book_files.sourceUri` 用于识别既有导入。迁移保留原始 URI，并为每个文件对象记录规范的私有副本路径。两类迁移来源初始状态为 `unknown`，探测后更新为 `present`、`missing` 或 `permission_required`。删除单个本地副本只更新该副本状态，书目、阅读记录与原始 SAF 文件均保留。

授权目录按服务提供方和根文档 ID 唯一。目录条目以目录 ID 和文档 ID 识别，刷新时保留导入关联和忽略状态。只有完整扫描才能标记缺失。重新关联需要已验证的 SHA-256、格式及扫描标记，并沿用原文件 ID。

Room v7 增加单一服务器连接、远程书目与文件缓存。旧云端关联迁移到未验证的记录，等待独立核验。每个远程书目对应一个可在书库显示的本地条目。正文在打开时下载，重新下载同一文件版本会恢复原有文件 ID 和阅读位置。每个书目可以单独移除离线副本，同哈希的物理文件在其他书仍使用时继续保留。

## 7. 账号和 API

参考 mybooks-android 的 AuthClient、Endpoint、ConnectionStore 和 ApiClient：

1. 设置中输入 acbooks 服务器地址，检查服务身份、初始化及协议能力；无需配置服务端存储路径或其他存储服务账号。
2. 用已有 acbooks 账号密码登录，获取独立设备 Token；用 Android Keystore 加密保存，不保存密码。
3. 后续 API 使用 `Authorization: Bearer <token>`。限制凭据只发送给绑定服务器，禁止登录重定向和跨域泄露；默认 HTTPS，局域网 HTTP 需明确确认风险。
4. 退出登录撤销设备会话、停止同步并清除凭据，保留本地数据、关联及未同步操作。

现有 acbooks `/api/v1` 已有 books、files、uploads、reading-sessions、reading-positions、bookmarks、annotations、stats。以 `docs/api/` 和实际 OpenAPI 为准，不复用 my-books 的旧路径。

Android 设置已通过 [设备认证接口](../api/android-client.md) 接入原生设备登录、会话验证和撤销。连接使用 Android 系统信任的 HTTPS，设备 Bearer 会话使用 Android Keystore 加密保存，密码仅用于登录请求。Web Cookie/CSRF 规则独立保留。

账号头像通过 `/api/v1/auth/me/avatar` 与 Web 共用同一份服务器资料。设置页点击 56dp 头像从系统照片选择器取图，按 512×512 居中裁剪并压缩；未登录时先保存到应用私有目录并标记待同步，登录后自动上传；已登录时直接上传。服务器已有头像且本地没有待同步修改时，客户端按 `avatarUpdatedAt` 下载覆盖本地缓存；应用启动、登录状态变化和每次进入设置页都会刷新，首页 40dp 玻璃头像按钮与设置卡片读取同一缓存。昵称仍保存在本地 DataStore，编辑弹窗后续接入，不再使用三级页面。

Android 用服务器地址和 `/auth/me` 返回的单管理员 UUID 确认书库身份。退出保留绑定、离线书籍和阅读数据，重新登录同一服务器继续使用原设备 ID。Keystore 加密设备 Token 后单独保存。

## 8. 同步与文件传输

- 连接成功后、应用回到前台及本地导入结束时同步书库，书库菜单也提供「立即同步」。现有本地书目使用原 UUID 幂等建书，按书上传正文并确认 SHA-256。授权目录和 SAF URI 留在本机。
- 每次同步读取分页快照并追赶本轮变更，随后在一个事务更新目录。云端书目显示封面及基本信息，封面右下角的云图标表示正文尚未保存在本机。点开书籍会下载正文，校验大小和 SHA-256 后才发布离线副本。
- 「删除本地」对已备份的书籍移除离线副本，保留云端条目、关联与本机阅读数据。未备份的本地书籍按本地删除提示处理。「全部删除」由已验证服务器确认后清理云端目录缓存和本地记录。SAF 原始文件保持只读。
- 阅读记录继续保存在本机。跨设备合并、持久增量游标、后台重试及断点续传由后续阶段接入。

## 9. 阅读与统计

进度、书签、笔记及阅读会话先保存本地。进入阅读开始会话，退出或后台时保存最终位置和有效时长，应用级任务确保落库；异常退出可恢复，排除后台/空闲时间。

统计基于 `reading_sessions` 实时计算，支持累计时长、次数、间隔、趋势、日历热力图、可编辑时间线。对齐 `docs/api/reading-stats.md` 的时间范围、时区、重叠区间及间隔定义；本地统计不依赖服务器。同步会话按稳定 ID 去重，不重复累计。

## 10. 开发顺序与验收

| 阶段 | 实现 | 验收 |
| --- | --- | --- |
| 1 | Android 工程、主题、Room、文件导入、授权目录扫描与批量导入、本地书库 | 无服务器可导入；重复扫描不重复建书；撤销授权保留数据；原文件不被修改 |
| 2 | EPUB/PDF 阅读、进度、笔记、书签、会话 | 飞行模式打开本地可用正文（管理副本或授权引用），重启恢复进度 |
| 3 | 统计、时间线、备份与恢复 | 统计与会话一致，恢复不丢阅读数据 |
| 4 | 后端原生认证 + Android 连接、远程目录、下载 | 账号登录、断点续传，退出仍可阅读 |
| 5 | 上传关联、增量同步、冲突处理 | 双设备离线编辑后同步，无重复或数据丢失 |

AI 开发规则：先读取现有 `AGENTS.md`、系统规范和 API 文档；按阶段开发，不重构无关模块；新增依赖更新开源依赖说明；每阶段提交测试、迁移和文档。不能实现的能力明确标记，不用模拟成功替代真实功能。
