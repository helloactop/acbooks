# 自动化检查

GitHub Actions 在分支推送、Pull Request 或手动触发时运行
[CI 工作流](../../.github/workflows/ci.yml)。后端在 Ubuntu 和 Windows
上以最低支持版本 Python 3.12 运行相同的检查。两个平台分别报告结果，
某个平台失败时，另一个平台继续运行。

## 本地运行

在独立的 Python 3.12 虚拟环境中，从 `apps/acbooks-server/` 执行以下命令。

```sh
python -m pip install -e ".[dev]"
python -m ruff check .
python -m mypy app tests
python -m pytest
```

## 当前覆盖范围

| 检查 | 覆盖范围 |
| --- | --- |
| Ruff | 后端 Python 代码的错误、未使用导入和导入排序 |
| mypy | 后端应用及测试代码的严格类型检查 |
| pytest | FastAPI 配置、健康检查、OpenAPI、错误响应和请求 ID |
| pytest | SQLite 临时文件库上的 Alembic 升降级、模型一致性、异常回滚重试、连接参数、外键和唯一约束、并发写入及在线备份恢复 |

测试使用 pytest 的临时目录创建数据库，不需要真实书库、NAS 挂载或服务凭据。
Web、Windows 客户端和 Android 工程初始化后，再接入各自的构建与检查。
NAS 部署、外部书库扫描和各端联调随对应功能添加验收用例。
