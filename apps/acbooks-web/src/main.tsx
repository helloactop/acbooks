import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";

import { I18nProvider, ThemeProvider } from "@acbooks/ui";

import App from "./App";
import { appResources } from "./i18n/resources";
import "./index.css";

const root = document.getElementById("root");

if (root === null) {
  throw new Error("Missing #root element.");
}

createRoot(root).render(
  <StrictMode>
    <ThemeProvider>
      <I18nProvider resources={appResources}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </I18nProvider>
    </ThemeProvider>
  </StrictMode>,
);
