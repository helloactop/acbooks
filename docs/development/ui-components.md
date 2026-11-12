# acbooks �?UI 公共组件规范

> 修订�?026-11-12 · v3（中英文国际�?+ Lucide 功能图标�?> 路径：`docs/development/ui-components.md`
> 上位 UI 规范：[ui.md](./ui.md)；工程约束：[standards.md](./standards.md)。范围：Web / Windows 共享 UI；Android 仅参考视觉与交互规则�?
> 本文只定义组件结构、变体和状态；颜色、圆角、字号、尺寸、动效、语言与图标策略以 `ui.md` 为准。冲突先报告，不另建规范�?
## 1. 通用规则

- 共享组件�?`packages/acbooks-ui/`，业务页面放各应用；优先复用已有组件，按需求实现，不预建空模块。采�?shadcn/ui + Radix Primitives + Tailwind CSS + Lucide React；不引入第二�?UI 库�?- 公共组件只接收类型化 props、状态和回调，不请求业务 API、不持有业务权限逻辑；业务容器使用生成的 API 客户端。保留原生元素语义、键盘操作和 ref 传递；禁止嵌套交互元素（如 button 内再�?button）�?- 只消�?`ui.md` 的语义令牌；仅内�?ui.md 规定的淡蓝配色，保留自定义能力；不硬编码颜色，不复制主题样式，不使用玻璃、背景模糊或折射。禁止通过页面 className 覆盖公共组件的核心颜色、尺寸和圆角；需要新变体先检查规范�?- 以下尺寸均为 Web CSS px 基准，优先映射已�?rem 令牌。移动端可点击目标至�?44×44；文字可缩放，容器允许随内容增高�?- 国际化继�?`ui.md`：组件通过共享翻译资源或调用方传入的已本地�?props 获取文案；内置默认文案（�?Spinner、PasswordInput、关闭、分页）必须随语言更新。Tooltip、`aria-label`、错误、占位符与可见文字同步；不在组件内部建立独立语言状态或翻译实例�?- 中英文共用组件；按钮宽度由内容决定，长文案允许重排，设置行、菜单、表格标题和 Dialog footer 允许换行。尺寸以 `ui.md` 为基准，不裁切重要操作名称、不缩小字号或命中区。计数、日期、时长用统一 locale-aware 格式化，保留原业务值�?
## 2. 图标组件与语义映�?
- 基础组件阶段统一 `Button`、`IconButton`、`PasswordInput`、`Spinner` �?Lucide 图标用法；具名导入实际使用的组件，例�?`import { Plus, Search } from "lucide-react"`。按钮内 SVG 默认 18px、线�?2、`currentColor`，保留明确的尺寸覆盖入口供导航等规定场景使用；不以全局 SVG 样式影响品牌标志或图表�?- 公共组件直接复用 Lucide 的具名导入；如有实际复用需求，�?`packages/acbooks-ui/` 内统一图标尺寸和语义映射（类型可用 `LucideIcon`），不创建包含全部图标的动态注册表。页面通过共享映射或同一 Lucide 名称取用，禁止按页面重复实现相同 SVG。图标尺寸、线宽和语义颜色继承 `ui.md`，不随语言切换改变�?- `Button` / `IconButton` 统一子图标为 18px、禁止收缩；导航 20px，辅助图标按 `ui.md`。图标与文字间距 8px，加载图标复�?Spinner 并保留布局；图标只负责表达，不单独承担点击事件，点击使用真正的 Button/链接�?- 有可见文字的图标设为 `aria-hidden=true`，避免重复朗读；IconButton、折叠导航和纯图标控件由控件提供本地化名称（�?`aria-label`），图标本身不获得焦点。Tooltip 同步当前语言；状态图标有文字或等价无障碍语义。切换图标时同时更新控件状态与名称�?
| 语义 | Lucide React 图标（统一选型�?|
| --- | --- |
| 首页 / 全部书籍 / 书架 | `House` / `LibraryBig` / `BookMarked` |
| 阅读记录 / 阅读统计 / 日历 | `History` / `ChartBar` / `CalendarDays` |
| 存储 / 导入 / 任务 / 设置 | `HardDrive` / `Upload` / `ListChecks` / `Settings` |
| 搜索 / 筛�?/ 排序 / 网格 / 列表 | `Search` / `SlidersHorizontal` / `ArrowUpDown` / `LayoutGrid` / `List` |
| 添加 / 编辑 / 删除 / 更多 | `Plus` / `Pencil` / `Trash2` / `Ellipsis` |
| 阅读 / 下载 / 书签 / 高亮 | `BookOpen` / `Download` / `Bookmark` / `Highlighter` |
| 语言 / 浅色 / 深色 / 系统 | `Languages` / `Sun` / `Moon` / `Monitor` |
| 侧栏 / 返回 / 前后�?/ 展开 / 关闭 | `PanelLeft` / `ArrowLeft` / `ChevronLeft`、`ChevronRight` / `ChevronDown` / `X` |
| 显示密码 / 隐藏密码 / 退�?| `Eye` / `EyeOff` / `LogOut` |
| 成功 / 警告 / 错误 / 信息 / 加载 / 重试 | `CircleCheck` / `TriangleAlert` / `CircleAlert` / `Info` / `LoaderCircle` / `RefreshCw` |
| 缺失封面 / 勾�?| `BookOpen` / `Check` |

