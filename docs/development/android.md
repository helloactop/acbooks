# Android 开发环境

Android 工程位于 `apps/acbooks-android/`，在 Android Studio 中直接打开该目录。命令行构建使用仓库内的 Gradle Wrapper。

- 使用 JDK 17 或 21，将 `JAVA_HOME` 或 Android Studio 的 Gradle JDK 指向该 JDK。Java 和 Kotlin 字节码目标均为 17。
- 通过 SDK Manager 安装 Android API 37 及 Android SDK Platform-Tools。将 `ANDROID_HOME` 指向 SDK，或由 Android Studio 写入本机 `local.properties`。
- 最低系统版本是 Android 13（API 33），编译 API 为 37，目标 API 为 36。最低版本由 Miuix 的模糊模块（`miuix-blur-android` 声明 minSdk 33，基于 `RenderEffect` 运行时着色器）决定。
- 首次构建需要访问 Gradle 分发站、Google Maven、Maven Central 和 Gradle Plugin Portal。工具链与库版本见 [Version Catalog](../../apps/acbooks-android/gradle/libs.versions.toml) 和 [开源依赖说明](../../LICENSE.md#android-开源依赖)。

从 `apps/acbooks-android/` 执行以下命令。Windows PowerShell 上将 `bash ./gradlew` 换成 `.\gradlew.bat`。

```sh
bash ./gradlew :app:assembleDebug
bash ./gradlew :app:installDebug
```

安装需要启动模拟器或连接已启用 USB 调试的设备。安装后可从启动器打开 acbooks，或执行 `adb shell am start -n app.acbooks/.MainActivity`。调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。初次启动直接进入最小首页，无需账号、服务器或网络。

## 模块

| 模块 | 本阶段职责 |
| --- | --- |
| `:app` | Application、Activity、Hilt 组装、Compose Navigation 和应用语言声明 |
| `:feature:home` | 免登录启动占位页和中英文资源 |
| `:core:designsystem` | 主题入口：`AcbooksTheme` 产出唯一的 Miuix `Colors` 并映射为 Material 3 外壳，`LocalAppColors` 由同一份色值反向还原 |
| `:core:datastore` | 独立的外观偏好，默认跟随系统，支持持久化浅色和深色选项 |
| `:core:database` | Room/KSP 和 schema 输出配置，以仪器测试验证 DAO；正式书库表随本地书库模块引入 |

`:app` 依赖首页、设计系统和偏好模块。Room 模块独立构建，正式数据层接入时再依赖。目录边界遵循 [Android 架构规范](../architecture/android-reader.md)。

## 国际化

默认资源为英文，简体中文资源位于 `values-zh-rCN`。Compose 使用 `stringResource` 读取文案。Android 13 及以上支持从系统设置选择应用语言，AppCompat 为后续应用内语言设置提供 API 和旧系统持久化支持。调用 `AppCompatDelegate.setApplicationLocales` 时可使用 `en`、`zh-CN` 或空语言列表（跟随系统）。

构建检查与设备测试见 [Android 测试说明](../testing/android.md)。

## 组件预览

安装 Debug APK 后运行：

```sh
adb shell am start -n app.acbooks/.preview.ComponentPreviewActivity
```

预览可切换中英文、跟随系统/浅色/深色、预设或自定义主题色，并模拟降低玻璃能力、减少透明效果和墨水屏。语言和主题设置通过正常应用偏好持久化。

账号资料：昵称和头像修订/待同步标记保存在 `ProfilePreferences`，头像文件缓存在应用私有目录。设置页点击 56dp 头像通过系统照片选择器选择图片，客户端居中裁剪为 512×512 JPEG；未登录时本地保存并在登录后上传，已登录时直接调用 `/api/v1/auth/me/avatar`；应用启动、登录状态变化和进入设置页时按 `avatarUpdatedAt` 拉取。首页 40dp 玻璃头像按钮读取同一缓存。昵称编辑暂只保留本地显示，后续以弹窗接入。

公共控件使用 `AppGlassScene` 声明同窗口背景。普通内容放在实色 `AppSurface`，控件与浮层放在独立前景。顶栏由 `GlassPageHeader` 通过 Haze 2.1.0（`haze`、`haze-blur`）对滚动正文做渐进高斯模糊；控件与浮层继续使用 Backdrop 2.0.1。硬件加速且未启用减少透明效果时启用 Backdrop 折射，其他场景回退模糊或实色。锚定菜单和下拉选择在 `AppGlassScene` 内由 `AppMenuHost` 统一承载，只有缺少场景时才回退到独立 Popup 窗口和实色材质；同一时刻只保留一个菜单请求。Backdrop 的公开 API 使用原尺寸效果缓冲区，预览页没有采样倍率选项。

设置层级（设置目录、外观、服务、服务器、登录）全部使用 Miuix 0.9.4：页面用 `AppMiuixPage` 包裹（内含 Miuix `Scaffold`，`contentWindowInsets` 置零避免与 `AppSecondaryPage` 的安全区叠加，同时为 `Overlay*` 提供 popup host），卡片统一用 `AppMiuixCard` 或 `AppMiuixSettingsGroup`，设置入口行用 `ArrowPreference`，外观页的主题模式与语言用 `OverlayDropdownPreference`。

主题已交给 Miuix：`AcbooksTheme` 是唯一入口，内部先由 `appColors()` 生成 15 个语义色，经 `toMiuixColors()` 填入 Miuix `Colors` 并作为全局唯一色源，再用 `toAppColors()` 反向还原出 `LocalAppColors`，因此 Miuix 组件与既有 Glass/Fluent 组件共享同一份色值。两个映射互逆，由 `AppMiuixThemeTest` 的往返测试覆盖。Miuix 未使用的 `errorContainer` / `onErrorContainer` 承载 `destructiveText` 与 `controlBorder`。设计系统以 `api` 暴露 `miuix-ui` 与 `miuix-preference`，业务模块可直接引用 Miuix 组件。

圆角、行内边距、行分隔线不覆盖，保持 Miuix 默认（16dp 圆角、16dp 内边距、无分隔线）。玻璃按钮 `LiquidGlassButton`、Backdrop 采样、Haze 顶栏模糊与折射逻辑均未改动，只是改为从同一份 Miuix 色板取色。注意 `MiuixTheme` 现在覆盖全 App，其 `LocalIndication` 与 `LocalOverscrollFactory` 会作用于所有子树；启用态玻璃按钮本来就 `indication = null`，影响面为禁用态与滚动回弹观感。
