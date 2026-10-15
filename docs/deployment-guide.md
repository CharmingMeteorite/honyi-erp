# Phase 10D：单 JAR 手动部署

## 架构与构建

Spring Boot 4.1.1 使用 Java 17。`erp/pom.xml` 将 `erp-vue/dist` 作为额外 Maven 资源放到 `BOOT-INF/classes/static/`；Boot JAR 同时提供页面、静态文件和 `/api/**`。Vue 使用 History 路由及同源相对 API 路径，已有前端路由由 `SpaController` 转发到 `index.html`；未知 API 和缺失静态资源保留 404。MySQL、私有 MinIO 及密钥都在 JAR 外。没有 Nginx、Docker 或独立前端服务要求。

在 Windows PowerShell 中安装 JDK 17、Node.js 22.18+、npm，并让 `JAVA_HOME` 指向 JDK 17。根目录 Git 忽略的 `.env` 可供本机构建测试使用；构建脚本仅接受指定的开发数据库 `192.168.1.247:3307/erp`，需要真实 MySQL/MinIO 测试配置。先核对该文件实际目标和权限，再执行：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
.\scripts\build-release.ps1
```

脚本先 `npm ci`、`npm run build`，再运行 Flyway validate、完整 Maven test 和 `mvnw.cmd clean package`（package 再运行测试），检查 JAR 可执行入口、后端类、依赖及前端 HTML/JS/CSS。失败即停止；发布文件仅放在 Git 忽略的 `.local/releases/`，输出绝对路径、字节数、Git 短提交与 SHA-256。不可直接跳过前端构建后使用旧 `target/`。前端大 chunk 警告不影响构建，但应列入后续性能优化。

## 上传前核对与配置

目标服务器、域名、TLS、发布窗口尚未指定；本仓库不执行远程连接或部署。手动确认目标 Java 17、可用端口、MySQL/MinIO 的实际目标与权限、ERP 专属私有 bucket、磁盘空间和上版 JAR/配置留存。对发布文件再次核对路径、大小、SHA-256、Git 提交；上传后在服务器重新计算 SHA-256 并比较。不要把本机 `.env` 原样带到其他环境。

配置项见根目录 `.env.release.example`，该文件只有占位值。真正配置必须放在工作区外且仅部署账号可读。必填：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`MINIO_ENDPOINT`、`MINIO_BUCKET`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`；常用：`SPRING_PROFILES_ACTIVE=prod`、`SERVER_PORT`、`ERP_LOG_DIR`、`DB_CONNECTION_TIMEOUT_MS`。保留 `FLYWAY_ENABLED=true`；管理账号种子变量正常上线应为空。Spring Boot 不会自动读取根目录 `.env`，必须注入进程环境或使用受限的外部 Spring 配置。不要把服务端密钥设成 `VITE_*` 前端变量。

Windows 手动前台启动示例（先由受限的本地配置或密钥管理系统给当前 PowerShell 会话设置上述变量）：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:SERVER_PORT = '8080'
$env:ERP_LOG_DIR = 'C:\erp\logs'
& "$env:JAVA_HOME\bin\java.exe" -jar 'C:\erp\releases\erp.jar'
```

Linux 手动前台启动示例（先通过受限的环境配置为当前 shell 设置上述变量）：

```sh
export SPRING_PROFILES_ACTIVE=prod SERVER_PORT=8080 ERP_LOG_DIR=/opt/erp/logs
java -jar /opt/erp/releases/erp.jar
```

示例 JAR 路径需替换成实际上传路径，Java 17 必须在 PATH。前台运行可用 Ctrl+C 正常停止；如使用系统服务管理器，只停止该 ERP 实例，不按名称杀所有 Java 进程。重新启动时先确认旧进程退出及端口空闲，再用同一组外部配置启动新 JAR。生产外部 HTTPS/TLS、进程托管和访问范围须由实际服务器环境确定；本阶段未配置或验证。

## 启动、升级和回滚

启动时 Flyway 从 JAR 内 `db/migration` 校验 checksum 并按顺序应用待执行迁移；`baseline-on-migrate=false`，校验失败会阻止应用启动，不得 repair、清库或关闭校验绕过。当前 Phase 10D 不增加 Migration，开发库最高 V18。未来新增迁移时，先核对目标、执行并验证数据库及对象备份，评估新旧 JAR 与数据库兼容性；回滚 JAR 不等于回滚数据库。

发布顺序：记录现行提交/配置 → 构建和测试 → 核对目标与备份 → 上传 JAR 并比对 SHA-256 → 停止指定旧实例 → 启动新实例 → 检查 `/health/live`、`/health/ready`、首页、深层路由、静态文件、登录及权限、附件、通知、报表/Excel → 观察日志。失败时保留证据，按原因恢复上版 JAR 或配置；涉及数据库/对象数据时按独立恢复 Runbook 处理，不在当前开发库演练覆盖。详见 [operations-runbook.md](operations-runbook.md)、[database-backup-restore.md](database-backup-restore.md)、[minio-backup-restore.md](minio-backup-restore.md)。
