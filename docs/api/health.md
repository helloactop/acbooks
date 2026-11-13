# 后端健康检查

## 存活探针

`GET /api/v1/health` 无需认证。HTTP 应用可处理请求时返回 `200` 和
`{"status":"ok"}`。这是存活探针，检查范围仅包含 HTTP 应用，不访问数据库。

## 就绪探针

`GET /api/v1/ready` 无需认证，检查数据库连接与迁移状态：

- 数据库可连接且 `alembic_version` 等于当前代码的 schema head 时，返回 `200`、
  `Cache-Control: no-store` 和 `{"status":"ok","migration":"current"}`。
- 数据库不可访问，或迁移未升级到当前版本时，返回 `503`，采用
  `application/problem+json`，`code` 为 `SERVICE_UNAVAILABLE`。

应用启动不会自动执行迁移；部署时由迁移服务先行执行 `alembic upgrade head`。
编排器或负载均衡可用 `/ready` 判断是否接入流量，仅判断进程存活时使用 `/health`。

响应包含服务端生成的 `X-Request-ID`。错误响应采用[接口约定](conventions.md)中的
`application/problem+json`。请求校验失败使用 `VALIDATION_ERROR`，
未处理的服务端异常使用 `INTERNAL_ERROR`，标准 HTTP 错误使用状态名
（例如 `NOT_FOUND` 和 `METHOD_NOT_ALLOWED`）。

契约由 `/openapi.json` 发布，操作 ID 为 `getHealth` 和 `getReadiness`。启动本地后端后，
从仓库根目录重新生成 TypeScript 客户端类型。

```sh
npx --yes openapi-typescript@7.10.1 http://127.0.0.1:8000/openapi.json -o packages/acbooks-api-client/schema.d.ts
```
