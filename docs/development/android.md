# Android 开发环境

Android 工程位于 `apps/acbooks-android/`，在 Android Studio 中直接打开该目录。命令行构建使用仓库内的 Gradle Wrapper。

- 使用 JDK 17 或 21，将 `JAVA_HOME` 或 Android Studio 的 Gradle JDK 指向该 JDK。Java 和 Kotlin 字节码目标均为 17。
- 通过 SDK Manager 安装 Android SDK Platform 37.2 及 Android SDK Platform-Tools。将 `ANDROID_HOME` 指向 SDK，或由 Android Studio 写入本机 `local.properties`。
- 最低系统版本是 Android 13（API 33），编译 API 为 37.2，目标 API 为 36。最低版本由 Miuix 的模糊模块（`miuix-blur-android` 声明 minSdk 33，基于 `RenderEffect` 运行时着色器）决定。
- 首次构建需要访问 Gradle 分发站、Google Maven、Maven Central 和 Gradle Plugin Portal。工具链与库版本见 [Version Catalog](../../apps/acbooks-android/gradle/libs.versions.toml) 和 [开源依赖说明](../../LICENSE.md#android-开源依赖)。

从 `apps/acbooks-android/` 执行以下命令。Windows PowerShell 上将 `bash ./gradlew` 换成 `.\gradlew.bat`。

```sh
bash ./gradlew :app:assembleDebug
bash ./gradlew :app:installDebug
```

在 Android 13 或更新的设备上安装并从启动器打开 acbooks。调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## GitHub Release APK

从 GitHub Actions 手动运行 **Android Release APK**。工作流构建 Release APK，只打包 `arm64-v8a` 原生库，并上传签名后的 `ACBooks-android-arm64-v8a-release.apk`。纯 Kotlin/Compose 构建可能没有原生库。

仓库通过 Actions Secrets 提供 `ANDROID_RELEASE_KEYSTORE_BASE64` 和 `ANDROID_RELEASE_KEYSTORE_PASSWORD`。前者是长期 PKCS#12 签名库的 Base64，后者是其密码。签名别名为 `acbooks-release`，工作流在上传前用 `apksigner` 验证 APK 并核对固定的证书 SHA-256 指纹。签名库与密码需在 GitHub 之外保留私密备份。

## 重建基线

当前工程只有 `:app` 模块。入口为包名 `app.acbooks` 下的 `MainActivity`，保留品牌启动图标。界面使用官方 `MiuixTheme`、`Scaffold` 和 `GlassTopAppBar`，默认跟随系统浅色与深色。首页、书库与统计共用 `GlassNavigationBar`，右侧圆形 `GlassIconButton` 打开独立搜索页。

首页使用可折叠的 `GlassTopAppBar`，通过 `MiuixScrollBehavior` 与正文滚动联动。展开和折叠均显示问候语，按设备本地时间在 `Good morning`、`Good afternoon` 和 `Good evening` 之间切换。正文提供阅读历史空状态和书库入口。右上角头像按钮打开独立的设置页。`AcbooksNavigation` 接收头像图片和更换头像回调，供后续头像选择与上传接入。

`:app` 直接依赖官方 `miuix-ui-android`、`miuix-glass-android` 和 `miuix-glass-icons-android`，统一锁定 [Miuix 0.9.5-beta03](https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.5-beta03)。业务页面、本地书库、阅读器、登录和同步按后续阶段重建。数据文件由原安装和 Android 系统管理，当前入口不会打开旧数据库或清理应用私有目录。

后续页面遵循 [Android UI 规范](../ui/android-ui.md) 和 [Android 阅读器架构](../architecture/android-reader.md)。当前检查命令见 [Android 测试说明](../testing/android.md)。
