import { spawn, spawnSync } from "node:child_process";
import { existsSync, mkdirSync, rmSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const e2eDir = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(e2eDir, "..");
const repoRoot = path.resolve(webRoot, "../..");
const serverRoot = path.join(repoRoot, "apps/acbooks-server");
const dataDir = path.join(webRoot, ".e2e-data");
const distDir = path.join(webRoot, "dist");
const port = process.env.ACBOOKS_E2E_PORT ?? "4174";
const origin = `http://127.0.0.1:${port}`;
const username = "admin";
const password = "correct horse battery staple";

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { stdio: "inherit", ...options });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

function resolvePython() {
  const candidates = [
    process.env.ACBOOKS_PYTHON,
    path.join(
      serverRoot,
      process.platform === "win32" ? ".venv/Scripts/python.exe" : ".venv/bin/python",
    ),
    process.platform === "win32" ? null : "python3",
    "python",
  ].filter((value) => value !== undefined && value !== null);
  for (const candidate of candidates) {
    if (candidate.includes(path.sep) && !existsSync(candidate)) {
      continue;
    }
    const probe = spawnSync(candidate, ["-c", "import fastapi, sqlalchemy"], {
      cwd: serverRoot,
      stdio: "ignore",
    });
    if (probe.status === 0) {
      return candidate;
    }
  }
  throw new Error("Python with server dependencies was not found. Set ACBOOKS_PYTHON.");
}

const python = resolvePython();

if (process.env.ACBOOKS_E2E_SKIP_BUILD !== "1") {
  const pnpm = process.platform === "win32" ? "pnpm.cmd" : "pnpm";
  run(pnpm, ["--filter", "@acbooks/web", "build"], {
    cwd: repoRoot,
    shell: process.platform === "win32",
  });
}
if (!existsSync(path.join(distDir, "index.html"))) {
  throw new Error("Web build output is missing; run the build before the E2E server.");
}

rmSync(dataDir, { force: true, recursive: true });
mkdirSync(dataDir, { recursive: true });
const database = path.join(dataDir, "acbooks.db");
const env = {
  ...process.env,
  PYTHONPATH: [e2eDir, serverRoot, process.env.PYTHONPATH]
    .filter(Boolean)
    .join(path.delimiter),
};

run(python, ["-m", "alembic", "-x", `db_path=${database}`, "upgrade", "head"], {
  cwd: serverRoot,
  env,
});
run(python, [path.join(e2eDir, "bootstrap.py"), database, username, password], {
  cwd: serverRoot,
  env,
});

const child = spawn(
  python,
  [
    "-m",
    "uvicorn",
    "e2e_app:app",
    "--host",
    "127.0.0.1",
    "--port",
    port,
    "--no-access-log",
  ],
  {
    cwd: serverRoot,
    env: {
      ...env,
      ACBOOKS_E2E_DATABASE: database,
      ACBOOKS_E2E_ORIGIN: origin,
      ACBOOKS_E2E_WEB: distDir,
      ACBOOKS_E2E_COVERS: path.join(dataDir, "covers"),
      ACBOOKS_E2E_LIBRARY: path.join(dataDir, "library"),
    },
    stdio: "inherit",
  },
);

let stopping = false;
for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    if (!stopping) {
      stopping = true;
      child.kill(signal);
    }
  });
}
child.on("exit", (code) => {
  process.exit(code ?? 0);
});
