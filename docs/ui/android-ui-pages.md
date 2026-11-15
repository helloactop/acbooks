# acbooks Android 页面设计规范

> 路径：`docs/ui/android-ui-pages.md`<br>
> 上位规范：[android-ui.md](android-ui.md)、[android-reader.md](../architecture/android-reader.md)<br>
> 本文规定页面结构、组件职责与交互；颜色、形状、材质、动效及国际化统一遵循 UI 规范。

## 1. 主导航与标题

- 默认启动首页，无需登录。三个 Tab：**首页 / 书库 / 统计**（Home / Library / Statistics）。切换保留各页滚动、筛选及布局状态；支持横向滑动，不与内部横向列表手势冲突。
- 手机：底部悬浮玻璃胶囊，高 64dp；右侧独立 64dp 圆形添加按钮，间距 8dp。添加不是页面 Tab，不改变当前选中状态。
- 宽屏：左侧悬浮纵向导航，添加按钮独立置于上方；根据可用窗口宽度适配，不锁定方向。
- 三页复用 `MainTabHeader`：状态栏下 12dp 开始，预留 20dp 副标题区域（随字体缩放），大标题 32sp Bold，标题行最小高 40dp，页面边距 24dp。右侧为页面操作。
- 滚动内容允许经过悬浮导航下方；使用 WindowInsets、列表末尾留白及渐进模糊，保证最后一项可完整滚出遮挡区。阅读器不显示主导航。

## 2. 首页 Home

```text
日期 / 星期
首页                              [头像/设置]

[封面]  继续阅读：书名、作者、进度     [···]

最近阅读
[封面]    [封面]    [封面]    → 横向滚动
 书名      书名      书名
 作者      作者      作者
 进度      进度      进度

今日阅读
        弧形目标进度 / 阅读时长
        每周阅读打卡

[首页   书库   统计]                [+]
```

| 区域 | 规范 |
| --- | --- |
| 标题 | 日期和星期 13sp；右侧头像/设置 40dp 视觉尺寸、48dp 触控区 |
| 继续阅读 | 最近实际阅读的一本书；18dp 连续圆角、16dp 内边距；封面宽 72dp，3:4，圆角 8dp |
| 卡片背景 | 封面静态模糊 24dp + 约 30% 暗色遮罩；根据对比度调整；无封面或减少透明效果时用可读实色；不是持续采样玻璃卡片 |
| 最近阅读 | 排除继续阅读书籍，最多 12 本；LazyRow，手机封面宽 96dp、宽屏 120dp，3:4、圆角 16dp、间距 16dp；书名 12sp、作者 11sp、进度 |
| 阅读目标 | 今日有效阅读时长、弧形进度、当周七天打卡；点击打开目标详情/设置 Sheet |
| 页面间距 | 区块间 24dp，分组标题 20sp；宽屏内容最大宽度建议 840dp |

- 点击继续阅读或最近阅读封面打开本地阅读器；从封面位置过渡，返回恢复原滚动位置。更多菜单提供详情、收藏等可用操作。
- 首页只展示已实际阅读的书籍；没有阅读历史时不伪造进度。空书库显示导入提示；有书但未读时提供前往书库入口。
- 所有内容来自本地数据；服务器连接、下载状态不阻塞首页。文件不可用时提示重新授权、定位或下载，不丢失阅读历史。
- 第一阶段不增加独立待阅读、推荐流或复杂会员入口。

## 3. 书库 Library

```text
共 N 本 · 已读完 N 本
书库                             [编辑] [···]
[ 搜索书籍                 ]   [全部⌄ │ 分类]

[分类封面]          [书籍封面]          [书籍封面]
 分类名              书名                书名
 数量                作者 / 进度         作者 / 进度
...

[首页   书库   统计]                [+]
```

### 3.1 标题与筛选

- 副标题显示本地书籍总数、已读完数；进入分类显示名称和返回路径。标题右侧编辑与更多按钮视觉尺寸 36dp，实际触控区 ≥48dp；有同步任务时显示非阻塞进度。
- 标题下 12dp 放置搜索与筛选胶囊，均高 48dp、间隔 12dp。左侧搜索占剩余宽度；右侧为「快捷筛选 ⌄ | 分类图标」双操作胶囊。
- 搜索从胶囊展开独立浮层，聚焦键盘，实时匹配书名、作者、标签；支持清除、退出、空结果；关闭保留原书库状态。
- 快捷筛选：全部、EPUB、PDF、收藏、阅读状态、标签；仅展示已实现的格式与可用标签。菜单锚定触发区域，显示当前选中项。
- 分类入口浏览虚拟分类、标签、已授权文件夹；虚拟分类和物理目录分离，不把分类移动等同于物理文件移动。服务器书库是独立入口，不混入本地目录授权。
- 更多菜单集中排序（最近阅读、添加时间、书名、作者）、布局、创建分类、刷新授权目录、手动同步（连接后可用）；不堆叠常驻工具按钮。

### 3.2 书籍展示

