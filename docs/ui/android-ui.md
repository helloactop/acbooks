# acbooks Android UI 设计规范

> 建议路径：`docs/ui/android-ui.md`<br>
> 工程：`apps/acbooks-android/`<br>
> 上位规范：`docs/architecture/android-reader.md`

## 1. 设计原则

- 阅读优先：简洁、适中信息密度、内容清晰；玻璃只用于控件和浮层，不覆盖所有内容。
- 唯一外观为 **Liquid Glass**，支持浅色、深色、跟随系统。无 Material 3 / 普通主题切换；Material 3 可作为 Compose 基础能力，不使用其默认视觉风格。
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
| 玻璃 | 默认透明度 0.55；控件回退模糊 8dp、浮层 12dp；默认采样 0.5x（待验证） |

### 2.1 语义颜色

| Token | Light | Dark |
| --- | --- | --- |
| background | `#FBFBFC` | `#000000` |
| surface | `#FFFFFF` | `#1C1C1E` |
| surfaceSecondary | `#F2F2F7` | `#2C2C2E` |
| textPrimary | `#000000` | `#FFFFFF` |
| textSecondary | `#6E6E73` | `#98989D` |
| divider | `#E5E5EA` | `#38383A` |
| accent | `#5D92E8` | `#5D92E8` |
| accentText | `#285FC1` | `#8AB4FF` |
| destructive | `#D92D3A` | `#FF6961` |

默认主题强调色固定为 `#5D92E8`，用户可在「设置 → 外观 → 主题色」选择预设色、自定义 HEX 颜色或恢复默认；修改后即时应用并通过 DataStore 持久化。表中 accentText 为默认色对应的基线，修改主题色后统一重新生成 accentText、accentSoft、onAccent、选中态等语义颜色，适配浅色/深色并校验对比度（普通文字至少 4.5:1，关键控件边界至少 3:1）。禁止业务组件硬编码默认强调色。更改主题色不改变 Liquid Glass 材质、布局和阅读正文配色。阴影、高光、遮罩透明度统一由材质令牌管理，不在页面自行叠加。

### 2.2 形状与动效

- 使用统一连续曲率圆角（参考 Kyant0/Shapes）；嵌套圆角 = max(0, 外圆角 − 内缩)。不支持对应轮廓时回退普通圆角或无折射材质。
- 按压、拖动、导航滑块、菜单展开使用共享弹簧动效；进入/退出路径一致。文字不随玻璃拉伸，不阻塞操作。
- 尊重系统动画设置；减少动态效果时使用短淡入淡出或无动画。

## 3. Liquid Glass 材质

| 角色 | 应用范围 | 渲染方式 |
| --- | --- | --- |
| Content | 书籍卡片、列表、统计图、正文 | 实色表面，不捕获背景 |
| Control | 导航、按钮、开关、分段控件、输入框 | 背景采样、模糊、折射、高光 |
| Overlay | Dialog、Sheet、菜单 | 磨砂玻璃 + 可读底色，优先文字对比度 |

- 底层采用 AndroidLiquidGlass / Backdrop；优先正式 Gradle 依赖，固定兼容版本。不复制整个 Lumi 或 prism_ui，不自行重写 Shader。公开 API 无法满足采样优化时再维护源码补丁。
- 每个页面/窗口统一管理背景捕获；玻璃不得捕获自身或形成循环引用。浮层最多三层可见功能玻璃，不叠加重复模糊；前景文字和图标保持原始分辨率。
- 阅读器仅在工具栏、菜单等玻璃浮层可见时采样正文；原生 View / WebView 通过阅读器适配层提供背景，失败时使用可读回退，不持续截图。
- API 33+ 且能力检测通过：完整折射；API 31–32：模糊回退；不支持硬件效果、缺少背景或减少透明效果时：可读的实色回退；墨水屏禁用光学效果。降级不改变布局、形状和操作。
- 采样目标 1x / 2⁄3x / 1⁄2x / 1⁄3x；优先验证实际效果缓冲区缩放能力，不以虚假的画质开关代替实现。自适应降级在有真机帧耗时数据后实现。

## 4. 公共组件

