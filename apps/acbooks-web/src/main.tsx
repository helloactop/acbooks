import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { ThemeProvider } from "@acbooks/ui";

import App from "./App";
import "./index.css";

const root = document.getElementById("root");

if (root === null) {
  throw new Error("Missing #root element.");
}

createRoot(root).render(
  <StrictMode>
    <ThemeProvider>
      <App />
    </ThemeProvider>
  </StrictMode>,
);