| 模式 | 布局 |
| --- | --- |
| 标准网格 | 手机 2 列；横向间距 24dp |
| 紧凑网格（默认） | 手机 3 列；横向间距 12dp |
| 列表 | 左封面、右书名/作者/进度/状态；高度随文字增长 |
| 宽屏 | 自适应列宽，网格最小 140dp |

- 网格纵向间距 24dp，页面边距 24dp；封面比例 3:4，连续圆角 16dp，封面下留 8dp。书名 14sp Semibold、最多两行；作者 12sp。无封面使用统一占位图与书名。
- 不在每本书外加玻璃卡片；封面是视觉中心，收藏、阅读进度、待下载、文件缺失等状态使用小图标或简短文字；必要时使用轻阴影。
- 分类在普通书籍前展示，使用同尺寸组合封面、名称和数量；文件夹内可保留返回路径。
- 点击可用封面进入阅读器，长按显示详情、编辑、收藏、标签、移动、移除等操作；未下载的书籍先显示下载操作，不自动假装可阅读。
- 编辑模式将搜索筛选行替换为完成、删除、标签、移动等批量操作，并提供已选数量和全选；退出恢复正常工具栏。移除书目与删除文件必须分开确认，授权目录原文件默认不删除。
- 布局与排序偏好持久化；第一阶段不实现 Cover Flow 和书脊模式，不预留无效入口。

## 4. 独立添加按钮

- 从 `+` 锚点展开玻璃菜单：选择本地文件、授权本地文件夹、从 acbooks 导入（已连接时）。未登录仍可使用前两项，并可通过设置连接服务器。
- 本地文件：系统文件选择器、多选、默认复制到 App 私有存储。
- 授权文件夹：SAF 持久读取权限 → 递归扫描 → 多选/全选 → 批量导入；默认引用原文件，支持主动复制；可刷新与重新授权。
- 服务器导入：通过现有账号及 API 进入远程书库、选择和下载；不直接访问服务器文件系统。
- 导入支持取消、重复检测、进度和错误反馈；在书库分类内发起时允许选择当前虚拟分类，不擅自移动原文件。返回后保留当前 Tab。

## 5. 其他页面基线

| 页面 | 结构 |
| --- | --- |
| 统计 | 共用大标题；时长、次数、间隔；趋势、月度热力日历、时间线；点击日期查看各书时长与记录 |
| 书籍详情 | 封面、书名/作者、继续阅读、书目信息、文件、阅读记录、笔记/书签；宽屏可双栏 |
| 阅读器 | 正文优先，轻触显示工具栏；目录、进度、笔记、阅读设置使用浮层；纸色独立 |
| 设置 | 首页头像进入；外观与语言、阅读、授权目录、服务器连接与同步、备份、关于 |

## 6. 组件与实现约束

- `MainTabHeader`、`AppNavigationBar`、`AppAddButton`、`AppSearchField`、`AppMenu` 为通用 UI；`ContinueReadingCard`、`RecentBooksRow`、`BookCover`、`BookGridItem`、`BookListItem`、`LibraryFilterCapsule` 为书籍业务组件，可跨页面复用。
- 首页和书库各自持有 ViewModel，通过 Repository 读取本地数据；不在 UI 执行文件扫描或同步。所有文本同时提供中英资源；放大字体时允许布局换行，不压缩触控区。
- 验收：未登录能完成导入和阅读；切换 Tab 保留状态；长按、批量编辑、搜索和筛选无冲突；小屏、横屏、平板和深色模式均可用；玻璃回退不改变功能。滑动和阅读过渡必须真机验证。

## 7. 参考源码

Lumi Books 当前基线 `66c6d9df36db3b96d73af634bcd34835edff9fb7`，只借鉴结构与交互，不复制整套页面或旧版三主题逻辑。

- [HomeScreen.kt](https://github.com/huangder/Lumi_Books/blob/66c6d9df36db3b96d73af634bcd34835edff9fb7/app/src/main/java/com/huangder/lumibooks/ui/home/HomeScreen.kt)、[RecentBooksRow.kt](https://github.com/huangder/Lumi_Books/blob/66c6d9df36db3b96d73af634bcd34835edff9fb7/app/src/main/java/com/huangder/lumibooks/ui/home/RecentBooksRow.kt)
- [BookshelfScreen.kt](https://github.com/huangder/Lumi_Books/blob/66c6d9df36db3b96d73af634bcd34835edff9fb7/app/src/main/java/com/huangder/lumibooks/ui/bookshelf/BookshelfScreen.kt)、[BookshelfSearchOverlay.kt](https://github.com/huangder/Lumi_Books/blob/66c6d9df36db3b96d73af634bcd34835edff9fb7/app/src/main/java/com/huangder/lumibooks/ui/bookshelf/BookshelfSearchOverlay.kt)
- [MainTabHeader.kt](https://github.com/huangder/Lumi_Books/blob/66c6d9df36db3b96d73af634bcd34835edff9fb7/app/src/main/java/com/huangder/lumibooks/ui/components/MainTabHeader.kt)、[NavGraph.kt](https://github.com/huangder/Lumi_Books/blob/66c6d9df36db3b96d73af634bcd34835edff9fb7/app/src/main/java/com/huangder/lumibooks/ui/navigation/NavGraph.kt)
