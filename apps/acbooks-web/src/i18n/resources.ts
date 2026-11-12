import type { TranslationResources } from "@acbooks/ui";

import enApp from "./locales/en/app.json";
import enAuth from "./locales/en/auth.json";
import enDashboard from "./locales/en/dashboard.json";
import enLibrary from "./locales/en/library.json";
import enPreview from "./locales/en/preview.json";
import zhCNApp from "./locales/zh-CN/app.json";
import zhCNAuth from "./locales/zh-CN/auth.json";
import zhCNDashboard from "./locales/zh-CN/dashboard.json";
import zhCNLibrary from "./locales/zh-CN/library.json";
import zhCNPreview from "./locales/zh-CN/preview.json";

export const appResources = {
  en: {
    app: enApp,
    auth: enAuth,
    dashboard: enDashboard,
    library: enLibrary,
    preview: enPreview,
  },
  "zh-CN": {
    app: zhCNApp,
    auth: zhCNAuth,
    dashboard: zhCNDashboard,
    library: zhCNLibrary,
    preview: zhCNPreview,
  },
} satisfies TranslationResources;
