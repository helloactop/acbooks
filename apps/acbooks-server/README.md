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
[后端健康检查](../../docs/api/health.md)。

## SQLite 与迁移

从本目录运行 `python -m alembic upgrade head` 初始化或升级数据库。
默认路径为 `/data/acbooks/database/acbooks.db`，运行前需在服务端的本地文件系统
准备可写的父目录。开发与验证可用 `-x db_path` 显式指定临时库或数据副本。

```sh
python -m alembic -x db_path=./.venv/acbooks-dev.db upgrade head
python -m alembic -x db_path=./.venv/acbooks-dev.db current
python -m alembic -x db_path=./.venv/acbooks-dev.db check
```

迁移由 Alembic 执行。应用启动和 HTTP 存活探针不会触发迁移。
API 与 Worker 的数据库调用可复用 `app.db.session` 的引擎和会话工厂，
由业务服务使用 `session_factory.begin()` 管理写事务。

变更模型后，对已升级至当前版本的本地库运行
`python -m alembic -x db_path=./.venv/acbooks-dev.db revision --autogenerate -m "描述变更"`。
审查生成的脚本，并在旧数据副本验证升级与完整性。涉及 SQLite batch 重建时，
特别检查入向外键和级联行为。初始迁移的 `downgrade base` 会删除业务表及其数据，
仅在可丢弃的测试库验证。真实库升级前使用 SQLite 一致性备份。

## 检查

本地与 CI 的环境及覆盖范围见[自动化检查](../../docs/testing/strategy.md)。

```sh
python -m ruff check .
python -m mypy app tests
python -m pytest
```
