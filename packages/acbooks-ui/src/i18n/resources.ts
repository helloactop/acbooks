import type { Resource } from "i18next";

import enCommon from "./locales/en/common.json";
import zhCNCommon from "./locales/zh-CN/common.json";

export const sharedResources = {
  en: { common: enCommon },
  "zh-CN": { common: zhCNCommon },
} satisfies Resource;
