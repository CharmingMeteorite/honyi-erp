# Phase 10D：单 JAR 手动部署

## 架构与构建

Spring Boot 4.1.1 使用 Java 17。`erp/pom.xml` 将 `erp-vue/dist` 作为额外 Maven 资源放到 `BOOT-INF/classes/static/`；Boot JAR 同时提供页面、静态文件和 `/api/**`。Vue 使用 History 路由及同源相对 API 路径，已有前端路由由 `SpaController` 转发到 `index.html`；未知 API 和缺失静态资源保留 404。MySQL、私有 MinIO 及密钥都在 JAR 外。没有 Nginx、Docker 或独立前端服务要求。

在 Windows PowerShell 中安装 JDK 17、Node.js 22.18+、npm，并让 `JAVA_HOME` 指向 JDK 17。根目录 Git 忽略的 `.env` 可供本机构建测试使用；构建脚本仅接受指定的开发数据库 `192.168.1.247:3307/erp`，需要真实 MySQL/MinIO 测试配置。先核对该文件实际目标和权限，再执行：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
.\scripts\build-release.ps1
```

脚本先 `npm ci`、`npm run build`，再运行 Flyway validate、完整 Maven test 和 `mvnw.cmd clean package`（package 再运行测试），检查 JAR 可执行入口、后端类、依赖及前端 HTML/JS/CSS。失败即停止；发布文件仅放在 Git 忽略的 `.local/releases/`，输出绝对路径、字节数、Git 短提交与 SHA-256。脚本还根据当前本地配置生成 `.local/releases/config/application.properties`，其中含真实数据库和 MinIO 凭据，必须当作密钥文件保管；它不会进入 JAR 或 Git。不可直接跳过前端构建后使用旧 `target/`。前端大 chunk 警告不影响构建，但应列入后续性能优化。

## 上传前核对与配置

当前已获授权验证的目标是 `192.168.1.247` 上的 1Panel `erp` 容器，主机端口 8090 映射到容器 8080；域名、TLS 和正式发布窗口仍未确认。本仓库不包含自动远程部署脚本。手动确认目标 Java 17、可用端口、MySQL/MinIO 的实际目标与权限、ERP 专属私有 bucket、磁盘空间和上版 JAR/配置留存。对发布文件再次核对路径、大小、SHA-256、Git 提交；上传后在服务器重新计算 SHA-256 并比较。不要把本机 `.env` 原样带到其他环境。

配置项见根目录 `.env.release.example`，该文件只有占位值。目标服务器上的真实配置必须置于 JAR 外的非公开、受限目录，且仅部署账号可读；本机构建生成的配置仅在 Git 忽略的 `.local/releases/config/` 暂存。必填：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`MINIO_ENDPOINT`、`MINIO_BUCKET`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`；常用：`SPRING_PROFILES_ACTIVE=prod`、`SERVER_PORT`、`ERP_LOG_DIR`、`DB_CONNECTION_TIMEOUT_MS`。保留 `FLYWAY_ENABLED=true`；管理账号种子变量正常上线应为空。Spring Boot 不会自动读取根目录 `.env`，必须注入进程环境或使用受限的外部 Spring 配置。不要把服务端密钥设成 `VITE_*` 前端变量。

当前 1Panel 容器已把 `/home/hongyi/erp` 挂载为 `/app`，因此配置文件放在宿主机 `/home/hongyi/erp/config/application.properties` 即可由容器内 `/app/config/application.properties` 读取。宿主机 `config/` 权限为 700，文件权限为 600。1Panel 的 `/opt/1panel/runtime/java/erp/run.sh` 从 `/.env` 读取 `EXEC_SCRIPT`；当前在 `/opt/1panel/runtime/java/erp/.env` 中使用以下**不含密钥**的启动命令，无须为此修改 Compose 挂载：

```sh
EXEC_SCRIPT="java -jar erp.jar --spring.config.additional-location=file:/app/config/application.properties --spring.profiles.active=prod"
```

变更 1Panel 的启动设置前先备份原 `.env`；若以后通过 1Panel 界面重新生成运行配置，应检查这些启动参数是否仍在。实测仅把文件放在工作目录下的 `config/` 并不能让该 JAR 正确加载它。其他已有容器若只挂载 JAR，可把配置文件单独只读挂载到 `/app/config/application.properties`，并设置：

