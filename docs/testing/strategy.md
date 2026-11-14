# 自动化检查

GitHub Actions 在分支推送、Pull Request 或手动触发时运行
[CI 工作流](../../.github/workflows/ci.yml)。后端在 Ubuntu 和 Windows
上以最低支持版本 Python 3.12 运行相同的检查；Web 在两个平台使用 Node.js 22.13
与 24，配合仓库锁定的 pnpm 版本运行相同的检查。Playwright 端到端测试在 Ubuntu
上以单源静态托管运行；Docker 部署在 AMD64 与 ARM64 运行器上分别验收。
各任务分别报告结果，某个任务失败时其他任务继续运行。

## 本地运行

在独立的 Python 3.12 虚拟环境中，从 `apps/acbooks-server/` 执行以下命令。

```sh
python -m pip install -e ".[dev]"
python -m ruff check .
python -m mypy app tests
python -m pytest
```

Web 与共享 UI 从仓库根目录执行：

```sh
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm i18n:check
pnpm test
pnpm build
pnpm --filter @acbooks/web exec playwright install chromium
pnpm e2e
```

端到端测试需要可导入 `fastapi` 与 `sqlalchemy` 的 Python；默认使用
`apps/acbooks-server/.venv`，可用 `ACBOOKS_PYTHON` 指定。测试使用临时数据库与
`http://127.0.0.1:4174`，会先构建 Web。无法下载 Playwright 浏览器时，可设置
`ACBOOKS_E2E_CHANNEL=chrome` 或 `msedge` 使用系统浏览器。

## 当前覆盖范围

| 检查 | 覆盖范围 |
| --- | --- |
| Ruff | 后端 Python 代码的错误、未使用导入和导入排序 |
| mypy | 后端应用及测试代码的严格类型检查 |
| pytest | FastAPI 配置、存活与就绪探针（数据库连接和迁移版本）、OpenAPI、业务错误码、500 脱敏诊断和请求 ID |
| pytest | SQLite 临时文件库上的 Alembic 升降级、模型一致性、异常回滚重试、连接参数、外键和唯一约束、阅读状态与有效时长约束、列表查询索引命中、任务和文件状态校验、并发写入、在线备份恢复及旧库升级保留电子文件引用 |
| pytest | 首次管理员初始化的码文件生成与权限、来源和 CSRF 验证、密码校验与 Argon2id 存储、限速、双应用实例并发、事务失败重试及清理失败后的重启 |
| pytest | Web 密码登录与限速、Cookie 属性、会话摘要与过期、重启持久化、会话轮换、CSRF 来源与绑定、单会话注销、登录时清理过期与撤销凭据 |
| pytest | 数据库备份：在线快照与清单、完整性/外键校验、篡改检测、恢复前快照、原子替换、拒绝活动 WAL/SHM、拒绝更新版本的备份、恢复前校验 managed 电子书/封面存在性（缺失拒绝、可放行） |
| pytest | Compose 部署冒烟（Ubuntu AMD64/ARM64）：镜像内 Web 静态托管与 SPA 回退、就绪探针、HTTPS 代理下的 Cookie/CSRF、迁移门禁、备份命令、Worker 与重启持久化 |
| pytest | Books Core V1：创建书目、重复 ISBN 不合并、作者/译者/ISBN/标签/书架关联、Unicode casefold 名称匹配、数据库级搜索筛选排序分页、部分更新保留副本与阅读数据、单本/批量删除及关联清理（含阅读位置）、批量状态/标签/书架与合并上限、封面上传/替换/删除与回退、Pillow 内容与尺寸校验、封面数据库失败保留原文件、会话/CSRF、OpenAPI 契约 |
| pytest | Copies/Locations：位置层级 CRUD 与防循环、有子级拒绝删除与副本 SET NULL、副本 CRUD 与越界校验、内联副本原子性、location 子树筛选、locations 选项源与筛选 schema、鉴权与错误码 |
| pytest | Files：流式上传 PDF/EPUB 与 SHA-256、真实格式校验、超大与重复拒绝、默认 managed 根、全文/Range/206/416/ETag 下载、路径越界拒绝、managed 删除并清阅读位置、external 只读拒绝、鉴权/CSRF、索引失败清理已发布文件、上传中断/磁盘满/发布失败清理、恶意文件名、符号链接越界、external 下载、幂等与并发删除、PDF/EPUB 结构校验与 ZIP 解压/条目/元数据上限、HTTP 接收层大小限制、下载拒绝非普通文件、解除关联（purge=false）与永久删除（purge=true）区分 |
| pytest | Storage roots & scan：注册绝对/存在/可读校验、重叠与受保护目录拒绝、占用删除拒绝；增量幂等不重复建书、多级遍历与隐藏/临时/符号链接跳过、格式校验与跳过、深度重算哈希、仅完整成功标缺失（根掉线不改状态）、任务入队去重/原子领取/恢复与结果回传 |
| pytest | 书库整理：同名 EPUB/PDF 扫描后待关联、候选依据、角色隔离、重新关联保留进度、合并预览过期、注入触发器失败后的完整回滚、实体副本和阅读迁移、NAS 原文件逐字节保留 |
| pytest | 阅读 API：手动和自动会话、书签和笔记 CRUD、并发重复提交、非法时长、进度冲突与重试、书籍与文件归属、CSRF、OpenAPI 错误契约 |
| pytest | 多端同步：快照与增量追赶、批量操作和冲突回执、并发幂等、文件关联与位置删除、级联墓碑和事务回滚 |
| pytest | Android 文件接入：Bearer 认证、SHA-256 候选分页、并发重复上传复用、If-Range 续传和版本降级、下载哈希校验、双凭据阅读位置同步 |
| pytest | 阅读统计：重叠去重、跨天、夏令时、全部历史、跨年区间和包含零时长会话的分页 |
| ESLint | Web 与共享 UI 的 TypeScript/React 代码、Hooks 规则和导入问题 |
| tsc | Web 与共享 UI 的 TypeScript strict 类型检查 |
| i18n:check | 共享与 Web 翻译资源的语言目录、命名空间、键集合和插值参数一致性 |
| Vitest | 共享 UI 的按钮变体与加载、表单标签/错误关联、禁用输入与密码切换、主题解析/本地保存/浏览器 theme-color、分段选择器键盘操作、浅深色令牌及组件级对比度（Soft/Default/Outline、分段选中、输入框、禁用态）、语言解析/回退/持久化/系统语言变化、Spinner 与密码控件双语文案、多值输入的数量与长度上限 |
| Vitest | Web 预览页中英即时切换、`<html lang>` 与本地存储更新、刷新恢复语言、系统语言跟随及切换不丢失输入 |
| Vitest | Web 初始化/登录状态机、CSRF 请求头与 Cookie 同源请求、错误码与 `Retry-After` 映射、加载禁用、初始化成功后返回登录、已有会话跳转 `/library`、认证页双语切换 |
| Vitest | Web 全部书籍界面：真实 Books API 的会话守卫、服务端搜索/筛选/排序/分页、加载/空库/无结果/失败重试、添加/详情/编辑、CSRF 保存、退出登录/会话失效、标签与格式/副本区分、IME 组合处理、加载期禁用选择与批量目标快照、详情页状态/封面互斥、详情页副本增删改、位置管理页层级/创建/删除与查看书籍跳转、详情页电子文件上传/下载/删除、添加书关联文件部分失败、存储根管理页列表/注册/扫描结果、双语与响应式结构 |
| Vite | Web 生产构建、共享 UI 源码解析与 Tailwind 样式生成 |
| Playwright | 真实服务端与同源静态托管下的登录（失败/成功/刷新恢复）、添加书籍、阅读状态筛选、详情编辑弹窗、批量选择/修改/删除、封面上传与删除、Dialog 内 Select、Escape 关闭顺序、搜索 A→B→A、高级筛选、浏览器返回、实体副本内联创建与详情增删改、位置层级管理与按位置子树筛选、电子文件上传/下载/删除、390px 移动端抽屉与下拉菜单 |
| Playwright | 阅读统计：全局补录、单书编辑与删除、统计自动刷新、全部时间、自定义范围、月历热力分级、键盘打开每日详情、深浅色切换、窄屏图表布局 |