| 组件 | 规范 |
| --- | --- |
| AppSurface / GlassSurface | 统一材质、形状、阴影和回退 |
| AppButton / AppIconButton | 统一主次、危险、禁用、加载和按压态；触控区域 ≥48dp |
| AppNavigationBar | 浮动玻璃胶囊包含首页 / 书库 / 统计，基础高度 64dp；选中滑块弹性移动 |
| AppAddButton | 独立玻璃圆形添加入口，64×64dp，与主导航并排；触发导入菜单，不参与导航选中状态 |
| AppTopBar | 返回、标题、操作；滚动时可渐进模糊 |
| AppCard / AppListItem | 普通内容材质；卡片圆角 14–18dp；列表自适应内容 |
| AppTextField / AppSearchField | 标签、清除、错误和焦点统一；最小高度 48dp |
| AppSwitch / AppSegmentedControl | 统一滑块、选中及拖动状态 |
| AppMenu | 锚定触发控件；菜单项基础高度 48dp，适配长文本 |
| AppDialog / AppSheet | Alert 最大宽度 320dp，任务浮层 480dp；圆角 32 / 28dp；避让键盘和系统栏 |
| AppState / AppProgress | 空态、加载、失败、重试、下载与同步进度 |

业务页面调用 `App*` 组件，不直接使用 Backdrop API；组件支持 enabled、pressed、selected、loading、error 等适用状态，统一 TalkBack 语义。普通操作不得使用危险色，不用颜色作为唯一状态提示。

### 图标

- Android 统一使用 Fluent UI System Icons，由 Version Catalog 管理，开源依赖与 MIT 许可证集中记录于[仓库根目录](../../LICENSE.md#fluent-ui-system-icons)。
- 业务页面通过设计系统的 `AppIcons` 取语义图标，通过公共控件或 `ControlIcon` 渲染。第三方图标导入仅限设计系统的图标映射。
- 首页、书库、统计导航使用 Filled，选中项使用主题强调色，普通操作使用 Regular。控件中的 Regular 轮廓统一加粗，Filled 保留原字形。
- 收藏和书签通过 `IconPair` 随状态切换 Regular / Filled，并提供当前可执行操作的本地化描述。有文字标签的导航图标使用空描述，由标签提供可访问名称。常规图标 24dp，小图标 18dp。

## 5. 页面布局与导航

页面结构与交互详见 [android-ui-pages.md](android-ui-pages.md)，本文仅规定通用视觉规则。

- 主导航为首页 / 书库 / 统计 + 独立添加按钮；首页为默认入口，设置由首页头像进入。
- 三个主页面共享 `MainTabHeader`；手机使用浮动底栏，宽屏使用侧栏。使用 edge-to-edge 与 WindowInsets；滚动内容可经过悬浮导航下方，但最后一项必须能滚出遮挡区。
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
│   ├── theme/        # AcbooksTheme、AppShapes、ReaderTheme
│   ├── glass/        # Backdrop、Surface、Capability、Sampling、Motion
│   ├── components/   # App* 公共组件
│   └── icons/        # 统一图标入口
└── res/              # 公共组件中英文资源
```

设计系统只依赖 UI 基础库，不依赖数据库、网络或 feature；业务模块依赖设计系统。主题偏好用 DataStore，阅读偏好与 App 外观分离。

## 8. 实现与验收

1. 先实现令牌、主题、连续圆角、玻璃能力检测与公共组件预览，再开发业务页面。
2. 真机验证浅/深色、主题色切换/恢复及重启持久化、两种语言、大字号、横竖屏、API 31/33+ 及不支持玻璃的设备；检查滚动、菜单、导航、阅读浮层的清晰度与帧耗时；验证三个页面 Tab 与独立添加按钮的选中状态、菜单取消和导入返回行为。
3. 不增加其他外观主题，不复制业务页面，不让视觉效果阻塞本地阅读；新增或升级依赖时，在[仓库根目录的 LICENSE.md](../../LICENSE.md#android-开源依赖)统一更新开源依赖说明及适用的版权与许可证文本。

## 9. 开源参考

- [Lumi Books 当前 UI 与动效规范](https://github.com/huangder/Lumi_Books/blob/66c6d9df36db3b96d73af634bcd34835edff9fb7/devdocs/lumi-ui-motion-design-system.md)：令牌、材质分层、形状、阅读浮层与动效。
- [Lumi AppTokens.kt](https://github.com/huangder/Lumi_Books/blob/66c6d9df36db3b96d73af634bcd34835edff9fb7/app/src/main/java/com/huangder/lumibooks/ui/theme/AppTokens.kt) / [AppPresentation.kt](https://github.com/huangder/Lumi_Books/blob/66c6d9df36db3b96d73af634bcd34835edff9fb7/app/src/main/java/com/huangder/lumibooks/ui/theme/AppPresentation.kt)：实际设计参数。
- [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)、[Shapes](https://github.com/Kyant0/Shapes)：渲染与连续圆角。
- [FlClash](https://github.com/chen08209/FlClash)、[prism_ui](https://github.com/chen08209/prism_ui)：组件封装、弹性导航及捕获优化；只参考设计，不引入 Flutter 依赖。
