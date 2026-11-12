import type { TranslationResources } from "@acbooks/ui";

import enPreview from "./locales/en/preview.json";
import zhCNPreview from "./locales/zh-CN/preview.json";

export const appResources = {
  en: { preview: enPreview },
  "zh-CN": { preview: zhCNPreview },
} satisfies TranslationResources;
