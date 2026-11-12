import type { TranslationResources } from "@acbooks/ui";

import enAuth from "./locales/en/auth.json";
import enLibrary from "./locales/en/library.json";
import enPreview from "./locales/en/preview.json";
import zhCNAuth from "./locales/zh-CN/auth.json";
import zhCNLibrary from "./locales/zh-CN/library.json";
import zhCNPreview from "./locales/zh-CN/preview.json";

export const appResources = {
  en: { auth: enAuth, library: enLibrary, preview: enPreview },
  "zh-CN": {
    auth: zhCNAuth,
    library: zhCNLibrary,
    preview: zhCNPreview,
  },
} satisfies TranslationResources;