```yaml
services:
  erp:
    volumes:
      - /实际服务器安全路径/application.properties:/app/config/application.properties:ro
    environment:
      SPRING_CONFIG_ADDITIONAL_LOCATION: file:/app/config/application.properties
      SPRING_PROFILES_ACTIVE: prod
```

这个 Compose 片段是其他容器的示例，不创建 MySQL/MinIO 容器。若 Compose 已设置 `SPRING_PROFILES_ACTIVE=dev`，须将其改为 `prod`；不要保留相互冲突的设置。上传前确认生成文件里的数据库和 bucket 正是该服务器应连接的目标；本地文件来自指定开发库，不能未经核对就当作生产配置。若新增 Compose 挂载，须重建**该 ERP 服务容器**，仅重启旧容器不会改变挂载。启动日志应显示 `prod` profile，并应有 Flyway V18 校验成功记录；随后检查 `/health/ready`。不要把配置文件复制进镜像、JAR 或公开目录，也不要用会输出文件内容的命令排查。

非容器场景可直接指定外部文件，不依赖当前工作目录。例如先把文件复制到受限目录，再用 Java 17 运行：

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar 'C:\erp\releases\erp.jar' --spring.config.additional-location=file:C:/erp/secure/application.properties --spring.profiles.active=prod
```

```sh
java -jar /opt/erp/releases/erp.jar --spring.config.additional-location=file:/etc/erp/application.properties --spring.profiles.active=prod
```

Windows 手动前台启动示例。先把 `.env.release.example` 中的占位值替换成真实目标配置，保存到仓库外的 `C:\erp\secure\erp.env`，仅授予部署账号读取权限；以下命令只给当前 PowerShell 进程及其子进程注入变量，不打印值：

```powershell
Get-Content 'C:\erp\secure\erp.env' | ForEach-Object {
    if ($_ -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}
& "$env:JAVA_HOME\bin\java.exe" -jar 'C:\erp\releases\erp.jar'
```

Linux 手动前台启动示例。将配置文件放在 `/etc/erp/erp.env`，归部署账号所有并设置 `chmod 600`；如下读取 `KEY=value` 原文，不执行配置内容：

```sh
while IFS='=' read -r key value; do
  case "$key" in ''|\#*) continue ;; esac
  export "$key=$value"
done < /etc/erp/erp.env
java -jar /opt/erp/releases/erp.jar
```

示例 JAR 路径需替换成实际上传路径，`SPRING_PROFILES_ACTIVE=prod`、`DB_*`、`MINIO_*` 等值均从外部文件获取；Windows 的 `JAVA_HOME` 应指向 JDK 17，Linux 的 `java` 应为 Java 17。这些环境文件不支持跨行值；遇到特殊字符仍以整行第一个 `=` 后的原文作为值。前台运行可用 Ctrl+C 正常停止；如使用系统服务管理器，只停止该 ERP 实例，不按名称杀所有 Java 进程。重新启动时先确认旧进程退出及端口空闲，再用同一组外部配置启动新 JAR。生产外部 HTTPS/TLS、进程托管和访问范围须由实际服务器环境确定；本阶段未配置或验证。

## 启动、升级和回滚

启动时 Flyway 从 JAR 内 `db/migration` 校验 checksum 并按顺序应用待执行迁移；`baseline-on-migrate=false`，校验失败会阻止应用启动，不得 repair、清库或关闭校验绕过。当前 Phase 10D 不增加 Migration，开发库最高 V18。未来新增迁移时，先核对目标、执行并验证数据库及对象备份，评估新旧 JAR 与数据库兼容性；回滚 JAR 不等于回滚数据库。

发布顺序：记录现行提交/配置 → 构建和测试 → 核对目标与备份 → 上传 JAR 并比对 SHA-256 → 停止指定旧实例 → 启动新实例 → 检查 `/health/live`、`/health/ready`、首页、深层路由、静态文件、登录及权限、附件、通知、报表/Excel → 观察日志。失败时保留证据，按原因恢复上版 JAR 或配置；涉及数据库/对象数据时按独立恢复 Runbook 处理，不在当前开发库演练覆盖。详见 [operations-runbook.md](operations-runbook.md)、[database-backup-restore.md](database-backup-restore.md)、[minio-backup-restore.md](minio-backup-restore.md)。
