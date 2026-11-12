import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = fileURLToPath(new URL("..", import.meta.url));

const LOCALE_ROOTS = [
  {
    name: "shared",
    path: "packages/acbooks-ui/src/i18n/locales",
  },
  {
    name: "web",
    path: "apps/acbooks-web/src/i18n/locales",
  },
];

const REQUIRED_LANGUAGES = ["zh-CN", "en"];

const errors = [];
const namespaceOwners = new Map();

function fail(message) {
  errors.push(message);
}

function readJson(filePath, label) {
  try {
    return JSON.parse(readFileSync(filePath, "utf8"));
  } catch (error) {
    fail(`${label}: cannot read JSON (${error.message})`);
    return null;
  }
}

function listLanguages(root) {
  const absolute = join(repoRoot, root.path);
  if (!existsSync(absolute)) {
    fail(`${root.name}: missing locale directory ${root.path}`);
    return [];
  }

  return readdirSync(absolute, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort();
}

function readNamespaces(root, language) {
  const directory = join(repoRoot, root.path, language);
  const namespaces = new Map();

  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (!entry.isFile() || !entry.name.endsWith(".json")) {
      continue;
    }
    const namespace = entry.name.slice(0, -".json".length);
    const label = `${root.name}/${language}/${entry.name}`;
    const content = readJson(join(directory, entry.name), label);
    if (content !== null) {
      if (typeof content !== "object" || Array.isArray(content)) {
        fail(`${label}: top-level value must be an object`);
        continue;
      }
      namespaces.set(namespace, content);
    }
  }

  return namespaces;
}

function flattenKeys(value, prefix = "", keys = new Map()) {
  for (const [key, child] of Object.entries(value)) {
    const path = prefix.length > 0 ? `${prefix}.${key}` : key;
    if (child !== null && typeof child === "object" && !Array.isArray(child)) {
      flattenKeys(child, path, keys);
    } else {
      keys.set(path, child);
    }
  }
  return keys;
}

function placeholdersOf(value) {
  if (typeof value !== "string") {
    return new Set();
  }
  return new Set(
    Array.from(value.matchAll(/{{\s*([\w.-]+)\s*}}/g), (match) => match[1]),
  );
}

function compareSets(expected, actual, expectedLabel, actualLabel) {
  return Array.from(expected)
    .filter((item) => !actual.has(item))
    .map((item) => `${actualLabel} is missing "${item}" from ${expectedLabel}`);
}

for (const root of LOCALE_ROOTS) {
  const languages = listLanguages(root);

  for (const language of REQUIRED_LANGUAGES) {
    if (!languages.includes(language)) {
      fail(`${root.name}: missing language directory "${language}"`);
    }
  }

  for (const language of languages) {
    if (!REQUIRED_LANGUAGES.includes(language)) {
      fail(
        `${root.name}: unsupported language directory "${language}" (expected ${REQUIRED_LANGUAGES.join(", ")})`,
      );
    }
  }

  const availableLanguages = REQUIRED_LANGUAGES.filter((language) =>
    languages.includes(language),
  );
  const namespacesByLanguage = new Map(
    availableLanguages.map((language) => [
      language,
      readNamespaces(root, language),
    ]),
  );

  const allNamespaces = new Set(
    Array.from(namespacesByLanguage.values()).flatMap((namespaces) =>
      Array.from(namespaces.keys()),
    ),
  );

  for (const namespace of allNamespaces) {
    const owner = namespaceOwners.get(namespace);
    if (owner !== undefined && owner !== root.name) {
      fail(
        `namespace "${namespace}" is defined in both ${owner} and ${root.name}`,
      );
    } else {
      namespaceOwners.set(namespace, root.name);
    }

    const flattened = new Map();
    for (const language of availableLanguages) {
      const content = namespacesByLanguage.get(language).get(namespace);
      if (content === undefined) {
        fail(`${root.name}: namespace "${namespace}" is missing "${language}"`);
        continue;
      }
      flattened.set(language, flattenKeys(content));
    }

    if (flattened.size < 2) {
      continue;
    }

    const [baseLanguage, ...otherLanguages] = Array.from(flattened.keys());
    const baseKeys = flattened.get(baseLanguage);

    for (const language of otherLanguages) {
      const keys = flattened.get(language);
      for (const message of compareSets(
        baseKeys.keys(),
        new Set(keys.keys()),
        `${baseLanguage}/${namespace}`,
        `${language}/${namespace}`,
      )) {
        fail(`${root.name}: ${message}`);
      }
      for (const message of compareSets(
        new Set(keys.keys()),
        new Set(baseKeys.keys()),
        `${language}/${namespace}`,
        `${baseLanguage}/${namespace}`,
      )) {
        fail(`${root.name}: ${message}`);
      }

      for (const key of baseKeys.keys()) {
        if (!keys.has(key)) {
          continue;
        }
        const basePlaceholders = placeholdersOf(baseKeys.get(key));
        const placeholders = placeholdersOf(keys.get(key));
        for (const message of compareSets(
          basePlaceholders,
          placeholders,
          `${baseLanguage}/${namespace}:${key}`,
          `${language}/${namespace}:${key}`,
        )) {
          fail(`${root.name}: ${message}`);
        }
        for (const message of compareSets(
          placeholders,
          basePlaceholders,
          `${language}/${namespace}:${key}`,
          `${baseLanguage}/${namespace}:${key}`,
        )) {
          fail(`${root.name}: ${message}`);
        }
      }
    }
  }
}

if (errors.length > 0) {
  console.error("i18n check failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log(
  `i18n check passed: ${LOCALE_ROOTS.map((root) => root.name).join(", ")} resources match ${REQUIRED_LANGUAGES.join(" / ")}.`,
);
