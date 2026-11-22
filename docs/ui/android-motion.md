# acbooks Android 动画规范

Miuix 控件、导航组件与浮层使用组件库默认动效。独立页面由 `miuix-nav` 管理，三个主 Tab 由 `HorizontalPager` 承载。组件范围见 [Android UI 规范](android-ui.md)。

## 页面转场

- 根 `NavDisplay` 使用 `NavTransitions.MiuixDefault`，沿用官方页面滑动、视差、透明度、遮罩曲线与转场时序，并使用设备屏幕圆角。设置页顶栏返回和系统预测性返回共享同一返回栈。边缘返回手势取消时，由 Miuix 恢复当前页面。
- 路由通过可序列化的 `NavKey` 和 `rememberNavController` 保存与恢复。主 Tab 的正文横滑交给 Pager，独立页面的边缘返回交给 Android 系统。在拖动正文时，底栏显示 Pager 的 `settledPage`，松手后根据 `targetPage` 同步选中项。横滑切换、退出搜索、从阅读器返回和系统重建后保留相应的路由与页面状态。
- 从底栏切换 Tab 和系统返回首页共用同一切页规则。相邻页使用 Miuix `springAnimateToPage`，跨越多个 Tab 时直接定位目标页。横滑松手后的吸附使用 `PagerNavigationSpringSpec`。
- 首页、书库、统计和设置使用 `GlassTopAppBar(expandable = true)`。每页将自身的 `MiuixScrollBehavior.nestedScrollConnection` 接入正文滚动容器，由 Miuix 驱动标题折叠与恢复。切换 Tab 时按页恢复各自的折叠状态。
- 预测性返回只在手势提交后改变返回栈。取消手势时保留当前路由及页面输入。
- 遵循系统动画时长缩放。减少动态效果时保留必要的操作反馈，过渡期间防止连续导航导致重复出栈。

## 浮层

- 搜索按钮打开官方 `GlassSearchOverlay`，主页面通过 `rememberGlassSearchBarAlpha` 与 `glassSearchBarTransition` 播放库内的淡出、缩放和模糊。浮层接管输入焦点、键盘、清除、取消和返回。搜索浮层关闭后恢复打开前的主 Tab 与页面状态。
- 对话框、菜单、下拉项与底部浮层使用 Miuix 自带的动效与遮罩。
- 底部任务浮层保留拖动关闭及程序关闭时的退出行为。菜单或浮层打开时，第一次返回优先关闭它。退出期间防止重复点击。
- 导入、登录和阅读操作的进度与结果由业务状态驱动。

## 兼容与验收

- 在 API 33 及以上验证系统返回、顶栏返回、左右边缘返回及取消，检查旋转或 Activity 重建后的路由、Tab、输入与阅读位置。
- 验证连续导航、Tab 切换、书库与阅读页返回，以及各类浮层的关闭顺序和系统动画缩放。

[Android 预测性返回设置](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture#opt-out-of-predictive-back)
