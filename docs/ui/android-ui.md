# acbooks Android UI 设计规范

> 建议路径：`docs/ui/android-ui.md`<br>
> 工程：`apps/acbooks-android/`<br>
> 上位规范：`docs/architecture/android-reader.md`

## 1. 设计原则

- 阅读优先：简洁、适中信息密度、内容清晰；玻璃只用于顶栏、控件和浮层，不覆盖所有内容。
- 唯一外观为 **Liquid Glass**，支持浅色、深色、跟随系统。无 Material 3 / 普通主题切换；Material 3 可作为 Compose 基础能力，不使用其默认视觉风格。
- 设置类页面可用 **Miuix** 组件呈现 MIUI 式分组列表。整套 App 的色值由 `AcbooksTheme` 统一产出为 Miuix `Colors`，Liquid Glass 组件与 Miuix 组件读取同一份色板；Miuix 的材质与卡片圆角沿用其默认值，不改写为 acbooks 令牌。
- 所有页面共享设计令牌、组件、形状和动效；切换明暗模式不改变布局或操作位置。
- 首页是默认入口，优先展示本地阅读内容；登录 acbooks 为可选功能，不设置强制登录页。
- 支持简体中文和英文，适配手机、横屏和平板，兼容系统字体缩放与无障碍。

## 2. 设计令牌

统一放在 `core/designsystem`，页面通过语义令牌取值，禁止散落同义颜色、字号、圆角和动画参数。以下数值为初始基线。

| 类别 | 令牌与数值 |
| --- | --- |
| 间距 | xs 4 / sm 8 / md 16 / lg 24 / xl 32 dp；页面边距 24dp |
| 圆角 | sm 8 / md 14 / lg 18 / xl 22 / capsule 28 dp；全圆按短边一半计算 |
| 字阶 | display 32 / title 28 / section 20 / headline 17 / body 16 / subhead 15 / small 14 / caption 12 / tiny 11 sp |
| 行高 | body 24 / small 20 / caption 16 sp；随字体缩放 |
| 字重 | 正文 Regular；操作 Medium；标题 SemiBold；大标题 Bold |
| 图标 | 小图标 18 / 常规 24 dp；统一图标库与线宽 |
| 触控 | 最小点击区域 48×48dp；视觉尺寸可更小 |
| 动效 | fast 120 / normal 200 / slow 300 ms；形变使用统一弹簧参数 |
| 玻璃 | 默认透明度 0.55；控件模糊 8dp、浮层 12dp；顶栏高斯模糊 24dp；顶栏底色透明度 0.38 |

### 2.1 语义颜色

| Token | Light | Dark |
| --- | --- | --- |
| background | `#F7F7F7` | `#000000` |
| surface | `#FFFFFF` | `#242424` |
| surfaceSecondary | `#F2F2F7` | `#2C2C2E` |
| textPrimary | `#000000` | `#FFFFFF` |
| textSecondary | `#6E6E73` | `#98989D` |
| divider | `#E5E5EA` | `#38383A` |
| accent | `#5D92E8` | `#5D92E8` |
| accentText | `#285FC1` | `#8AB4FF` |
| destructive | `#D92D3A` | `#FF6961` |

页面底色与卡片色对齐 Miuix 的 `surface` / `surfaceContainer` 分工：浅色为灰底 `#F7F7F7` 上的白卡 `#FFFFFF`；深色为纯黑底上的 `#242424` 卡片。设置类页面因此可直接使用 Miuix 卡片而不出现卡片与背景同色。

`AcbooksTheme` 以 Miuix `Colors` 作为唯一色源：先由 `appColors()` 生成 15 个语义色，再经 `toMiuixColors()` 填入 Miuix 角色，`LocalAppColors` 由同一份 `Colors` 反向还原，因此 Miuix 组件与既有组件读取的是同一组色值。映射关系：`surfaceContainer ← surface`、`surfaceVariant ← surfaceSecondary`、`onSurfaceVariantSummary ← textSecondary`、`onTertiaryContainer ← accentText`、`error ← destructive`、`outline ← controlBorder`、`dividerLine ← divider`。Miuix 未使用的 `errorContainer` / `onErrorContainer` 分别承载 `destructiveText` 与 `controlBorder`，保证往返无损。

