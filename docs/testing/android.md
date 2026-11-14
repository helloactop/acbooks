# Android 基础测试

从 `apps/acbooks-android/` 运行。Windows PowerShell 上将 `bash ./gradlew` 换成 `.\gradlew.bat`。

```sh
bash ./gradlew assembleDebug testDebugUnitTest lintDebug
bash ./gradlew :app:connectedDebugAndroidTest :core:database:connectedDebugAndroidTest
```

连接一个 API 23 或更新的设备运行设备测试。系统设置中的应用语言需在 API 33 或更新的设备验证。构建和安装步骤见 [Android 开发环境](../development/android.md)。

| 检查 | 覆盖范围 |
| --- | --- |
| DataStore JVM 测试 | 默认跟随系统，显式主题跨 DataStore 实例持久化，未知存储值回退 |
| 启动仪器测试 | 启动 Activity、Hilt 注入、导航默认进入首页，中英文切换与 Activity 重建，其他语言回退英文 |
| Room 仪器测试 | 测试内存库的生成 DAO 写入和查询 |
| Android Lint | Android 清单、资源、平台 API 和源码静态检查 |
| 设计系统 JVM 测试 | 主题色派生的文字与控件对比度、API 与能力分级、Fluent 图标变体和控件加粗 |
| 组件预览仪器测试 | 语言与主题切换、材质降级、导航与独立添加入口、收藏和书签切换及双语可访问名称、菜单和浮层 |

报告位于各模块的 `build/reports/`。启动测试可在未配置账号和服务器的设备上运行。

## 初始化验收

基于 `ffe9310` 的 Android 初始化工作区在 Windows、JDK 21 和 Android 16（API 36）模拟器上通过上述检查。DataStore JVM 测试 2 项、设备测试 4 项全部通过。Lint 无错误，保留依赖更新、目标 API 和启动图标等警告。低版本设备与实体设备尚未验证。

公共设计系统在同一环境通过 Debug、未签名 Release 构建、Lint、5 项 JVM 测试与 5 项设备测试。API 31–32 由能力分级单元测试覆盖，模糊和实色路径在 API 36 预览中验证。真实低版本设备、墨水屏及渲染帧耗时尚未验证。

迁移 Fluent UI System Icons 1.0.1 后，使用 JDK 21 通过 Debug、未签名 Release 构建、Lint、8 项 JVM 测试及 API 36 模拟器上的 5 项仪器测试。图标测试覆盖 Filled 导航、Regular 控件描边及收藏和书签变体，组件预览验证中英文可访问名称随状态变化。
