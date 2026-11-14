# acbooks 许可证说明

acbooks 的项目源码采用 **GNU 通用公共许可证第 3 版（GPL-3.0-only）**。`only` 表示仅适用第 3 版，不包含“或任何更新版本”的选项。完整条款见 [LICENSE](LICENSE)。

- **使用和修改**：你可以运行、研究和修改 acbooks，包括商业使用。仅在自己的设备或 NAS 上运行或修改，通常不需要公开源码。仅通过网络提供服务，本身也不会触发 GPL 第 3 版的源码提供义务。
- **分发**：如果向他人分发原版或修改版，须保留适用的版权和许可声明，附上许可证全文，并按 GPL 第 3 版的条款许可受其约束的作品。分发修改版时须标明修改及相关日期；分发二进制文件、安装包或容器镜像时，须按许可证允许的方式向接收者提供对应源码。
- **第三方内容**：第三方组件遵循各自的许可证。你导入的电子书、封面和其他资料不会因为由 acbooks 管理而改用 GPL；它们仍适用各自的版权和授权条款。

本说明便于阅读；具体权利、义务和免责声明以 [许可证全文](LICENSE) 为准。

## 阅读统计的开源依赖

以下版本与前端锁文件及后端依赖清单一致。分发产物应保留适用的第三方版权与许可证声明。

| 开源项目 | 包和实际版本 | 仓库 | 许可证 | 用途 |
| --- | --- | --- | --- | --- |
| Nivo Line | `@nivo/line@0.99.0` | [plouc/nivo](https://github.com/plouc/nivo/tree/v0.99.0/packages/line) | [MIT](https://github.com/plouc/nivo/blob/v0.99.0/LICENSE.md) | 绘制阅读时长趋势、阅读间隔趋势 |
| Nivo Bar | `@nivo/bar@0.99.0` | [plouc/nivo](https://github.com/plouc/nivo/tree/v0.99.0/packages/bar) | [MIT](https://github.com/plouc/nivo/blob/v0.99.0/LICENSE.md) | 绘制阅读次数、阅读时段分布 |
| React Activity Calendar | `react-activity-calendar@3.2.0` | [grubersjoe/react-activity-calendar](https://github.com/grubersjoe/react-activity-calendar/tree/v3.2) | [MIT](https://github.com/grubersjoe/react-activity-calendar/blob/v3.2/LICENSE) | 绘制年度阅读热力图 |
| Python tzdata | `tzdata==2026.6` | [python/tzdata](https://github.com/python/tzdata) | [Apache-2.0](https://pypi.org/project/tzdata/2026.6/) | 为缺少 IANA 时区数据库的 Windows 或精简部署提供 `zoneinfo` 回退 |

MIT（Expat）和 Apache-2.0 均兼容 GPL 第 3 版。图表运行时的传递依赖使用 MIT、ISC、BSD-3-Clause 与 Unlicense。[GNU 许可证兼容性说明](https://www.gnu.org/licenses/license-compatibility.html)
