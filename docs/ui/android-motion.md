# acbooks Android 动画规范

页面转场由 Jetpack Compose Animation 管理；根路由使用 Navigation Compose 的 `NavHost`，三个主 Tab 使用 `HorizontalPager`。液态玻璃材质、交互形变与图标遵循 [Android UI 规范](android-ui.md)。

## 页面转场

- 打开页面或切换至书库、统计时，新页从屏幕右侧滑入，原页在下方保持原位。
- 导航返回时，当前页向屏幕右侧滑出，上一页在原位显示。顶栏返回键、系统返回键、边缘手势提交和 Dock 返回首页均使用此转场。
- 页面前进和返回共用 `AppMotion.pageDurationMillis`，默认 200ms，使用同一条对称缓动曲线。Compose 遵循系统动画时长缩放。
- 应用清单关闭系统预测性返回动画。拖动期间当前页保持原位，手势取消后保留当前路由和输入。
- 根 `NavHost` 使用 `NavigationMotion.kt`；Tab 切换由 `HorizontalPager` 的水平滑动承载，底栏选中项和 `currentPage` 同步。书籍封面和书名入口使用根路由页面横滑，返回保留书架筛选和滚动位置。
- 顶栏返回键仅接受当前已恢复到前台的路由发出的操作，避免连点同时弹出多级。只有正在显示且独立消费返回的覆盖层可注册局部返回处理。

## 浮层

- 底部任务浮层使用 Material 3 `ModalBottomSheet` 的进出场、拖动和遮罩。程序关闭时先完成 Sheet 的隐藏动画。
- 居中 Dialog 从底部滑入、向下退出，开关和遮罩共用 300ms 对称缓动。系统返回提交后关闭。
- 锚定菜单和下拉列表共用 120ms 淡入淡出与轻微缩放。展开时优先消费一次返回并关闭，退出动画中禁用选项。
- 实色按钮、分段控件与开关使用共享弹簧。Kyant0 负责玻璃控件的折射与交互形变。

## 兼容与验收

- 最低版本为 API 33。验证系统返回、顶栏返回与原生 Sheet。
- 在手势导航模式验证左右边缘提交返回，拖动期间当前页保持原位。旋转或 Activity 重建后核对路由、Tab 和输入。
- 验证 Tab 切换、书库和阅读页返回、连续导航及逐级返回。底部浮层和选择框展开时，第一次返回关闭对应浮层。

[Android 预测性返回设置](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture#opt-out-of-predictive-back) · [Material 3 底部浮层](https://developer.android.com/develop/ui/compose/components/bottom-sheets)
