FROM node:22-bookworm-slim AS web

WORKDIR /src
RUN corepack enable

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml tsconfig.base.json ./
COPY apps/acbooks-web/package.json apps/acbooks-web/package.json
COPY packages/acbooks-ui/package.json packages/acbooks-ui/package.json
RUN pnpm install --frozen-lockfile --filter @acbooks/web...

COPY apps/acbooks-web/ apps/acbooks-web/
COPY packages/acbooks-ui/ packages/acbooks-ui/
COPY packages/acbooks-api-client/ packages/acbooks-api-client/
RUN pnpm --filter @acbooks/web build


FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    ACBOOKS_WEB_DIST_PATH=/opt/acbooks/web

WORKDIR /opt/acbooks
COPY LICENSE ./
COPY apps/acbooks-server/pyproject.toml apps/acbooks-server/alembic.ini ./
COPY apps/acbooks-server/app/ ./app/
COPY --from=web /src/apps/acbooks-web/dist ./web

RUN python -m pip install --no-cache-dir . \
    && groupadd --gid 10001 acbooks \
    && useradd --uid 10001 --gid 10001 --no-create-home acbooks \
    && install -d -m 0750 -o acbooks -g acbooks /data/acbooks \
        /data/acbooks/database /data/acbooks/library /data/acbooks/covers \
        /data/acbooks/cache /data/acbooks/backups /data/acbooks/logs \
    && install -d -m 0700 -o acbooks -g acbooks /data/acbooks/secrets

USER 10001:10001
EXPOSE 8000