同一语义保持一致；未列出的图标按功能从 Lucide 选择并复用，不另造图形。实际导出名以仓库锁定版本及 TypeScript 检查为准。危险操作始终有明确文字及确认步骤，不用纯图标替代最终确认�?
## 3. Button / IconButton

| 变体 | 默认样式 | Hover / Active | 使用场景 |
| --- | --- | --- | --- |
| `soft` | `accent-soft` 背景 + `accent-border` 边框 + `accent-text` + 600 字重 | `accent-hover` / `accent-active`（仍为淡色） | 每个操作区唯一主操作：添加、导入、登�?|
| `default` | `surface` 背景 + `foreground` | `surface-hover` / `surface-active` | 需要中性填充的表单提交：保存、确认、应�?|
| `outline` | 透明 + `control-border` + `foreground` | 中性浅色背�?| 需要边界的次要操作：筛选、视�?导航入口 |
| `ghost` | 透明 + 无可见边�?| 中性浅色背�?| 取消、编辑、工具栏 |
| `danger` | 透明 + `destructive-text` | 极淡危险色背�?| 删除入口 |
| `danger-confirm` | `destructive` + `destructive-foreground` | 保持红色，验证对比度 | 危险操作最终确�?|

- `Button` 默认变体�?`default`；每个操作区最多一个视觉主操作。不提供普�?`solid` 变体；禁止黑色、白色反相或高饱和主题色实心按钮。`danger-confirm` 仅用于危险确认流程�?- `soft` 的加�?`accent-soft`、`accent-border` 与文字组合必须经过浅深主题对比度验证：文�?�?4.5:1，边框相对背�?表面及自身淡色背�?�?3:1。边框预�?1px，不使用阴影或实心主色�?- `default` 用于提交/确认类操作的中性填充；`outline` 用于次要、可逆或导航/筛选操作。`outline` 不得被用成主要操作，`default` 不在同一区重复出现。需要无边框的取�?工具栏操作使�?`ghost`�?- `size=sm/md/lg` 高度 32/36/44，横向内边距 12/16/24，圆�?10，文�?14/500（Soft 14/600），图标 18，图文间�?8。边框预�?1px，避免状态变化导致布局移动�?- `IconButton` 默认 Ghost�?6×36，图�?18；工具栏可用全圆角，导航/输入框内按容器形状。必须有可访问名称，必要�?Tooltip；移动端扩大命中区�?- `disabled` 禁止交互，使�?`disabled-surface` / `disabled-foreground` / `disabled-border` 语义令牌，不降低整体透明度；`loading` 保留原宽高与按钮文字的布局占位，显�?Spinner 并阻止重复触发，不使用禁用态配色覆盖原变体。提交中有可访问状态提示。普通按钮默�?`type=button`，表单提交显式使�?`submit`。焦点环使用 `ring`，不�?Hover/Active 缩放或位移�?
## 4. 表单组件

| 组件 | 结构与尺�?| 行为 |
| --- | --- | --- |
| `Field` | Label �?Control �?Description/Error；间�?8 | label 关联控件；错误与说明通过 aria 关联；必填有文字/语义提示 |
| `Input` / `PasswordInput` | �?40（认�?44）、圆�?10、横向内边距 12；始终保�?`control-border` 边框 | 密码可见切换使用 IconButton，不丢失焦点；支�?autofill；错误用危险边框与文字；禁用态使�?`disabled-border` / `disabled-surface` / `disabled-foreground`，保持边框与尺寸 |
| `Textarea` | 圆角 10、最小高 96，内边距 12 | 可纵向扩展，不固定裁切正�?|
| `Select` / `Combobox` | Trigger �?40、圆�?10；末尾箭头；选项行最�?36 | 单选用 Select，大量选项需搜索时用 Combobox；选中有勾选标记，支持键盘 |
| `Checkbox` / `Radio` | 视觉 18×18，Checkbox 圆角 4，Radio 全圆 | 标签可点击；支持 checked/disabled；批量选择支持 indeterminate |
| `Switch` | 视觉轨道 40×24、胶囊，滑块清晰 | 仅用于即时布尔设置；用位置与语义表达开关状态，不只依赖颜色 |
| `SearchInput` | �?40、圆�?10；搜索图�?+ 输入 + 清除按钮 | 清除不改变布局；回车提交或按业务约定防抖；空查询恢复默认结果；输入法组合结束后才触发搜�?|

