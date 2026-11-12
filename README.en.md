# acbooks

[简体中文](README.md)

acbooks is a self-hosted personal library designed primarily for NAS deployment, with Linux server support planned. It brings physical books and ebooks into one library. A single book entry can track where physical copies are stored, hold multiple ebook files, and keep your reading history.

The project is in early development. Planned features include library management and reading on the web, bulk management on Windows, and reading on Android, along with offline reading, cross-device sync, and reading statistics.

## Frontend development

The Web client requires Node.js >= 22.12 and pnpm 10. From the repository root:

```sh
pnpm install
pnpm dev:web
```

Run `pnpm lint`, `pnpm typecheck`, `pnpm test` and `pnpm build` for checks.
See the [Web README](apps/acbooks-web/README.md) and
[shared UI README](packages/acbooks-ui/README.md) for details.

The project is licensed under GPL-3.0-only. [Full license text](LICENSE)
