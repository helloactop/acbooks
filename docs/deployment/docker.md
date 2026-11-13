# Docker Compose 部署

在 Linux 或 NAS 上使用 Docker Engine 和 Compose V2。从仓库根目录执行下列命令。
API、Worker 和迁移服务使用同一 Linux 镜像，容器进程以 UID/GID `10001:10001` 运行。
镜像先在 Node 阶段构建 Web，再由 Python 运行时托管 API、Worker 与静态文件；
基础镜像同时支持 AMD64 和 ARM64。

```sh
docker compose -f deploy/compose.yaml up -d --build --wait
docker compose -f deploy/compose.yaml ps -a
curl -fsS http://127.0.0.1:8000/api/v1/health
curl -fsS http://127.0.0.1:8000/api/v1/ready
```

浏览器直接访问 `http://127.0.0.1:8000`（或反向代理后的 HTTPS 来源）。
Web 静态文件和 `/api/v1` 由同一 FastAPI 进程提供，`/library`、`/settings` 等
前端深链接回退到 `index.html`，`/api` 路径不会回退。由于前后端同源，
Cookie 会话和 CSRF 来源校验保持现有行为，不需要 CORS 或额外的前端容器。

`/api/v1/health` 只表示进程可处理请求；`/api/v1/ready` 还要求数据库可连接且迁移
已升级到当前版本，数据库或迁移异常时返回 `503`。Compose 健康检查使用 `/ready`，
外部探针也应使用它判断是否接入流量。

`migrate` 先执行 `alembic upgrade head`，并为尚未初始化的数据库准备一次性初始化码。
它成功退出后，Compose 才会启动 API 和 Worker。预期状态为 `migrate` 退出码 0、
`api` 和 `worker` 健康。Worker 获取排他锁并检查任务表后才通过健康探针，
`up --wait` 会等待该探针通过。
当前 Worker 只提供进程生命周期，扫描、导入等任务处理器接入后才会消费队列。
如果启动失败，运行 `docker compose -f deploy/compose.yaml logs migrate api worker` 查看原因。

## 多架构构建

Compose 默认构建当前主机架构。需要同时产出 AMD64/ARM64 镜像时：

```sh
docker buildx build --platform linux/amd64,linux/arm64 \
  -f deploy/Dockerfile -t acbooks-server:local --load .
```

`--load` 一次只能加载单一平台；发布多架构清单请改用 `--push` 到镜像仓库。
CI 在 AMD64 与 ARM64 运行器上分别执行真实的 Compose 部署验收。

## 持久化目录

默认使用 Docker 管理的本地卷，三个服务共享容器内的 `/data/acbooks`。
SQLite 和 Worker 锁位于 `database/`，初始化码位于 `secrets/`，内置备份默认写入 `backups/`。
常规的 `docker compose down` 保留卷，`down --volumes` 会删除命名卷及库内数据。
活动 SQLite 数据库须使用本机文件系统。

如需把持久化目录放在 NAS 的本地磁盘上，将 `ACBOOKS_DATA_DIR` 设为宿主机绝对路径。
绑定目录会覆盖镜像中的预置目录。首次使用空目录时，先以宿主管理员身份建立目录和权限。

```sh
export ACBOOKS_DATA_DIR=/srv/acbooks
sudo install -d -m 0750 -o 10001 -g 10001 "$ACBOOKS_DATA_DIR"
for directory in database library covers cache backups logs; do
  sudo install -d -m 0750 -o 10001 -g 10001 "$ACBOOKS_DATA_DIR/$directory"
done
sudo install -d -m 0700 -o 10001 -g 10001 "$ACBOOKS_DATA_DIR/secrets"
```

## 首次初始化与 HTTPS

API 默认发布到宿主机的 `127.0.0.1:8000`。可用 `ACBOOKS_PORT` 修改宿主机端口，
用 `ACBOOKS_BIND_ADDRESS` 指定监听地址。上述配置可放在仓库根目录的本地 `.env`。
容器外的首次初始化应经 HTTPS 反向代理访问，将 `ACBOOKS_PUBLIC_ORIGIN` 设为浏览器
实际访问的 HTTPS 来源，并将 `ACBOOKS_FORWARDED_ALLOW_IPS` 设为 API 容器看到的
受信代理地址。代理应传递 `X-Forwarded-Proto: https`，并把所有路径（包括 `/api`）
转发到同一个 API 端口。

宿主管理员可在受信终端读取初始化码，随后按 [API 说明](../api/conventions.md#41-初始化与登录路由)提交。

```sh
docker compose -f deploy/compose.yaml exec -T api cat /data/acbooks/secrets/setup-code
```

## 备份与升级

升级前先停止写入，并按[数据库备份与恢复](backup.md)创建一致性备份：

```sh
docker compose -f deploy/compose.yaml stop api worker
docker compose -f deploy/compose.yaml run --rm --no-deps api \
  python -m app.modules.backup backup --retain-days 7
```

保留原数据卷和 Compose 项目名，再执行升级。`migrate` 会应用新迁移；若迁移拒绝
旧数据会整体回滚，此时按[备份与恢复](backup.md)修正或恢复数据。

```sh
docker compose -f deploy/compose.yaml up -d --build --wait
```

恢复时保持 API 与 Worker 停止，使用带 `--offline` 的 `restore` 子命令；
该命令会检查数据库空闲、合并 WAL，并先为当前数据库创建 `pre-restore` 快照。
详见[数据库备份与恢复](backup.md)。