独立字段使用可辨认边界，不能仅用浅灰背景暗示输入框，也不得在页面样式中移除输入框边框。校验失败显示字段错误或表单级错误；保留非敏感输入；不把密码、初始化码、认证令牌写入本地持久化或日志�?
## 5. 导航与选择

| 组件 | 结构与尺�?| 状态规�?|
| --- | --- | --- |
| `SidebarItem` | �?40、圆�?10、图�?20、图文间�?8、横向内边距 12 | 当前路由使用淡色背景 + 强调文字�?`aria-current`；折叠时仍有可访问名�?|
| `Tabs` | 标签�?+ 内容面板，采用底部指示线或中性样�?| 不用整行实心强调色；仅真正的 tab panel 使用 Tabs 语义 |
| `SegmentedControl` | 中性胶囊轨道、高 36，内�?4；选中块中性并�?`segment-selected-border` 边框，内外同心圆�?| 选项等高；有选中语义和键盘操作；边框不随主题使用强调色；用于统计周期、视图模式等互斥选择 |
| `Breadcrumb` | 13px，分隔符 12�?6px | 当前页不可重复导航；窄屏折叠中间项，不裁切当前页 |
| `Pagination` | 按钮复用 Button；当前页淡色背景 + 明确标记 | 消费实际分页结果，边界禁用；筛选变化重置页码；不一次取全库 |

分段选择器不得使用饱和色实心选中块；选中边框为中性的 `segment-selected-border`，并在浅深主题下验证相对背景/表面的可见性。切换选项保持容器尺寸稳定，不因动效延迟业务更新�?
## 6. 浮层与确�?
| 组件 | 结构与尺�?| 行为 |
| --- | --- | --- |
| `Dialog` | 圆角 24；内边距 24；普通最大宽 480、编辑表单可�?640；Header / Body / Footer | 标题与说明；正文独立滚动；操作区固定；取�?Ghost，确�?Default �?Soft |
| `AlertDialog` | 复用 Dialog；清晰显示目标和后果 | 危险操作使用 danger-confirm；默认焦点优先安全操作；失败保留对话框，不假报成�?|
| `DropdownMenu` / `ContextMenu` | 圆角 12、内边距 4；项最小高 36、图�?18；危险项红字 | 分组用细分割线；锚定触发源，碰撞时调整位置；键盘可达 |
| `Popover` | 圆角 12、内边距 12�?6 | 适合短筛�?轻量编辑；大表单�?Dialog；浮层不被卡片裁�?|
| `Drawer` | 移动导航/窄屏面板；内边距 16，贴�?| 支持关闭、滚动和安全区域；不使用玻璃或背景模�?|
| `Tooltip` | 小型实色浮层、圆�?8 | 说明图标操作；鼠标与焦点均可访问；不得承载必要信息或交互按钮 |

- 使用 Radix 处理 Portal、焦点陷阱（模态）、Esc、键盘导航与焦点恢复；非模态不锁全页。浮层继承当前主题，遮罩只使用普通半透明暗层（不模糊）�?- Dialog/Drawer 不超出可用视口；窄屏保留安全边距和可操作 footer。危险操作不得仅靠按钮颜色表达风险；提交中不能重复触发。关闭不会自动取消已提交的服务端任务�?
## 7. 信息与反�?
| 组件 | 规范 |
| --- | --- |
| `Card` | 圆角 14（大�?18）；内边�?16/24；实色、无默认阴影；必要时细边框。禁止层层套卡片 |
| `Badge` | 12px，圆�?4，内边距 4/8；淡色背�?+ 对比文字；状态有明确文本 |
| `Progress` | 书籍进度条高 4、全圆角；使用主题强调色与中性轨道；未知进度显示不确定状态，不伪造百分比 |
| `Toast` | 短时操作反馈、可关闭；不作为表单错误或持续故障的唯一提示；重复消息合�?|
| `Alert` | 持续错误/警告/说明；图�?+ 标题 + 内容，可选重试；文字说明而非仅靠颜色 |
| `EmptyState` | 简洁图标、标题、原因及可选下一步；区分首次空库、无搜索结果和加载失�?|
| `Skeleton` / `Spinner` | Skeleton 保留真实布局尺寸；Spinner 用于局部操作；尊重减弱动态，不反复整页闪�?|
| `DataTable` | 普通行最�?48、紧�?40；表头、排序、选择、行菜单与分页；仅表格区域允许必要横向滚�?|