测试使用 pytest 的临时目录创建数据库，不需要真实书库、NAS 挂载或服务凭据。
前端测试使用 jsdom，不连接真实 API。Windows 客户端和 Android 工程初始化后，
再接入各自的构建与检查。

## 验收要求

| 改动范围 | 验收条件 |
| --- | --- |
| API 和错误处理 | 核对 HTTP 状态、问题响应类型、业务码、请求 ID 和有语义的响应头。对 500 同时检查响应与内部日志，验证请求 ID 能关联诊断，异常原文、凭据及内部路径未泄露。契约变化时同步检查 OpenAPI 和生成客户端。 |
| 数据库 | 在空库和上一版本的数据副本上升级，检查版本、数据和外键。测试非法写入、失败回滚和重试。降级在可丢弃的副本上验证，使用在线备份执行恢复检查。 |
| 文件与后台任务 | 使用临时库与隔离文件目录模拟重复执行、权限失败、挂载失联和处理中断。验收恢复后的任务状态、错误摘要、文件关系与原件保留。NAS 挂载和容器权限在目标环境单独验证。 |
| 认证与多端同步 | 对已实现功能覆盖未授权、过期、撤销、重复提交和冲突，分别验收接口行为与数据库结果。 |

交付记录应注明受测提交、平台和运行命令，记录通过、失败及未执行的场景。
新功能纳入验收时，至少覆盖成功路径和相关的失败或恢复路径。

## Docker 部署验收

CI 在 Ubuntu AMD64 和 ARM64 上运行真实 Docker Compose 集成测试。测试使用独立的项目、
临时端口和空数据卷，并在结束后清理容器与数据卷。可在 Docker Engine 运行时从仓库根目录复现。

```sh
python apps/acbooks-server/tests/compose/smoke.py
```

- 验证迁移权限错误阻止 API 和 Worker 启动，修复权限后检查 Alembic 版本、非 root 用户和 SQLite WAL。
- 通过启用证书校验的 Caddy HTTPS 代理完成管理员初始化、登录及登出，验证 Cookie、一次性初始化码和代理信任边界。
- 用共享数据卷启动第二个 Worker，验证排他锁和正常关闭，并验证未就绪的 Worker 无法通过 `up --wait`。
- 重建容器后验证管理员 ID 保留，已使用的初始化码没有重新生成。

目标 NAS 的实际绑定目录、域名和代理配置仍需按 [Docker 部署说明](../deployment/docker.md)验收。
