# ERP 单 JAR 运维与排障

`GET /health/live` 返回 200 和 `status=UP`，只说明进程可响应；`GET /health/ready` 用 `SELECT 1` 测数据库，使用只读 bucket 存在性检查测 MinIO，全部可用返回 200，否则返回 503，响应只含 `UP/DOWN`，无连接串或密钥。原 `GET /health` 保留兼容入口，等同简单存活探测。依赖探测请求不扫描业务表，也不创建 bucket；不要用 `/health/live` 代替上线验收。

生产 profile 将日志写入 `${ERP_LOG_DIR}/erp.log`，10 MB 轮转、最多保留 30 天且总量最多 1 GB；启动前给部署账号日志目录写权限并监控剩余空间。响应携带 `X-Request-Id`；用该 ID 对照客户端错误与服务日志。不得把 Authorization、数据库/MinIO 密钥、预签名 URL、文件正文或整个请求体加入日志。现有业务异常经统一处理返回用户提示，未处理异常只在服务端记录堆栈。

只读检查：Windows 使用 `Get-Process java`、`Get-NetTCPConnection -LocalPort <port>`、`Get-Content C:\erp\logs\erp.log -Tail 100`、`Invoke-WebRequest http://<host>:<port>/health/ready`；Linux 使用 `ps -ef | grep '[j]ava -jar'`、`ss -ltnp`、`tail -n 100 /opt/erp/logs/erp.log`、`curl -i http://<host>:<port>/health/ready`。替换尖括号参数后才执行，不要在共享终端输出秘密环境变量。

| 现象 | 只读排查顺序与安全处理 |
| --- | --- |
| JAR 不启动或 Java 报版本错 | `java -version` 应为 17；`jar tf <jar>` 确认入口和静态资源；核对 SHA-256，换用正确 JDK/JAR。 |
| 日志出现 `Driver ... does not accept jdbcUrl, ${DB_URL}` | 应用没有得到有效数据库配置。核对容器内 `/app/config/application.properties` 是否为只读文件，并核对 `SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/config/application.properties` 是否传入 ERP 进程；再确认启动日志为 `prod` profile。不要在日志中输出配置文件内容或密码。 |
| 端口冲突 | 查询该端口进程及归属；确认是否本 ERP 旧实例，只停止经确认的实例，或选择已批准端口。 |
| 数据库拒绝连接、权限或 Flyway 失败 | 核对目标地址、账号授权和网络；查看启动日志中的错误类型；只读检查 `flyway_schema_history`、`flyway:validate/info`。禁止 repair、清库、改 checksum。 |
| `/health/ready` 为 503 | 观察 `database` / `objectStorage` 哪项 DOWN；数据库查网络/账号，MinIO 查 endpoint、私有 ERP bucket 及授权。健康检查不负责建 bucket。 |
| 首页可开但 API 4xx/5xx | 确认浏览器与 API 同源，检查 `/api/auth/me` 的 401/403 是否符合登录与权限；用 `X-Request-Id` 找服务日志。未知 API 不应返回 HTML 200。 |
| 前端刷新深层路由失败或 JS/CSS 404 | 核对 URL 是否为已有 Vue 路由；检查 JAR 中 `BOOT-INF/classes/static/index.html` 和 `assets/`，比较上传前后 SHA-256。 |
| 附件上传可见但读取失败 | 只读核对附件元数据状态和对象键，再由管理员检查对应 bucket 对象；不得自动删除所谓孤儿对象。缺失对象或 `DELETE_PENDING` 按附件文档处理。 |
| 通知、报表或 Excel 下载异常 | 用本人权限检查相关 API、`Content-Type`、浏览器下载提示和 request ID；确认 MySQL、磁盘和真实业务数据范围。 |
| 日志或磁盘增长、配置更新不生效 | 只读检查日志目录容量、JAR SHA 和进程启动时间；清理前确认保留要求，重启只针对已确认 ERP 实例。 |

修改配置、停止服务、覆盖 JAR、恢复数据库或对象之前，先确认目标环境、维护窗口、上一版产物和有效备份。当前无日志平台、告警平台、病毒扫描或对象后台自动修复；生产 TLS 和服务托管也尚待目标服务器确定。
