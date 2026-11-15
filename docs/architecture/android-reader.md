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
| [Lumi Books](https://github.com/huangder/Lumi_Books) | Android 工程、本地书库、授权文件夹、阅读界面和交互 |
| [Readest](https://github.com/readest/readest) | 阅读功能、阅读引擎隔离和多端体验 |
| [Readium Kotlin](https://github.com/readium/kotlin-toolkit) | EPUB 阅读引擎、阅读定位 |
| [KOReader](https://github.com/koreader/koreader) | 离线阅读和文件管理 |

服务器接入重点阅读 mybooks-android 的 `data/sync/`、`MyBooksConnectionStore.kt` 和 [同步协议](https://github.com/helloactop/mybooks-android/blob/78888d0bcd3e9201dd2021986334b3b19bd11b9e/devdocs/mybooks-sync-protocol.md)。借鉴实现方式，不照搬旧 API、旧数据库和服务器 ID 主键设计。

授权文件夹参考 mybooks-android 的 [`AuthorizedStorageManager.kt`](https://github.com/helloactop/mybooks-android/blob/78888d0bcd3e9201dd2021986334b3b19bd11b9e/app/src/main/java/com/huangder/lumibooks/util/AuthorizedStorageManager.kt)、`AuthorizedFolderSnapshotStore.kt`、`ui/settings/AuthorizedFoldersViewModel.kt` 和 [Android SAF 官方规范](https://developer.android.com/training/data-storage/shared/documents-files)。

## 3. 技术栈与架构

Kotlin + Jetpack Compose + Material 3；ViewModel + StateFlow；Hilt；Room/SQLite；DataStore；OkHttp/Retrofit；WorkManager；Readium。PDF 先验证渲染、文本选择和标注能力，再确定引擎。首期支持 EPUB/PDF，不自研排版引擎。

```text
Compose UI / ViewModel
        ↓
Domain / Repository
        ↓
Data ── Room + 本地文件 + ReaderEngine
        ↑
Sync ── API Client ── acbooks Server
```

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
| `file_sources` | 正文来源（managed / external），受管理相对路径或 SAF URI、授权目录 ID、可用状态；一个内容对象可有多个来源 |
| `authorized_folders`、`folder_entries` | 持久授权目录、扫描索引、文档身份、导入/忽略状态 |
| `reading_positions` | 每个文件的进度及 Locator |
| `reading_sessions` | 每次阅读的起止时间、有效秒数、设备 |
| `bookmarks`、`annotations` | 书签、划线、笔记 |
| `shelves`、`shelf_books` | 本地书架 |
| `server_profiles` | 服务器身份、账号及设备 ID，不含明文凭据 |
| `remote_catalog`、`remote_catalog_files` | 远程目录缓存 |
| `remote_book_links`、`remote_file_links`、`remote_record_links` | 本地与服务器 ID 映射 |
| `sync_outbox`、`sync_state`、`sync_baselines`、`sync_conflicts` | 待同步操作、游标、版本基线及冲突 |
| `transfers` | 传输状态 |

本地 UUID 永久不变；上传后仅新增远程映射，不替换本地主键。远程映射按服务器实例和账号隔离。一本书允许多个文件，进度和标注绑定具体文件版本；不同正文不得盲目套用旧 Locator。阅读位置用版本化 Locator JSON，百分比只用于展示。Room 必须提供迁移，不允许破坏性清库。

## 7. 账号和 API

参考 mybooks-android 的 AuthClient、Endpoint、ConnectionStore 和 ApiClient：

1. 设置中输入 acbooks 服务器地址，检查服务身份、初始化及协议能力；无需配置服务端存储路径或其他存储服务账号。
2. 用已有 acbooks 账号密码登录，获取独立设备 Token；用 Android Keystore 加密保存，不保存密码。
3. 后续 API 使用 `Authorization: Bearer <token>`。限制凭据只发送给绑定服务器，禁止登录重定向和跨域泄露；默认 HTTPS，局域网 HTTP 需明确确认风险。
4. 退出登录撤销设备会话、停止同步并清除凭据，保留本地数据、关联及未同步操作。

现有 acbooks `/api/v1` 已有 books、files、uploads、reading-sessions、reading-positions、bookmarks、annotations、stats。以 `docs/api/` 和实际 OpenAPI 为准，不复用 my-books 的旧路径。

**待后端实现：**原生设备登录、会话验证/撤销、Bearer 鉴权、服务器实例身份与能力声明、完整增量拉取及变更幂等。复用已有 `devices`、`sync_changes`、`sync_mutations` 模型和业务服务；保留 Web Cookie/CSRF 安全规则。不得假设这些能力已经开放。设备 ID 必须绑定认证身份。

## 8. 同步与文件传输

- 首次连接仅获取远程目录，按需下载。用户明确选择关联、上传和同步范围；不自动上传全部本地历史或正文。授权目录与 URI 仅在本机有效，不同步权限或路径；用户明确选择上传时通过 ContentResolver 读取正文并校验。
- 本地修改与 outbox 写入同一事务。已关联记录使用稳定 mutationId 重试；服务端按设备和 mutationId 去重，确认后才清队列。
- SyncCoordinator 串行执行拉取、合并、推送；按服务器游标拉取变化。整批成功后提交游标，游标失效重新获取完整快照；空增量不是删除。
- 使用 revision 与共同基线做三方合并；不同记录自动合并，同记录冲突保留本地版本并提示选择。不得用设备时间强制覆盖或用回包覆盖同步期间的新修改。
- 自动模式参考 MyBooksSyncWorker：启动、退出阅读、退后台触发；本地变化防抖后调度 WorkManager，联网才运行，临时错误指数退避。手动模式只在用户点击时同步；认证失效暂停并保留队列。
- 远程文件下载使用 Range/If-Range、强 ETag、SHA-256 校验；缺少可信验证器时完整下载，不拼接不明正文。校验完成才发布为可读文件。
- 本地删除仅移除 App 管理副本或外部文件引用，不删除授权目录原件；远程删除保留本地内容和阅读历史，标记关联失效。删除云端原件必须单独明确确认。禁止自动合并书目；服务端原件的操作必须遵循 acbooks API 的权限及文件保护规则。
- 首期仅一个活跃服务器。重新登录同一实例复用关联；切换实例或账号不得复用旧游标、凭据及远程 ID。

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
