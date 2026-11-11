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

## 检查

```sh
python -m pytest
python -m ruff check .
python -m mypy app tests
```
