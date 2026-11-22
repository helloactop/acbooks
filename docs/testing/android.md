# Android 基础测试

从 `apps/acbooks-android/` 运行。Windows PowerShell 上将 `bash ./gradlew` 换成 `.\gradlew.bat`。

```sh
bash ./gradlew :app:assembleDebug :app:lintDebug
```

将安装包安装到 API 33 或更新的设备。启动后确认进入首页，底栏可点击或横滑切换首页、书库和统计，标题无遮挡。切换系统浅色和深色，确认应用跟随系统。构建和安装步骤见 [Android 开发环境](../development/android.md)。

从首页点击或拖动底栏到统计，确认正文直接切到统计，书库不会短暂选中。从统计使用系统返回，确认直接回到首页。确认相邻 Tab 点击仍有弹簧过渡，横滑松手后底栏选中项和停靠页面一致，拖回原页时选中项回到原页。

分别从书库和统计打开玻璃搜索浮层。确认输入、清除和键盘提交可用，旋转后查询仍在。使用取消按钮和系统返回关闭，确认恢复原 Tab。连续打开和关闭，检查焦点及键盘状态。取消边缘返回手势时应保留浮层。

从首页的头像打开设置，验证官方页面转场和顶栏返回。用系统边缘手势分别取消和提交返回，检查设置页和首页的状态。启用系统的「不保留活动」选项后，从后台恢复时确认设置页与主 Tab 状态可恢复。

## 旧实现的历史验收

以下记录对应重建前的实现。

基于 `ffe9310` 的 Android 初始化工作区在 Windows、JDK 21 和 Android 16（API 36）模拟器上通过当时的构建、Lint 和测试。DataStore JVM 测试 2 项、设备测试 4 项全部通过。Lint 无错误，保留依赖更新、目标 API 和启动图标等警告。低版本设备与实体设备尚未验证。

公共设计系统在同一环境通过 Debug、未签名 Release 构建、Lint、5 项 JVM 测试与 5 项设备测试。能力分级由单元测试覆盖，模糊和实色路径在 API 36 预览中验证。墨水屏及渲染帧耗时尚未验证。

迁移 Fluent UI System Icons 1.0.1 后，使用 JDK 21 通过 Debug、未签名 Release 构建、Lint、8 项 JVM 测试及 API 36 模拟器上的 5 项仪器测试。图标测试覆盖 Filled 导航、Regular 控件描边及收藏和书签变体，组件预览验证中英文可访问名称随状态变化。

Navigation Compose 2.10.2 接入后，Debug 构建、应用和设计系统 Lint、8 项设计系统 JVM 测试通过。API 36 模拟器上的两项导航仪器测试通过，覆盖左右边缘返回、手势取消、Activity 重建后恢复外观页、连续导航和 Sheet 关闭后保留当前 Tab。APK 清单确认最低 API 33，API 33–34 设备运行暂未验证。

账号头像与 40dp 玻璃入口：Debug 编译、DataStore JVM 测试通过；API 36 模拟器验证照片选择、512×512 裁剪、本地保存、设置卡片与首页 40dp 玻璃头像显示，以及无服务器时的待同步状态。服务器头像上传、`avatarUpdatedAt` 修订和设备 Bearer 下载同一 512×512 文件由后端 pytest 与 Web 登录实测覆盖；Android 端到端上传需要受信任的 HTTPS 服务器，本轮未验证。

引入 Miuix 0.9.4 并将最低版本提升到 API 33 后，Debug APK 构建、应用和设计系统 Lint、设计系统 JVM 测试通过，APK 清单确认 `minSdkVersion 33`。Miuix 六个模块（UI、Preference、Icons、Blur、Squircle、Nav）已在 `:core:designsystem` 完成依赖解析与编译；API 33 实体设备暂未验证。

主题已交给 Miuix：`AcbooksTheme` 产出唯一的 Miuix `Colors`，`LocalAppColors` 由它反向还原，测试覆盖 15 个语义色的双向往返（含 White/Black/Yellow 等自定义色）。用户自定义主题色原样直通，`accent`/`accentText`/`accentBorder` 不做对比度重算；默认色保留手工基线；`destructiveText` 与 `controlBorder` 仍按 4.5:1 / 3:1 调整。Debug APK 构建、设计系统 JVM 测试、应用 Lint 通过；API 36 模拟器确认首页、设置目录、服务器页在浅色与深色下的背景、卡片、玻璃按钮与导航外观无回归。全局 `MiuixTheme` 带来的滚动回弹与禁用态水波纹观感尚未逐项确认。
