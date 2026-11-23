# Android 开发环境

Android 工程位于 `apps/acbooks-android/`，在 Android Studio 中直接打开该目录。命令行构建使用仓库内的 Gradle Wrapper。

- 使用 JDK 21，将 `JAVA_HOME` 或 Android Studio 的 Gradle JDK 指向该 JDK。Java 和 Kotlin 字节码目标均为 21，以便调用 Miuix Nav 的内联 API。
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

## 本地检查与设备验收

从 `apps/acbooks-android/` 执行（Windows PowerShell 使用 `.\\gradlew.bat`）：

```sh
bash ./gradlew :app:assembleDebug :app:lintDebug
bash ./gradlew :core:database:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

设备测试需要连接 API 33 或更新版本的模拟器/真机。按改动范围检查 Room 迁移与旧数据保留、本地 EPUB/PDF 导入和进度恢复、服务器登录与离线同步、返回手势、玻璃菜单及中英文/深浅色布局。**不将旧版本的历史测试结果视为当前验证结果**；本次检查结果在 PR 中单独报告。

Android 架构与 UI 规则见 [阅读器架构](../architecture/android-reader.md)、[Android UI](../ui/android-ui.md) 和 [页面规范](../ui/android-ui-pages.md)。
