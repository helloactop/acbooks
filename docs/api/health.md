# 后端健康检查

`GET /api/v1/health` 无需认证。HTTP 应用可处理请求时返回 `200` 和
`{"status":"ok"}`。这是存活探针，检查范围仅包含 HTTP 应用。

响应包含服务端生成的 `X-Request-ID`。错误响应采用[接口约定](conventions.md)中的
`application/problem+json`。请求校验失败使用 `VALIDATION_ERROR`，
未处理的服务端异常使用 `INTERNAL_ERROR`，标准 HTTP 错误使用状态名
（例如 `NOT_FOUND` 和 `METHOD_NOT_ALLOWED`）。

契约由 `/openapi.json` 发布，操作 ID 为 `getHealth`。启动本地后端后，
从仓库根目录重新生成 TypeScript 客户端类型。

```sh
npx --yes openapi-typescript@7.10.1 http://127.0.0.1:8000/openapi.json -o packages/acbooks-api-client/schema.d.ts
```
