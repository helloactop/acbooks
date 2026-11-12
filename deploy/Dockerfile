FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /opt/acbooks
COPY LICENSE ./
COPY apps/acbooks-server/pyproject.toml apps/acbooks-server/alembic.ini ./
COPY apps/acbooks-server/app/ ./app/

RUN python -m pip install --no-cache-dir . \
    && groupadd --gid 10001 acbooks \
    && useradd --uid 10001 --gid 10001 --no-create-home acbooks \
    && install -d -m 0750 -o acbooks -g acbooks /data/acbooks \
        /data/acbooks/database /data/acbooks/library /data/acbooks/covers \
        /data/acbooks/cache /data/acbooks/backups /data/acbooks/logs \
    && install -d -m 0700 -o acbooks -g acbooks /data/acbooks/secrets

USER 10001:10001
EXPOSE 8000
