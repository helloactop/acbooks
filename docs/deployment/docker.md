# Docker Compose 部署

在 Linux 或 NAS 上使用 Docker Engine 和 Compose V2。
API、Worker 和迁移服务使用同一 Linux 镜像，容器进程以 UID/GID `10001:10001` 运行。
镜像先在 Node 阶段构建 Web，再由 Python 运行时托管 API、Worker 与静态文件；
基础镜像同时支持 AMD64 和 ARM64。

## 从 GHCR 部署

仓库默认分支中的 **Publish Server Image** 工作流可在 GitHub **Actions** 手动运行。
工作流分别在 AMD64 和 ARM64 运行器构建服务端镜像，两个构建均成功后发布统一镜像
`ghcr.io/helloactop/acbooks-server:sha-<提交 SHA>`。运行摘要提供可直接复制到 `.env` 的
`ACBOOKS_IMAGE` 和镜像清单摘要。发布使用 Actions 自带的 `GITHUB_TOKEN`。

在 VPS 的部署目录保存与本次发布对应的 `deploy/compose.image.yaml`，命名为 `compose.yaml`。
同目录创建 `.env`，把 Actions 摘要里的 `ACBOOKS_IMAGE` 整行复制进去。按实际 HTTPS 域名
填写 `ACBOOKS_PUBLIC_ORIGIN` 并配置反向代理及其受信地址。若要按字节固定一个发布版本，
可使用 `ghcr.io/helloactop/acbooks-server@sha256:<清单摘要>` 作为 `ACBOOKS_IMAGE`。

公开镜像可直接拉取。私有镜像由有访问权限的 GitHub 账号使用带 `read:packages` 权限的
classic PAT 登录 `ghcr.io`，通过 `docker login --password-stdin` 传入令牌。

```sh
docker compose pull
docker compose up -d --wait
curl -fsS http://127.0.0.1:8000/api/v1/ready
```

后续升级先按[备份与升级](#备份与升级)备份，再把 `.env` 的 `ACBOOKS_IMAGE` 改为新版本，
重新执行拉取和启动命令。保留原 Compose 项目名和数据卷或绑定目录。

## 从源码构建

从仓库根目录运行。

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

API 必须保持**单 Worker**：CSRF 挑战签名和登录限速状态保存在进程内，多 Worker
会导致校验随机失败。Compose 已固定 `--workers 1`，自行部署时不要调高。

`migrate` 先执行 `alembic upgrade head`。首次初始化不再需要初始化码。
它成功退出后，Compose 才会启动 API 和 Worker。预期状态为 `migrate` 退出码 0、
`api` 和 `worker` 健康。Worker 获取排他锁并检查任务表后才通过健康探针，
`up --wait` 会等待该探针通过。
当前 Worker 只提供进程生命周期，扫描、导入等任务处理器接入后才会消费队列。
如果启动失败，在镜像部署目录运行 `docker compose logs migrate api worker`。
从源码部署时使用 `docker compose -f deploy/compose.yaml logs migrate api worker`。

## 多架构构建

Compose 默认构建当前主机架构。也可以构建单一架构的本地镜像。

```sh
docker buildx build --platform linux/amd64 \
  -f deploy/Dockerfile -t acbooks-server:local --load .
```

多架构镜像由发布工作流生成。CI 在 AMD64 与 ARM64 运行器上分别执行 Compose 部署验收。

## 持久化目录

默认使用 Docker 管理的本地卷，三个服务共享容器内的 `/data/acbooks`。
SQLite 和 Worker 锁位于 `database/`，头像位于 `avatars/`，内置备份默认写入 `backups/`。
常规的 `docker compose down` 保留卷，`down --volumes` 会删除命名卷及库内数据。
活动 SQLite 数据库须使用本机文件系统。

如需把持久化目录放在 NAS 的本地磁盘上，将 `ACBOOKS_DATA_DIR` 设为宿主机绝对路径。
绑定目录会覆盖镜像中的预置目录。首次使用空目录时，先以宿主管理员身份建立目录和权限。

```sh
export ACBOOKS_DATA_DIR=/srv/acbooks
sudo install -d -m 0750 -o 10001 -g 10001 "$ACBOOKS_DATA_DIR"
for directory in database library covers avatars cache backups logs; do
  sudo install -d -m 0750 -o 10001 -g 10001 "$ACBOOKS_DATA_DIR/$directory"
done
sudo install -d -m 0700 -o 10001 -g 10001 "$ACBOOKS_DATA_DIR/secrets"
```

## 首次初始化与 HTTPS

API 默认发布到宿主机的 `127.0.0.1:8000`。可用 `ACBOOKS_PORT` 修改宿主机端口，
用 `ACBOOKS_BIND_ADDRESS` 指定监听地址。镜像部署将配置放在 Compose 文件同目录的 `.env`，
源码部署使用仓库根目录的 `.env`。
容器外的首次初始化应经 HTTPS 反向代理访问，将 `ACBOOKS_PUBLIC_ORIGIN` 设为浏览器
实际访问的 HTTPS 来源，并将 `ACBOOKS_FORWARDED_ALLOW_IPS` 设为 API 容器看到的
受信代理地址。代理应传递 `X-Forwarded-Proto: https`，并把所有路径（包括 `/api`）
转发到同一个 API 端口。

首次打开页面后直接填写用户名、密码和确认密码即可完成初始化，不需要初始化码。
未初始化实例必须先限制在 localhost 或可信内网，完成初始化并登录验证后再对外开放。

## 备份与升级

升级前先停止写入，并按[数据库备份与恢复](backup.md)创建一致性备份。
在镜像部署目录执行下列备份命令时，`.env` 仍使用当前镜像版本。

```sh
docker compose stop api worker
docker compose run --rm --no-deps api \
  python -m app.modules.backup backup --retain-days 7
```

备份完成后更新镜像和配置，执行 `docker compose pull` 与 `docker compose up -d --wait`。
若需要回退到数据库迁移前的版本，使用与旧版本匹配的备份和镜像。

从源码部署时，在仓库根目录执行备份。

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
