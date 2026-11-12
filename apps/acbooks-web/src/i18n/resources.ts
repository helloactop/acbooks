import type { TranslationResources } from "@acbooks/ui";

import enAuth from "./locales/en/auth.json";
import enPreview from "./locales/en/preview.json";
import zhCNAuth from "./locales/zh-CN/auth.json";
import zhCNPreview from "./locales/zh-CN/preview.json";

export const appResources = {
  en: { auth: enAuth, preview: enPreview },
  "zh-CN": { auth: zhCNAuth, preview: zhCNPreview },
} satisfies TranslationResources;
