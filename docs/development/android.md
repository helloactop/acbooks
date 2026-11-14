# Android 开发环境

Android 工程位于 `apps/acbooks-android/`，在 Android Studio 中直接打开该目录。命令行构建使用仓库内的 Gradle Wrapper。

- 使用 JDK 17 或 21，将 `JAVA_HOME` 或 Android Studio 的 Gradle JDK 指向该 JDK。Java 和 Kotlin 字节码目标均为 17。
- 通过 SDK Manager 安装 Android API 36、Android SDK Build-Tools 35.0.0 和 Android SDK Platform-Tools。将 `ANDROID_HOME` 指向 SDK，或由 Android Studio 写入本机 `local.properties`。
- 最低系统版本是 Android 6.0（API 23），编译和目标 API 均为 36。
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
| `:core:designsystem` | Material 3 浅色、深色 Compose 主题入口 |
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

公共控件使用 `AppGlassScene` 声明同窗口背景。普通内容放在实色 `AppSurface`，控件与浮层放在独立前景。API 33+ 启用 Backdrop 折射，API 31–32 使用模糊，其他场景回退实色。锚定菜单使用独立 Popup 窗口并采取实色回退。Backdrop 1.0.6 的公开 API 使用原尺寸效果缓冲区，预览页没有采样倍率选项。
