# acbooks — 系统架构

> 项目名称：**acbooks** ｜ 许可证：**GPL-3.0-only（GPL v3.0）** ｜ 定位：单用户自托管个人图书馆

## 1. 系统简介

- **部署**：NAS 优先，也可独立部署于 Linux 服务器；Docker Compose，兼容 AMD64 / ARM64。
- **统一书库**：实体书与电子书不分库；一条 `Book` 可关联实体副本、多个电子文件及阅读数据。
- **客户端**：Web 负责书库管理、电子书下载、阅读记录和统计；Windows 负责批量管理；Android 负责电子书正文阅读和离线阅读；iOS 后续支持。Web 阅读器不在项目范围内。
- **功能**：书目与元数据、实体书存放位置、文件上传/扫描/去重、阅读与离线下载、多端同步、WebDAV、OPDS。
- **阅读统计**：累计时长、阅读次数、每次起止时间、有效时长、相邻阅读间隔、趋势图、日历热力图、阅读时间线。

## 2. 技术架构

| 模块 | 技术 |
| --- | --- |
| 后端 / 后台任务 | Python、FastAPI、Pydantic、SQLAlchemy、Alembic |
| 数据库 | SQLite（WAL） |
| Web | React、TypeScript、Vite |
| Windows | Tauri、React、TypeScript；按需使用 Rust |
| Android | Kotlin、Jetpack Compose、Room、Readium |
| iOS（后续） | Swift、SwiftUI；可评估 KMP |
| 阅读 / 文件处理 | Readium、PDFium、Python 文件解析工具 |
| 部署 | Docker Compose；NAS / Linux 统一部署包；Web 构建产物由 FastAPI 同源托管 |

**架构约束**：模块化单体；API 与 Worker 复用业务逻辑；客户端不直连数据库；REST + OpenAPI 为内部与客户端接口；OPDS 负责书籍发现/下载，WebDAV 负责文件访问，进度和笔记通过专用同步 API。

## 3. 核心数据模型

```text
Book                 # 统一书籍记录：元数据、标签、书架、阅读状态
├── BookCopy[]        # 实体副本、存放位置、购入信息
├── BookFile[]        # EPUB / PDF / 漫画 / 有声书等
├── ReadingSession[]  # 每次阅读的时间和有效时长
├── Bookmark[]        # 书签
└── Annotation[]      # 划线与笔记

BookFile → StorageObject  # 文件路径、哈希、大小
ReadingPosition → BookFile  # 每个文件的阅读位置
```

- `Book` 可仅有实体副本、仅有电子文件，或两者都有；不按文件名自动合并不同书籍。
- 阅读会话保存 `bookId`、可选 `fileId`、`deviceId`、`startedAt`、`endedAt`、`activeSeconds`、`source`；实体书可手动录入。
- 阅读间隔 = 本次开始时间 − 上次结束时间；时间统一存 UTC，按本地时区显示；离线记录使用唯一 ID 去重。
- 仅统计有效阅读时间；暂停、后台和长时间无操作不计时。统计由会话数据计算，可重新生成。

## 4. 代码与文档目录

```text
acbooks/
├── apps/
│   ├── acbooks-server/
│   │   ├── app/
│   │   │   ├── api/           # HTTP 路由
│   │   │   ├── modules/       # books/copies/files/library/reader/stats/sync/opds/webdav
│   │   │   ├── db/            # ORM、迁移
│   │   │   ├── workers/       # 扫描、导入、解析、任务
│   │   │   └── core/          # 配置、鉴权、通用能力
│   │   └── tests/             # 后端单元与集成测试
│   ├── acbooks-web/           # React Web；测试随项目存放
│   ├── acbooks-desktop/       # Tauri Windows
│   ├── acbooks-android/       # Kotlin Android
│   └── acbooks-ios/           # SwiftUI（后续）
├── packages/
│   ├── acbooks-ui/            # Web/Windows 共享组件
│   └── acbooks-api-client/    # OpenAPI 生成客户端
├── docs/
│   ├── architecture/          # 系统设计、数据模型、架构决策
│   │   └── system.md          # 本文档在仓库中的建议位置
│   ├── development/           # 开发规范、环境配置、模块约定
│   ├── testing/               # 测试策略、测试用例、测试报告
│   ├── api/                   # API 与协议文档
│   └── deployment/            # NAS/Linux 部署及备份说明
├── deploy/                    # Docker Compose / Dockerfile
├── scripts/                   # 构建、检查、发布脚本
├── .github/workflows/         # CI
├── LICENSE                    # GPL-3.0 完整许可证文本
└── README.md
```

**文档规范**：
- 开发文档统一放 `docs/development/`；测试**文档**统一放 `docs/testing/`；自动化测试**代码**放对应 `apps/acbooks-*/tests/` 或项目内测试目录。
- 架构设计统一放 `docs/architecture/`；接口说明放 `docs/api/`；部署说明放 `docs/deployment/`。
- 统一使用 Markdown、英文小写目录名、`kebab-case` 文件名；文档更新应与对应代码变更同步。

## 5. NAS / 服务器数据目录

```text
/data/acbooks/
├── database/     # acbooks.db
├── library/      # 应用管理的电子文件
├── covers/       # 封面、缩略图
├── cache/        # 可再生缓存
├── backups/      # 数据库与配置备份
├── logs/         # 运行日志
└── secrets/      # 密钥，不纳入公开仓库
```

NAS 现有藏书目录可另外挂载并原地扫描，默认不移动/改名；SQLite 数据库使用运行设备的可靠本地文件系统，不放远程 SMB/NFS 共享目录。程序与数据分离，升级不覆盖数据。

## 6. 开发顺序

1. 统一 `Book` 模型、数据库、NAS 扫描、文件上传和 Web 书库。
2. 元数据管理、Windows 批量操作、REST/OpenAPI、WebDAV、OPDS。
3. Android 阅读、离线与多端同步、阅读会话采集、Web 阅读统计。
4. iOS、格式转换、插件等扩展。

## 7. 开源约定

项目名称、仓库名、应用标识和主要服务/数据目录使用 **acbooks**；源码以 **GPL-3.0-only** 开源，仓库根目录保留完整 `LICENSE`。引入第三方依赖时检查许可证兼容性。