错误、加载、空状态、成功、禁用必须可区分；异步任务按真实状态显示。状态色独立于主题色；阅读进度、统计等基于实际数据，不将缺失值当 0�?
## 8. 图书与设置组�?
| 组件 | 结构与尺�?| 行为 |
| --- | --- | --- |
| `BookCover` | 2:3 容器、圆�?8，等�?contain；固定占位尺�?| 懒加载、加载失败回退占位；不拉伸或裁切原封面；装饰图避免重复朗读书名 |
| `BookCard` | 封面 �?间距 8 �?书名（最�?2 行）�?作者（1 行）�?可选状�?进度 | 无独立白色底板；网格宽度 144�?80 自适应；主链接打开详情，选择及更多菜单为独立控件 |
| `BookListItem` | 小封面、书�?作者、实�?电子标记、可选元数据、尾部菜�?| 与网格消费同一 Book 数据及选择状态；缺文件不删除书目；操作不触发详情导航 |
| `SettingsGroup` | 标题/说明 + 圆角 14 的共享表面和边界；行间细分割�?| 不为每行新增卡片或阴影，不裁切内�?Portal |
| `SettingsRow` | 最小高 56、横向内边距 16、图文间�?8；前缀 �?标题/说明 �?尾部控件 | 尾部对齐，窄屏允许换行；可点击行与独立控件明确区分，避免双触�?|
| `PageHeader` | 页面标题、可选说�?计数、尾部操作；标题与说明间�?4�? | 窄屏操作换行；不重复顶栏功能；主操作最多一�?|
| `AppShell` | 复用 ui.md 的侧栏、顶栏、内容面板参�?| 独立内容滚动；响应式折叠/抽屉；业务页面不自行创建第二套框�?|

## 9. 开发与验收

- 按实际需求逐步建立组件预览（可用现有预览路由或开发工具），展示全部变体和正常、悬停、焦点、选中、禁用、加载、错误状态；不引入额外框架作为硬性前置条件�?- 修改组件后检查默认配色的�?深色及已实现的自定义配色、键盘操作及焦点恢复、移�?桌面、文字缩放、减弱动态和溢出；组件级对比度至少覆�?Soft 文字/边框、Default/Outline、分段选中边框、输入框边框与禁用态，并遵�?ui.md。测试代码、执行及交付仍遵�?standards.md �?docs/testing/strategy.md�?- 图标验收：验证尺寸与颜色、按需导入、语义一致性、纯图标命中区及双语可访问名称；图文按钮不重复朗读，禁用/加载状态不可误触发�?- 组件验收须覆盖两种语言的文案、可访问名称、错�?加载反馈、切换后状态保留和长英文布局；检查翻�?key 与参数一致、无缺失翻译�?- 新组件需说明真实复用场景；新颜色/圆角/尺寸先核�?ui.md；不因某个页面调整全局组件。不得引入新业务模型、接口或假成功响应�?
## 参考（仅借鉴机制�?
- [Stump Button](https://github.com/stumpapp/stump/blob/main/packages/components/src/button/Button.tsx)：共享组件、变体与加载状态�?- [Mantine variantColorResolver](https://github.com/mantinedev/mantine/blob/master/packages/@mantine/core/src/core/MantineProvider/color-functions/default-variant-colors-resolver/default-variant-colors-resolver.ts)：柔�?轮廓/透明按钮色彩解析�?- [Readest DESIGN.md](https://github.com/readest/readest/blob/main/apps/readest-app/DESIGN.md)：分组设置、动作语义与焦点；不采用实心 contrast 按钮�?- [Lumi UI 规范](https://github.com/huangder/Lumi_Books/blob/main/devdocs/lumi-ui-motion-design-system.md)：令牌、同心圆角、统一组件；不采用玻璃或复杂形变�?- [Radix Primitives](https://www.radix-ui.com/primitives/docs/overview/accessibility) / [shadcn/ui](https://github.com/shadcn-ui/ui)：可访问的浮层、表单和组件组合�?- [Tome BookCard](https://github.com/bndct-devops/tome/blob/main/frontend/src/components/BookCard.tsx)：书籍网�?列表、选择、快捷操作；封面适配遵循 acbooks ui.md�?
- [Lucide React](https://lucide.dev/guide/packages/lucide-react)：线性图标、尺寸属性和具名导入�?