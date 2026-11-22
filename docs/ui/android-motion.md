# acbooks Android 动画规范

Miuix 控件、导航组件与浮层使用组件库默认动效。Compose Navigation 管理页面路由，三个主 Tab 由 `HorizontalPager` 承载。组件范围见 [Android UI 规范](android-ui.md)。

## 页面转场

- 保留页面前进时从右侧进入、返回时向右退出的路由行为。顶栏返回、系统返回、边缘手势提交和返回首页沿用同一路由语义。
- 根 `NavHost` 管理页面转场。底栏选中项与 Pager 的 `currentPage` 同步。横滑切换、从阅读器返回和系统重建后保留相应的路由与页面状态。
- 点击主 Tab 和从主 Tab 返回首页使用 Miuix `springAnimateToPage`，横滑松手后的吸附使用 `PagerNavigationSpringSpec`。
- 导航重建时，拖动期间当前页保持原位，手势取消后保留路由和输入。仅正在显示且独立消费返回的浮层可以拦截一次返回。
- 遵循系统动画时长缩放。减少动态效果时保留必要的操作反馈，过渡期间防止连续导航导致重复出栈。

## 浮层

- 对话框、菜单、下拉项与底部浮层使用 Miuix 自带的动效与遮罩。
- 底部任务浮层保留拖动关闭及程序关闭时的退出行为。菜单或浮层打开时，第一次返回优先关闭它。退出期间防止重复点击。
- 导入、登录和阅读操作的进度与结果由业务状态驱动。

## 兼容与验收

- 在 API 33 及以上验证系统返回、顶栏返回、左右边缘返回及取消，检查旋转或 Activity 重建后的路由、Tab、输入与阅读位置。
- 验证连续导航、Tab 切换、书库与阅读页返回，以及各类浮层的关闭顺序和系统动画缩放。

[Android 预测性返回设置](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture#opt-out-of-predictive-back)
