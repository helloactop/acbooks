# acbooks Android UI 设计规范

适用于 `apps/acbooks-android/` 的所有应用自绘界面，包括首页、书库、统计、书籍详情、设置、导入、导航、阅读器控件、弹窗和其他浮层。本文规定迁移目标，当前实现见 [Android 开发环境](../development/android.md)。

## 1. 设计原则

- Android 统一使用 [Miuix](https://github.com/compose-miuix-ui/miuix)，直接使用组件库提供的组件和默认样式。
- 保留跟随系统、浅色和深色三种外观模式，由 `MiuixTheme` 统一提供主题。首页仍是默认入口，本地导入和阅读无需登录。
- 支持简体中文和英文，适配手机、横屏和平板，兼容系统字体缩放和 TalkBack。

## 2. 主题与样式

- 颜色、字阶、圆角、控件内边距、间距和组件动效沿用 Miuix 默认值。需要读取颜色或字阶时，直接使用 `MiuixTheme` 提供的值。
- 禁止建立 acbooks 自有的设计令牌、色板及颜色映射层。已有用户自定义颜色作为 Miuix 动态主题的种子色。
- 业务布局可用 Compose Foundation 组织内容和适配窗口。封面比例、网格列数、正文排版和系统安全区属于内容或布局规则。
- 液态玻璃效果直接使用官方 `miuix-glass` 和 `miuix-blur`，沿用组件库的材质和交互反馈。

## 3. 公共组件

| 用途 | 实现规则 |
| --- | --- |
| 应用框架、顶栏、导航 | 直接使用 Miuix 的页面容器、顶栏与导航组件。`miuix-nav` 管理路由，`HorizontalPager` 管理主 Tab |
| 按钮、表单、搜索和选择 | 直接使用 Miuix 对应的按钮、输入、选择控件。加载时防止重复提交，错误与禁用状态须明确可见 |
| 卡片与设置 | 使用 Miuix `Card` 和 `ArrowPreference` 等组件。设置页面的玻璃组件见下文 |
| 弹窗、底部浮层和菜单 | 直接使用 Miuix 的对话框、底部浮层和菜单组件。保留确认、取消、危险操作及返回语义 |
| 反馈 | 直接使用 Miuix 的进度和反馈组件，业务逻辑提供真实的加载、空、失败、重试及完成状态 |

页面直接调用 Miuix。需要组织书籍、统计或阅读等领域内容时，可以组合现有控件与 Compose Foundation 布局。直接使用库的组件名称和默认外观。若锁定版本缺少所需组件，先说明缺口并由用户决定处理方案。

确认和取消须有本地化文字。危险操作使用 Miuix 的错误语义色，状态同时用文字或图标表达。长文案允许换行，交互控件的触控区域至少为 48×48dp。

### 设置页面

- 设置首页、外观、服务、服务器状态和登录使用可折叠的 `GlassTopAppBar`。设置首页的返回按钮使用默认 44dp 的 `GlassIconButton`。
- 设置列表使用 `LazyColumn` 和 `SmallTitle`。每组设置项以 `squircleSurface` 拼成一张连续卡片，使用主题的 `surfaceContainer` 和 `onSurfaceContainer`，外侧圆角 16dp，左右边距 12dp，组底间距 6dp。
- 普通设置入口使用 `ArrowPreference`，开关使用 `SwitchPreference`，下拉使用 `GlassDropdownPreference`。服务器表单使用 `GlassTextField`。应用内对话框使用 `GlassDialog` 和 `GlassDialogButton`，底部浮层使用 `GlassBottomSheet`。
- 设置页用一个 `rememberLayerBackdrop` 采样底色和列表。顶栏与下拉菜单共用该采样，浮层宿主与顶栏位于采样区域之外。滚动内容可经过顶栏下方。
- 浅色和深色均从当前 Miuix 主题取色。收起下拉浮层再返回上一级，保留主题、语言、服务器登录和头像同步的行为。

### 图标

- 功能图标统一直接使用官方 `miuix-glass-icons-android` 的 `MiuixIcons.Glass`。同一语义在各 Android 页面使用同一图标。
- 纯图标按钮提供中英文可访问名称。有可见文字的装饰图标由文字提供语义。收藏与书签切换时更新当前可执行操作的名称。

## 4. 页面、导航与阅读器

- 页面结构与操作见 [Android 页面规范](android-ui-pages.md)，路由和浮层的返回行为见 [Android 动画规范](android-motion.md)。
- 手机使用底部主导航，宽屏使用侧栏。首页、书库、统计保留各自的页面状态。搜索按钮打开 Miuix 玻璃搜索浮层。
- 阅读器由现有引擎渲染 EPUB/PDF 正文。应用提供的翻页、目录、进度、书签、笔记和阅读设置等可见控件使用 Miuix。正文纸色和排版沿用独立的阅读偏好。
- 系统文件或照片选择器、权限对话框等由 Android 系统呈现。应用布局使用 edge-to-edge 和 WindowInsets，控件避让系统栏与键盘。

## 5. 中英文国际化

- 跟随系统（默认）、简体中文 `zh-CN`、英文 `en`。其他系统语言回退英文。
- 每个模块使用 `res/values/strings.xml` 和 `res/values-zh-rCN/strings.xml`，通过 `stringResource` / `pluralStringResource` 获取文案。新增文案同时维护两种语言。
- 时间、数字、时长按当前 locale 格式化。服务端错误码映射本地文案，用户内容保留原文。

## 6. 迁移与验收

1. 接入 Miuix 默认主题，优先迁移导航、顶栏、按钮、表单、弹窗与菜单。按页面迁移首页、书库、统计、书籍详情、设置、导入与阅读器，移除已无调用的旧组件、令牌和视觉依赖。
2. 验证浅色、深色、跟随系统、重启后偏好恢复、中英文、字体缩放、TalkBack、横竖屏和 API 33。使用组件预览核对各控件状态。
3. 验证 Tab 切换和状态保留、系统返回、弹窗关闭、导入取消、账号头像和登录状态。验证 EPUB/PDF 的沉浸阅读、翻页、目录、书签和阅读设置。
4. 构建与设备验证见 [Android 基础测试](../testing/android.md)。新增、升级或移除依赖时，按实际变更更新 [开源依赖说明](../../LICENSE.md#android-开源依赖)。