默认主题强调色固定为 `#5D92E8`，用户可在「设置 → 外观」使用主题色预设、自定义 HEX 或恢复默认；修改后即时应用并通过 DataStore 持久化。**用户自定义的颜色原样直通**：`accent`、`accentText`、`accentBorder` 均取用户输入值，不做对比度重算，由用户自行承担可读性。按钮上的前景色（`onAccent`、`onDestructive`）仍在黑白之间选对比度更高的一个，不改动填充色。默认色 `#5D92E8` 保留手工基线 `accentText` `#285FC1` / `#8AB4FF`。`destructiveText` 与 `controlBorder` 是项目自有固定色、与用户选择无关，仍按 4.5:1 / 3:1 调整。禁止业务组件硬编码默认强调色。更改主题色不改变 Liquid Glass 材质、布局和阅读正文配色。阴影、高光、遮罩透明度统一由材质令牌管理，不在页面自行叠加。

### 2.2 形状与动效

- 使用统一连续曲率圆角（参考 Kyant0/Shapes）；嵌套圆角 = max(0, 外圆角 − 内缩)。不支持对应轮廓时回退普通圆角或无折射材质。
- 按压、拖动、导航滑块、菜单展开使用共享弹簧动效；进入/退出路径一致。文字不随玻璃拉伸，不阻塞操作。
- 尊重系统动画设置；减少动态效果时使用短淡入淡出或无动画。
- 页面转场与浮层动效遵循 [Android 动画规范](android-motion.md)。

## 3. Liquid Glass 材质

| 角色 | 应用范围 | 渲染方式 |
| --- | --- | --- |
| Content | 书籍卡片、列表、统计图、正文 | 实色表面，不捕获背景 |
| Control | 导航、按钮、开关、分段控件、输入框 | 背景采样、模糊、折射、高光 |
| Overlay | Dialog、Sheet、菜单 | 磨砂玻璃 + 可读底色，优先文字对比度 |
| TopBar | 主页面与二级页顶栏 | Haze 采样正文并做高斯模糊，叠加底色与底部渐隐 |

