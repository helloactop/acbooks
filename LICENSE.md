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
| Nivo Line | `@nivo/line@0.99.0` | [plouc/nivo](https://github.com/plouc/nivo/tree/v0.99.0/packages/line) | [MIT](https://github.com/plouc/nivo/blob/v0.99.0/LICENSE.md) | 绘制阅读时长趋势 |
| Nivo Bar | `@nivo/bar@0.99.0` | [plouc/nivo](https://github.com/plouc/nivo/tree/v0.99.0/packages/bar) | [MIT](https://github.com/plouc/nivo/blob/v0.99.0/LICENSE.md) | 绘制阅读时段分布 |
| React Activity Calendar | `react-activity-calendar@3.2.0` | [grubersjoe/react-activity-calendar](https://github.com/grubersjoe/react-activity-calendar/tree/v3.2) | [MIT](https://github.com/grubersjoe/react-activity-calendar/blob/v3.2/LICENSE) | 绘制年度阅读热力图 |
| shadcn-calendar-heatmap | 源码改编，提交 `7598567` | [gurbaaz27/shadcn-calendar-heatmap](https://github.com/gurbaaz27/shadcn-calendar-heatmap/tree/759856717f149bbf95d4e1e4b2712b64a0006e7b) | MIT | 月度日历热力分级 |
| React DayPicker | `react-day-picker@9.14.0` | [gpbl/react-day-picker](https://github.com/gpbl/react-day-picker/tree/v9.14.0) | MIT | 月历、键盘导航与日期交互 |
| date-fns | `date-fns@4.4.0` | [date-fns/date-fns](https://github.com/date-fns/date-fns/tree/v4.4.0) | MIT | 月历日期转换及中英文 locale |
| Python tzdata | `tzdata==2026.6` | [python/tzdata](https://github.com/python/tzdata) | [Apache-2.0](https://pypi.org/project/tzdata/2026.6/) | 为缺少 IANA 时区数据库的 Windows 或精简部署提供 `zoneinfo` 回退 |

MIT（Expat）、ISC 和 Apache-2.0 均兼容 GPL 第 3 版。[GNU 许可证兼容性说明](https://www.gnu.org/licenses/license-compatibility.html)

## 图标依赖

| 开源项目 | 包和实际版本 | 仓库 | 许可证 | 用途 |
| --- | --- | --- | --- | --- |
| Lucide | `lucide-react@1.72.0` | [lucide-icons/lucide](https://github.com/lucide-icons/lucide) | ISC，Feather 派生图标为 MIT | Web 与共享 UI 的导航、日历和操作图标 |
| Feather | 随 `lucide-react@1.72.0` 分发 | [feathericons/feather](https://github.com/feathericons/feather) | MIT | Lucide 中的派生图标，包括左右箭头等 |

## 第三方版权与许可声明

### shadcn-calendar-heatmap

~~~~text
MIT License

Copyright (c) 2024 Gurbaaz Singh Nandra

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
~~~~

### React DayPicker

~~~~text
The MIT License (MIT)

Copyright (c) 2014-2025 Giampaolo Bellavite <io@gpbl.dev> and contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
~~~~

### date-fns

~~~~text
MIT License

Copyright (c) 2021 Sasha Koss and Lesha Koss https://kossnocorp.mit-license.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
~~~~

### Lucide 与 Feather

~~~~text
ISC License

Copyright (c) 2026 Lucide Icons and Contributors

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

---

The following Lucide icons are derived from the Feather project:

airplay, alert-circle, alert-octagon, alert-triangle, aperture, arrow-down-circle, arrow-down-left, arrow-down-right, arrow-down, arrow-left-circle, arrow-left, arrow-right-circle, arrow-right, arrow-up-circle, arrow-up-left, arrow-up-right, arrow-up, at-sign, calendar, cast, check, chevron-down, chevron-left, chevron-right, chevron-up, chevrons-down, chevrons-left, chevrons-right, chevrons-up, circle, clipboard, clock, code, columns, command, compass, corner-down-left, corner-down-right, corner-left-down, corner-left-up, corner-right-down, corner-right-up, corner-up-left, corner-up-right, crosshair, database, divide-circle, divide-square, dollar-sign, download, external-link, feather, frown, hash, headphones, help-circle, info, italic, key, layout, life-buoy, link-2, link, loader, lock, log-in, log-out, maximize, meh, minimize, minimize-2, minus-circle, minus-square, minus, monitor, moon, more-horizontal, more-vertical, move, music, navigation-2, navigation, octagon, pause-circle, percent, plus-circle, plus-square, plus, power, radio, rss, search, server, share, shopping-bag, sidebar, smartphone, smile, square, table-2, tablet, target, terminal, trash-2, trash, triangle, tv, type, upload, x-circle, x-octagon, x-square, x, zoom-in, zoom-out

The MIT License (MIT) (for the icons listed above)

Copyright (c) 2013-present Cole Bemis

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
~~~~
