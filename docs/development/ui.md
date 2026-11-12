# acbooks — UI 设计规范

> 目标路径：`docs/development/ui.md`  
> 范围：Web / Windows 共享 UI；Android 参考视觉令牌，使用原生 Compose 实现。  
> 本文仅定义界面与交互，不改变系统架构、业务模型、认证、API 或测试规范。

## 1. 执行原则

- 内容优先：参考 Readest 的克制、Lumi 的圆角、Radix 的色阶、Mantine 的按钮、Tome 的布局和 Stump 的组件组织。
- 柔和实色、清晰层级、适度留白、轻边框。**不引入毛玻璃、液态玻璃、背景模糊、折射或相关依赖。**
- 普通按钮禁止黑色、白色反相或高饱和强调色实心填充；只有危险操作的最终确认允许红色实心。
- 修改前阅读 `AGENTS.md`、`system.md`、`standards.md` 及任务相关专项规范。冲突先报告，不自行覆盖权威规则。
- Web 保持 React + TypeScript strict + Vite；采用 Tailwind CSS、shadcn/ui、Radix Primitives、Lucide React。Radix Colors 用于默认色阶；实现自定义配色时按需评估 Culori。不得重复引入 Mantine、Radix Themes、DaisyUI 等完整 UI 体系。新增依赖核对 GPL-3.0-only 兼容性。

## 2. 主题与颜色

- 外观模式：跟随系统（默认）、浅色、深色；**唯一内置配色为淡蓝 `#5D92E8`**，不提供其他预设配色。保留自定义主题能力，用户可自行修改配色；模式与配色独立，书库与阅读器主题独立。
- 品牌种子色 `#5D92E8`；它不等于可读的强调文字色。默认配色包含浅、深两套色阶；自定义主题支持背景、文字和强调色，派生色统一生成并验证对比度。
- 所有组件只消费 CSS 语义令牌，不直接引用具体调色板或硬编码色值。将 shadcn/ui 默认变量映射到同一体系，避免第二套主题。以根节点主题属性和 `color-scheme` 统一切换，弹窗、菜单等 Portal 同步继承。
- 主题偏好可保存在本地，不持久化认证凭据；首屏避免主题闪烁，系统模式响应系统变化。不新增后端主题接口或数据表作为前置条件。

| 语义令牌（CSS 变量） | 默认浅色 | 默认深色 | 用途 |
| --- | --- | --- | --- |
| `--frame` | `#EDF1F6` | `#10151E` | 应用框架、侧栏 |
| `--background` | `#FFFFFF` | `#171B23` | 主内容 |
| `--surface` | `#F5F7FA` | `#202631` | 次级容器 |
| `--foreground` | `#172033` | `#E8EDF5` | 主要文字 |
| `--muted-foreground` | `#536277` | `#A3AEC0` | 次要文字 |
| `--border` | `#D4DDE9` | `#344052` | 装饰边界、分割线 |
| `--accent-text` | `#285FC1` | `#8AB4F8` | 强调文字、图标 |
| `--accent-soft` | `#EAF2FF` | `#243A58` | 淡色强调、选中 |
| `--destructive` | `#B42318` | `#B42318` | 危险确认背景 |
| `--destructive-text` | `#B42318` | `#FF9C96` | 危险文字、图标 |

派生规则参考 Radix 的 12 色阶：1–2 背景，3–5 交互表面，6–8 边界，9–10 强强调，11–12 文字。另设 `--surface-hover`、`--surface-active`、`--accent-hover`、`--accent-active`、`--control-border`、`--ring`、`--success`、`--warning`、`--destructive-foreground`（默认白）。颜色按用途映射；不把所有色阶都暴露给页面。默认及自定义主题必须满足正文 4.5:1、大字和必要控件边界/焦点 3:1 的对比度要求。错误色独立于主题色，状态不得只依靠颜色表达。

## 3. 设计令牌与尺寸

以下为 Web CSS px 基准；实现优先使用可缩放的 rem 令牌。Android 使用对应 dp/sp，并遵循原生无障碍要求。

| 项目 | 规范 |
| --- | --- |
| 间距 | 4 / 8 / 12 / 16 / 24 / 32 / 48 |
| 圆角 | 标签/复选框 4；封面 8；按钮/输入框 10；菜单 12；卡片 14；大卡片 18；主面板 22；弹窗 24；胶囊 full |
| 字号 | 页标题 28/600；区块 20/600；弹窗 18/600；正文 14/400；操作及书名 14/500；次要信息 13；辅助 12；移动正文 16 |
| 控件 | 小按钮 32；标准 36；大按钮 44；输入框 40；登录输入框 44；图标按钮 36×36；图标 18，导航图标 20 |
| 表格/设置 | 表格行 48（紧凑 40）；设置行最小 56，允许随内容增高 |
| 阴影 | 普通卡片无阴影；封面、浮层可用轻微阴影 |

系统字体、正文行高 1.5、统计数字等宽数字。阅读排版独立。嵌套同心圆角采用 `max(0, 外圆角 - 内缩距离)`，仅用于同心轮廓。不要把普通卡片和输入框全部做成胶囊。