- 顶栏统一使用 [Haze](https://github.com/chrisbanes/haze) 的 `hazeSource` / `hazeBlur`：页面背景作为采样源，顶栏对滚动正文做高斯模糊，只叠加随主题变化的底色，不做折射。主页面、书库、设置及服务器登录等二级页都由 `GlassPageHeader` 渲染同一套模糊；阅读界面为沉浸式正文，不显示顶栏，因此不应用高斯模糊。控件与浮层继续使用 AndroidLiquidGlass / Backdrop；两类渲染各自独占层，不互相采样。优先正式 Gradle 依赖，固定兼容版本。不复制整个 Lumi 或 prism_ui，不自行重写 Shader。
- 每个页面/窗口统一管理背景捕获；玻璃不得捕获自身或形成循环引用。浮层最多三层可见功能玻璃，不叠加重复模糊；前景文字和图标保持原始分辨率。
- 阅读器仅在工具栏、菜单等玻璃浮层可见时采样正文；原生 View / WebView 通过阅读器适配层提供背景，失败时使用可读回退，不持续截图。
- 最低 API 33 且硬件加速、未启用减少透明效果时：控件与浮层使用完整折射，顶栏使用高斯模糊；不支持硬件效果、缺少背景或减少透明效果时：可读的实色回退；墨水屏禁用光学效果。降级不改变布局、形状和操作。组件预览可强制限制为模糊或实色，用于检查降级路径。
- 采样目标 1x / 2⁄3x / 1⁄2x / 1⁄3x；优先验证实际效果缓冲区缩放能力，不以虚假的画质开关代替实现。自适应降级在有真机帧耗时数据后实现。

## 4. 公共组件

| 组件 | 规范 |
| --- | --- |
| AppSurface / GlassSurface | 统一材质、形状、阴影和回退 |
| LiquidGlassButton / LiquidGlassIconButton | 胶囊文字按钮与圆形图标按钮，统一配色、尺寸、按压、加载和降级 |
| AppBackButton | 48dp 圆形玻璃返回键，复用 Fluent 箭头、按压交互和玻璃降级路径 |
| AppNavigationBar | 浮动玻璃胶囊包含首页 / 书库 / 统计，基础高度 64dp；选中滑块弹性移动 |
| AppAddButton | 独立玻璃圆形添加入口，64×64dp，与主导航并排；触发导入菜单，不参与导航选中状态 |
| AppTopBar | 返回、标题、操作；滚动时用 Haze 对正文做渐进高斯模糊（24dp），底部 24dp 渐隐 |
| AppSecondaryPage | 二级页面容器，统一滚动正文与顶栏。左侧返回，页面名称按整个顶栏居中 |
| MainTabHeader | 首页、书库、统计统一左上角 32sp Bold 大标题；首页右上角 40dp 圆形玻璃头像按钮进入设置，有头像时显示同步图片，否则显示 Fluent 人像图标 |
| AppLibraryHeader / AppLibraryTools | 书库大标题和圆形玻璃操作按钮；下方复用搜索和筛选分类胶囊 |
| AppCard / AppListItem | 普通内容材质；卡片圆角 14–18dp；列表自适应内容 |
| AppMiuixSettingsGroup / AppMiuixSettingsRow | 设置目录的 Miuix 分组：多行共用一张 `Card`，行使用 `ArrowPreference`。卡片圆角、行内边距、箭头与分隔线行为沿用 Miuix 默认（16dp 圆角、16dp 内边距、无行分隔线）；左侧图标仍用 Fluent，颜色取 `MiuixTheme.colorScheme` |
| AppMiuixCard / AppMiuixPage | 设置层级内任意内容卡与整页 Miuix 主题容器。`AppMiuixCard` 沿用 Miuix 卡片圆角与容器色，内边距由调用方决定；`AppMiuixPage` 包裹整个二级页，使其中的 `MiuixTheme.colorScheme` 与 Miuix 组件均生效 |
| AppMiuixProfileCard | 设置目录首项，Miuix `Card` 内左侧 56dp 圆形头像、右侧昵称；不显示说明、右箭头或修改昵称入口。点击头像选择图片，按圆形裁剪后本地保存并同步服务器 |
| AppTextField / AppSearchField | 标签、清除、错误和焦点统一；最小高度 48dp |
| AppSwitch / AppSegmentedControl | 统一滑块、选中及拖动状态 |
| AppMenu | 锚定触发控件；菜单项基础高度 48dp，适配长文本 |
| AppGlassSelect | 行内单选，左侧名称，右侧选择框最大宽 144dp。收起时当前值以整个选择框为基准居中，箭头靠右。展开面板与选择框等宽，当前项左侧显示勾号。设置层级改用 Miuix `OverlayDropdownPreference` |
| AppDialog / AppSheet | Alert 最大宽度 320dp，任务浮层 480dp；圆角 32 / 28dp；避让键盘和系统栏 |
| AppDialogAction | Dialog、Sheet 的统一操作入口，按语义角色选材质与前景，随文案宽度切换布局 |
| AppState / AppProgress | 空态、加载、失败、重试、下载与同步进度 |

业务页面调用设计系统组件，不直接使用 Backdrop API；组件支持 enabled、pressed、selected、loading、error 等适用状态，统一 TalkBack 语义。普通操作不得使用危险色，不用颜色作为唯一状态提示。

### 液态玻璃按钮

| 图标按钮尺寸 | 视觉直径 | 图标 | 用途 |
| --- | --- | --- | --- |
| XS | 32dp | 18dp | 卡片内部操作 |
| SM | 36dp | 17dp | 首页、书库顶部操作 |
| MD | 44dp | 20dp | 筛选、排序、弹窗图标操作 |
| LG | 48dp | 20dp | 二级和更深页面的返回、顶部操作 |
| FAB | 56dp | 24dp | 普通悬浮按钮 |
| Dock | 64dp | 28dp | 底部独立添加入口 |

文字按钮均为胶囊形。紧凑按钮最小高 32dp、字阶 14sp。普通按钮最小 72×44dp、字阶 14sp。确认、取消及弹窗操作最小 72×48dp、字阶 16sp，组内间距 8dp。共用 `ButtonSizeTokens` 和对应的文字、图标尺寸枚举。

视觉尺寸小于 48dp 时，在布局中为按钮预留至少 48×48dp 点击区，将玻璃和图标绘制在内部。相邻按钮各自占有独立布局空间，触摸区互不重叠。

书库搜索入口复用共享按钮。筛选与分类复用共享的分段按钮交互，保留同一层胶囊玻璃与中间分隔线。列表行、菜单选项、卡片入口和开关沿用各自的组件语义。

普通操作用中性玻璃，主要操作用当前 Accent 染色，危险操作用红色染色。前景按文字对比度选择。复用当前 Backdrop 的采样、模糊、折射、高光、阴影和弹性按压。硬件加速且未启用减少透明效果时使用折射，否则回退模糊或实色。缺少背景采样或启用降级时使用对应实色及 Compose 弹簧按压。加载保留按钮尺寸并阻止重复点击。

### 弹窗操作按钮

通过 `AppDialog` 或 `AppSheet` 的 `actions` 提供 `AppDialogAction`。页面提供本地化文案、角色、可用状态和回调，样式由设计系统统一。

| 角色 | 背景 | 前景 |
| --- | --- | --- |
| Primary（确定、保存、添加） | Accent 染色玻璃 | OnAccent |
| Secondary、Cancel | 中性玻璃 | TextPrimary |
| Destructive（删除、清空） | Destructive 染色玻璃 | OnDestructive |

- 完全胶囊形，最小 72×48dp，横向内边距 12dp，按钮间距 8dp，文字 16sp SemiBold，禁用态整体透明度 48%。默认 Accent 为 `#5D92E8`，随当前主题色与明暗模式变化。
- 两个按钮在文案和最小尺寸均可容纳时等宽横排，取消在左，确认或危险操作在右。超过两个按钮或任一文案无法单行容纳时改为全宽纵排，主要或危险操作在上，取消在下，长文案可换行。
- 复用 Backdrop 按钮和现有按压动效。按钮采样页面背景，遵循玻璃层数上限，并按启用态文字对比提高染色不透明度。缺少硬件或背景时沿用实色回退。

### 图标

- Android 统一使用 Fluent UI System Icons，由 Version Catalog 管理，开源依赖与 MIT 许可证集中记录于[仓库根目录](../../LICENSE.md#fluent-ui-system-icons)。
- 业务页面通过设计系统的 `AppIcons` 取语义图标，通过公共控件或 `ControlIcon` 渲染。第三方图标导入仅限设计系统的图标映射。
- 首页、书库、统计导航使用 Filled，选中项使用主题强调色，普通操作使用 Regular。控件中的 Regular 轮廓统一加粗，Filled 保留原字形。
- 收藏和书签通过 `IconPair` 随状态切换 Regular / Filled，并提供当前可执行操作的本地化描述。有文字标签的导航图标使用空描述，由标签提供可访问名称。常规图标 24dp，小图标 18dp。

## 5. 页面布局与导航

页面结构与交互详见 [android-ui-pages.md](android-ui-pages.md)，本文仅规定通用视觉规则。

- 主导航为首页 / 书库 / 统计 + 独立添加按钮；首页为默认入口，设置由首页头像进入。
- 设置首页仅展示已接通的详情入口。修改控件放在对应的详情页，通过圆形返回键或系统返回回到上一层。
- 深色模式等单选设置使用 Miuix `OverlayDropdownPreference`：行内左侧标题、右侧当前值与上下箭头，点击后在锚定面板中选择，当前项带勾号。面板由 `AppMiuixPage` 内的 Miuix `Scaffold` 承载。设置层级以外的行内单选仍使用 `AppGlassSelect`。选择条目、点击外部或返回时关闭。
- 设置目录首项为头像卡片，只显示头像与昵称；未设置昵称时显示本地化的「本地读者」。昵称编辑暂不提供，后续以独立弹窗接入，不再使用三级页面。点击 56dp 头像打开系统照片选择器，图片按 512×512 居中裁剪并压缩；未登录时保存本地并标记待同步，登录后自动上传；服务器已有头像且本地无待同步修改时下载覆盖本地。
- 三个主页面共享 `MainTabHeader`，大标题左对齐并避开系统状态栏。首页头像使用 40dp 圆形玻璃按钮和至少 48dp 触控区，图片内缩 2dp 保留玻璃描边，作为设置入口。手机使用浮动底栏，宽屏使用侧栏。使用 edge-to-edge 与 WindowInsets，滚动内容可经过悬浮导航下方，最后一项可完整滚出遮挡区。
- 二级页面使用 `AppSecondaryPage`。顶栏与主页面共用安全区下 12dp 顶部留白、24dp 左右边距和 48dp 最小行高。左侧使用共享圆形玻璃 `AppBackButton`。页面名使用 20sp SemiBold，以整个顶栏的宽度为基准居中，标题两侧预留相同空间。
- 主页面与二级页共用滚动玻璃材质，由 `GlassPageHeader` 统一渲染。滚动位置为零时整条顶栏透明；正文滚到顶栏下方时，前 24dp 的滚动距离把 Haze 高斯模糊从 0 增加到 24dp，并叠加随深浅主题变化的 0.38 不透明度底色，不做折射。模糊层在底部跨越顶栏边缘的 24dp 范围内逐渐淡至透明。顶栏只采样正文，标题和操作始终位于前景，渐隐区不计入顶栏布局和触摸范围。内容不足一屏、无法滚动的页面（如服务器登录）不会出现可采样的正文，顶栏保持透明；阅读界面不显示顶栏。
- 业务页面统一使用设计系统组件，书籍与阅读正文保持内容优先；大字号及英文长文本允许换行，不压缩触控区域。

## 6. 中英文国际化

- 跟随系统（默认）、简体中文 `zh-CN`、英文 `en`；其他系统语言回退英文。
- 每个模块使用 `res/values/strings.xml`（英文默认）和 `res/values-zh-rCN/strings.xml`（中文），通过 `stringResource` / `pluralStringResource` 获取；AppCompat/平台应用语言 API 持久化语言选择。
- 时间、数字、时长按当前 locale 格式化；服务端错误码映射本地文案；书名、作者、笔记等用户内容不自动翻译。每次新增文案必须同步维护两种语言。

## 7. 工程目录

```text
core/designsystem/src/main/
├── kotlin/.../designsystem/
│   ├── tokens/       # Colors、Typography、Spacing、Radius、Sizes、Motion、GlassTokens
│   ├── theme/        # AcbooksTheme（唯一主题入口）、AppShapes、ReaderTheme
│   ├── glass/        # Backdrop、Haze、Surface、Capability、Sampling、Motion
│   ├── components/   # App* 公共组件
│   └── icons/        # 统一图标入口
└── res/              # 公共组件中英文资源
```

设计系统只依赖 UI 基础库，不依赖数据库、网络或 feature；业务模块依赖设计系统。主题偏好用 DataStore，阅读偏好与 App 外观分离。

## 8. 实现与验收

1. 先实现令牌、主题、连续圆角、玻璃能力检测与公共组件预览，再开发业务页面。
2. 真机验证浅/深色、主题色切换/恢复及重启持久化、两种语言、大字号、横竖屏、API 33 及不支持玻璃的设备；检查顶栏高斯模糊在主页面和二级页滚动时是否只采样正文、底部渐隐是否正确，以及菜单、导航、阅读浮层的清晰度与帧耗时；验证三个页面 Tab 与独立添加按钮的选中状态、菜单取消和导入返回行为；验证头像选择、圆形裁剪、40dp 玻璃描边、未登录本地保存及登录后与 Web 双向同步。
3. 不增加其他外观主题，不复制业务页面，不让视觉效果阻塞本地阅读；新增或升级依赖时，在[仓库根目录的 LICENSE.md](../../LICENSE.md#android-开源依赖)统一更新开源依赖说明及适用的版权与许可证文本。

## 9. 开源参考

- [Lumi Books 当前 UI 与动效规范](https://github.com/huangder/Lumi_Books/blob/66c6d9df36db3b96d73af634bcd34835edff9fb7/devdocs/lumi-ui-motion-design-system.md)：令牌、材质分层、形状、阅读浮层与动效。
- [Lumi AppTokens.kt](https://github.com/huangder/Lumi_Books/blob/66c6d9df36db3b96d73af634bcd34835edff9fb7/app/src/main/java/com/huangder/lumibooks/ui/theme/AppTokens.kt) / [AppPresentation.kt](https://github.com/huangder/Lumi_Books/blob/66c6d9df36db3b96d73af634bcd34835edff9fb7/app/src/main/java/com/huangder/lumibooks/ui/theme/AppPresentation.kt)：实际设计参数。
- [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)、[Shapes](https://github.com/Kyant0/Shapes)：控件与浮层的背景采样、折射与连续圆角。
- [Haze](https://github.com/chrisbanes/haze)：顶栏对滚动正文的高斯模糊。
- [Miuix](https://github.com/compose-miuix-ui/miuix)：MIUI 风格的 Compose 组件库，`miuix-ui-android`、`miuix-preference-android`、`miuix-icons-android`、`miuix-blur-android`、`miuix-squircle-android`、`miuix-nav-android` 由 Version Catalog 统一管理。Miuix 的 `Colors` 是整套 App 的唯一色源，由 `AcbooksTheme` 从 acbooks 语义色生成；当前设置层级 5 个页面使用其 `Card`、`ArrowPreference` 与 `OverlayDropdownPreference`。不改写 Miuix 的圆角与间距默认值。
- [FlClash](https://github.com/chen08209/FlClash)、[prism_ui](https://github.com/chen08209/prism_ui)：组件封装、弹性导航及捕获优化；只参考设计，不引入 Flutter 依赖。
