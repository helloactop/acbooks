# 数据库备份与恢复

acbooks 内置 `app.modules.backup`，使用 SQLite Online Backup API 生成一致快照。
备份在事务边界上复制数据库，不直接拷贝正在写入的 `.db` 文件，因此不依赖 WAL
是否已合并；完成后执行 `PRAGMA integrity_check`、`PRAGMA foreign_key_check` 并记录
Alembic revision。每个备份旁生成同名 `.json` 清单，包含大小、SHA-256、schema 版本和
创建时间；恢复前会核对清单，避免使用被截断或混入其他版本的备份。

备份只包含 SQLite 数据库。`storage_roots` 指向的电子书与 `covers/` 封面仍需按 NAS
快照或文件备份单独保护；`.env`、反向代理证书和 `/data/acbooks/secrets/` 也应一并
备份。为避免数据库与文件不一致，恢复前会校验备份库引用的 **managed** 电子书与封面
文件是否存在于当前文件系统：存在缺失时默认拒绝恢复（`external` 根文件可能离线，
不计入）；确认另行恢复或接受缺失时可用 `--allow-missing-files` 放行。

## 创建备份

本机运行（默认数据库路径 `/data/acbooks/database/acbooks.db`）：

```sh
python -m app.modules.backup backup --retain-days 7
python -m app.modules.backup verify /data/acbooks/backups/acbooks-20260101T000000Z.db
```

Docker 部署（API 与 Worker 可保持运行，备份使用在线 API）：

```sh
docker compose -f deploy/compose.yaml exec -T api \
  python -m app.modules.backup backup --retain-days 7
```

省略 `--output` 时写入数据库目录同级的 `backups/`，文件名带 UTC 时间戳（含微秒）。
已存在的目标文件默认拒绝覆盖；确需覆盖时显式传入 `--overwrite`，避免每日任务
反复写同一个文件而丢失历史快照。

`--retain-days N` 在备份成功后删除同目录内由本工具生成、清单创建时间早于 N 天的
备份及其清单，仅处理带有 `.json` 清单的文件，不会删除手写副本或其他文件。
命令输出清单 JSON 和 `pruned` 列表，可直接交给外部监控。建议保留至少最近 7 天与
每月一份长期备份，并把备份复制到另一块磁盘或远端存储。

备份与恢复共享 `<数据库名>.backup.lock` 互斥锁，同一数据库不会被两个进程同时
备份或恢复。临时文件位于目标目录并使用唯一名称，失败时自动清理；备份路径、
清单路径和恢复目标不得指向数据库本身或其 `-wal`/`-shm`/`-journal` 文件。

## 校验

```sh
python -m app.modules.backup verify /data/acbooks/backups/acbooks.db
```

也可用 `--manifest` 指定清单路径。校验会拒绝损坏的数据库、外键不一致、
缺少 Alembic 版本或与清单不匹配的文件。

## 恢复

恢复会替换整个数据库，必须先停止 API 与 Worker，避免连接继续写入。
`--offline` 是显式确认；缺少该参数时命令拒绝执行：

```sh
docker compose -f deploy/compose.yaml stop api worker
docker compose -f deploy/compose.yaml run --rm --no-deps api \
  python -m app.modules.backup restore /data/acbooks/backups/acbooks-20260101T000000Z.db --offline
docker compose -f deploy/compose.yaml up -d --wait
curl -fsS http://127.0.0.1:8000/api/v1/ready
```

`restore` 的行为：

1. 校验备份及清单；备份 schema 比当前代码新时拒绝恢复，提示先升级服务端。
2. 校验路径没有指向数据库本身或其日志文件，并取得数据库互斥锁；缺少 `--offline` 时拒绝。
3. 恢复前若目标数据库旁存在 `-wal`/`-shm`，视为仍有 API/Worker 连接并拒绝恢复；
   随后执行 `wal_checkpoint(TRUNCATE)` 和 `BEGIN EXCLUSIVE` 探测，忙碌时同样拒绝。
4. 不删除任何 WAL 文件；检查点后若日志仍存在，继续保持拒绝，需先停止服务。
   进程崩溃遗留的日志应先停止服务并手动确认/检查点，避免丢失已提交数据。
5. 若目标数据库存在，先创建 `<数据库名>.pre-restore-<UTC 时间>` 快照及清单。
6. 通过 SQLite Backup API 写入唯一临时文件，再次校验后原子替换数据库。
7. 备份版本较旧时可直接恢复，随后由 `migrate` 升级；升级前仍应先创建新备份。

恢复前还会核对备份库引用的 managed 电子书与封面文件：`--covers` 可指定封面目录
（默认由数据库路径推导为同级的 `covers/`）；存在缺失时默认拒绝恢复，
`--allow-missing-files` 可显式放行（仅在你已另行恢复文件或接受缺失时使用）。

恢复后检查 `/api/v1/ready` 为 `200`，登录并抽查书库、阅读记录与实体副本；
如结果不符合预期，可用 `pre-restore` 快照按同样步骤回退。

## 权限与自动化

备份目录位于 `/data/acbooks/backups/`，由服务用户 `10001:10001` 写入，目录权限
`0750`、文件权限跟随进程 umask。宿主机定时任务应以后端服务用户或通过
`docker compose ... run --no-deps` 执行，避免以 root 生成不可读文件。

最小自动化示例（宿主机 cron，每天 03:00）：

```cron
0 3 * * * cd /srv/acbooks && docker compose -f deploy/compose.yaml run --rm --no-deps api \
  python -m app.modules.backup backup --retain-days 7
```

省略 `--output` 会按 UTC 时间生成唯一文件名；不要改用固定的 `daily.db`，
否则第二次运行会被拒绝覆盖或需要 `--overwrite` 而丢掉历史快照。

失败时检查退出码与 stderr；备份或校验返回非零不得视为成功。
