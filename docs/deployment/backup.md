# 数据库备份与恢复

acbooks 内置 `app.modules.backup`，使用 SQLite Online Backup API 生成一致快照。
备份在事务边界上复制数据库，不直接拷贝正在写入的 `.db` 文件，因此不依赖 WAL
是否已合并；完成后执行 `PRAGMA integrity_check`、`PRAGMA foreign_key_check` 并记录
Alembic revision。每个备份旁生成同名 `.json` 清单，包含大小、SHA-256、schema 版本和
创建时间；恢复前会核对清单，避免使用被截断或混入其他版本的备份。

备份只包含 SQLite 数据库。`storage_roots` 指向的电子书、封面等文件仍需按 NAS
快照或文件备份单独保护；`.env`、反向代理证书和 `/data/acbooks/secrets/` 也应一并备份。

## 创建备份

本机运行（默认数据库路径 `/data/acbooks/database/acbooks.db`）：

```sh
python -m app.modules.backup backup --output /data/acbooks/backups/acbooks.db
python -m app.modules.backup verify /data/acbooks/backups/acbooks.db
```

Docker 部署（API 与 Worker 可保持运行，备份使用在线 API）：

```sh
docker compose -f deploy/compose.yaml exec -T api \
  python -m app.modules.backup backup --output /data/acbooks/backups/acbooks.db
```

省略 `--output` 时写入数据库目录同级的 `backups/`，文件名带 UTC 时间戳。
命令输出清单 JSON，可直接交给外部监控或保留期管理。建议保留至少最近 7 天与
每月一份长期备份，并把备份复制到另一块磁盘或远端存储。

## 校验

```sh
python -m app.modules.backup verify /data/acbooks/backups/acbooks.db
```

也可用 `--manifest` 指定清单路径。校验会拒绝损坏的数据库、外键不一致、
缺少 Alembic 版本或与清单不匹配的文件。

## 恢复

恢复会替换整个数据库，必须先停止 API 与 Worker，避免连接继续写入：

```sh
docker compose -f deploy/compose.yaml stop api worker
docker compose -f deploy/compose.yaml run --rm --no-deps api \
  python -m app.modules.backup restore /data/acbooks/backups/acbooks.db
docker compose -f deploy/compose.yaml up -d --wait
curl -fsS http://127.0.0.1:8000/api/v1/ready
```

`restore` 的行为：

1. 校验备份及清单；备份 schema 比当前代码新时拒绝恢复，提示先升级服务端。
2. 若目标数据库存在，先创建 `<数据库名>.pre-restore-<UTC 时间>` 快照及清单。
3. 通过 SQLite Backup API 写入临时文件，再次校验后原子替换数据库，并清理旧 `-wal`/`-shm`。
4. 备份版本较旧时可直接恢复，随后由 `migrate` 升级；升级前仍应先创建新备份。

恢复后检查 `/api/v1/ready` 为 `200`，登录并抽查书库、阅读记录与实体副本；
如结果不符合预期，可用 `pre-restore` 快照按同样步骤回退。

## 权限与自动化

备份目录位于 `/data/acbooks/backups/`，由服务用户 `10001:10001` 写入，目录权限
`0750`、文件权限跟随进程 umask。宿主机定时任务应以后端服务用户或通过
`docker compose ... run --no-deps` 执行，避免以 root 生成不可读文件。

最小自动化示例（宿主机 cron，每天 03:00）：

```cron
0 3 * * * cd /srv/acbooks && docker compose -f deploy/compose.yaml run --rm --no-deps api \
  python -m app.modules.backup backup --output /data/acbooks/backups/daily.db
```

失败时检查退出码与 stderr；备份或校验返回非零不得视为成功。