## 4. 组件规范

| Button 变体 | 外观 | 用途 |
| --- | --- | --- |
| `soft` | 极浅强调色背景、强调色文字 | 主要操作（添加、导入、登录） |
| `default` | 柔和中性表面、主要文字 | 保存、确认、应用 |
| `outline` | 透明背景、细边框 | 普通次要操作 |
| `ghost` | 透明、无边框 | 取消、编辑、工具栏 |
| `danger` | 透明、红色文字/图标 | 删除入口 |
| `danger-confirm` | 红色实心、高对比文字 | 危险操作最终确认 |

- `soft` 只允许接近背景的淡色；不得退化为高饱和实心。覆盖 shadcn 的默认实心按钮，不提供普通 `solid` 变体。每个操作区最多一个视觉主操作。
- Hover / Active 仅改变背景、文字或边框色阶；禁用不可交互；Loading 保留尺寸，防重复提交；键盘焦点始终可见。移动触控目标至少 44×44，桌面目标满足 WCAG 2.2 AA。
- 分段选择器使用中性胶囊轨道和中性选中色块，不用主题色实心选中。独立图标按钮有可访问名称；开关表示布尔设置；表单有标签、错误提示和必要说明。
- 设置采用 `SettingsGroup` + `SettingsRow`：标题、可选说明、尾部控件；同组共享边界、行间细分割线，尾部对齐，不叠加重复容器。弹窗、菜单、抽屉支持键盘、焦点管理和关闭后焦点恢复。
- 公共组件按真实需求实现：Button、IconButton、Input、Dialog、Select、Tabs、Switch、BookCover、BookCard、PageHeader、EmptyState 等；不预建空模块。

## 5. 页面布局

- 桌面：240px 左侧栏（折叠 64px）、56px 顶栏、独立滚动的主内容面板；面板外留白 8px，内容内边距 24px，区块间距 32px。侧栏项高 40px、间距 4px。侧栏选中采用淡色背景+强调文字。
- 响应式：≥1024px 完整侧栏；768–1023px 图标侧栏；<768px 抽屉导航，主内容全宽、去外边距及大面板圆角，页面内边距 16px。使用可用视口高度，避免双重页面滚动和横向溢出。
- 书库：**统一 Book 列表**，实体/电子属性仅作为筛选和展示，不拆成两套数据或书库。网格/列表复用相同数据；封面容器 2:3、等比缩放、默认宽度 144–180px、网格间距 24px，列数自适应；窄屏可缩小封面。书名最多两行，作者一行，封面缺失有占位。
- 页面标题与简短说明明确；书库提供搜索、筛选、排序、分页及必要批量操作；不为每个区块套卡片。初始化与登录共用居中表单布局，最大宽度 400px，初始化成功后单独登录，未认证不显示私人书库。
- 列表、详情及表单覆盖加载、空、错误、重试、成功与禁用状态。异步任务展示服务端实际状态，不将受理当成功。

## 6. 动效、工程与验收

- 默认 `150ms ease-out` 颜色过渡；主题切换 180ms，弹窗 200ms，侧栏 220ms，抽屉 240ms。禁止普通 Hover 缩放、弹跳及全局 `transition-all`。尊重 `prefers-reduced-motion`，保留必要状态反馈。
- `packages/acbooks-ui/` 放共享令牌、主题和可复用组件；`apps/acbooks-web/` 放页面及业务交互；Windows 按架构复用 UI。只按实际复用需要建目录，业务规则留在后端，生成的 API 客户端不得手改。
- UI 认证、CSRF、会话、限流、错误码、分页和文件操作遵循 `security.md`、`api/conventions.md` 与相应架构规范。按真实 OpenAPI 接入，未实现的接口不得用假成功代替；429 有 `Retry-After` 时按服务端提示处理。
- 交付按 `standards.md` 与 `docs/testing/strategy.md` 执行。UI 验收至少检查默认配色的浅/深色、已实现的自定义配色、移动/桌面、键盘焦点、加载/失败/禁用、无溢出和减弱动态；认证及数据安全按对应专项测试。使用现有检查脚本，不声称未执行的测试通过。

## 参考（借鉴机制，不复制实现）

- [Readest DESIGN.md](https://github.com/readest/readest/blob/main/apps/readest-app/DESIGN.md) / [主题实现](https://github.com/readest/readest/blob/main/apps/readest-app/src/styles/themes.ts)
- [Lumi UI 规范](https://github.com/huangder/Lumi_Books/blob/main/devdocs/lumi-ui-motion-design-system.md)（不采用其玻璃及复杂形变）
- [Radix Colors 色阶](https://www.radix-ui.com/colors/docs/palette-composition/understanding-the-scale) / [Mantine 按钮](https://mantine.dev/core/button/)
- [Stump 共享组件](https://github.com/stumpapp/stump/tree/main/packages/components) / [Tome ShellFrame](https://github.com/bndct-devops/tome/blob/main/frontend/src/components/ShellFrame.tsx)
