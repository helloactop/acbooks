# acbooks 后端

在本目录使用 Python 3.12 或更高版本的独立虚拟环境运行。

```sh
python -m pip install -e ".[dev]"
python -m uvicorn app.main:create_app --factory --no-access-log
```

默认监听 `127.0.0.1:8000`。应用访问日志记录请求 ID、状态码和耗时；
启动参数 `--no-access-log` 关闭 Uvicorn 自带的请求行日志。

环境变量 `ACBOOKS_ENABLE_DOCS` 默认为 `false`，设为 `true` 可开启
`/docs`。OpenAPI 始终在 `/openapi.json` 提供。健康检查见
[后端健康检查](../../docs/api/health.md)，Books Core V1 见
[Books API](../../docs/api/books.md)。

## SQLite 与迁移

从本目录运行 `python -m alembic upgrade head` 初始化或升级数据库。
默认路径为 `/data/acbooks/database/acbooks.db`，运行前需在服务端的本地文件系统
准备可写的父目录。开发与验证可用 `-x db_path` 显式指定临时库或数据副本。

```sh
python -m alembic -x db_path=./.venv/acbooks-dev.db upgrade head
python -m alembic -x db_path=./.venv/acbooks-dev.db current
python -m alembic -x db_path=./.venv/acbooks-dev.db check
```

迁移由 Alembic 执行。应用启动和 HTTP 存活探针不会触发迁移；
`GET /api/v1/ready` 检查数据库连接与 `alembic_version`，未升级到当前 head 时返回 `503`。
API 与 Worker 的数据库调用可复用 `app.db.session` 的引擎和会话工厂，
由业务服务使用 `session_factory.begin()` 管理写事务。

变更模型后，对已升级至当前版本的本地库运行
`python -m alembic -x db_path=./.venv/acbooks-dev.db revision --autogenerate -m "描述变更"`。
审查生成的脚本，并在旧数据副本验证升级与完整性。新增迁移后同步更新
`app/db/version.py` 的 `SCHEMA_HEAD_REVISION`；`/api/v1/ready` 与测试据此校验迁移状态。涉及 SQLite batch 重建时，
特别检查入向外键和级联行为。初始迁移的 `downgrade base` 会删除业务表及其数据，
仅在可丢弃的测试库验证。真实库升级前使用 SQLite 一致性备份。

## 首次管理员初始化

迁移到 `head` 后，在宿主机或受信任的容器中，以后端服务用户执行安装命令。
命令会在 `/data/acbooks/secrets/setup-code` 生成 256 位随机初始化码，不输出码本身。
部署管理员从宿主机读取该文件。Linux 上目录须归服务用户所有，权限为 `0700`，
文件权限为 `0600`。Windows 本地开发须将数据目录放在仅当前用户和系统管理员可访问的
NTFS 目录下，由新文件继承目录 ACL。

```sh
python -m app.modules.auth.provision
```

生产部署将 `ACBOOKS_PUBLIC_ORIGIN` 设为外部 HTTPS 来源，例如 `https://books.example`。
反向代理终止 TLS 时，应向 Uvicorn 转发 HTTPS 协议信息，并把
`--forwarded-allow-ips` 限定为受信代理地址。默认 HTTP 来源仅用于本机开发。
首次初始化期间运行一个 API 进程，预认证挑战与限速状态保存在该进程内。

调用方式见 [API 规范的首次初始化一节](../../docs/api/conventions.md#41-初始化与登录路由)。
创建成功后，数据库中的管理员记录立即关闭初始化入口，服务端会删除码文件。
若文件删除失败，修复权限后可重跑安装命令清除残留文件。

Web Cookie 会话默认在签发 7 天后到期。可通过 `ACBOOKS_WEB_SESSION_TTL_SECONDS`
配置新会话有效期，范围为 1–604800 秒。使用 Docker Compose 时可在项目的 `.env`
中设置该变量。

## 检查

本地与 CI 的环境及覆盖范围见[自动化检查](../../docs/testing/strategy.md)。

```sh
python -m ruff check .
python -m mypy app tests
python -m pytest
```
