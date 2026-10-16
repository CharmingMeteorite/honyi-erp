# Phase 10D 验证记录

日期：2026-10-15，工作区 `F:\codexerp`。从干净的 Phase 10C 提交 `2aaae9b8c756c96f6ae104c4f704e10284733226` 建立 `feature/phase10d-deployment-operations`；远端 Phase 10C HEAD 与该提交一致。没有触碰 main。Phase 10D 只修改构建、静态路由、健康/日志及运维资产；V1～V18 Migration 未修改，也未新增 V19。

## 真实环境核对

- 本机 Java 默认 26；使用实际安装的 Java 17.0.19 构建和运行。Node.js 24.18.0、npm 12.2.0、Vite 8.2.2、MySQL 客户端 8.0.46。
- 只连接指定 `192.168.1.247:3307/erp`。`flyway:validate/info` 成功；只读 SQL 确认 MySQL 8.0.46、18 条成功 Migration、最高 V18、`sys_attachments`/`sys_notifications` 两表存在。实际 JAR 启动日志再次显示 18 项校验成功、当前 V18、无待执行迁移。
- 本地忽略的 `.env` 指向已有 MinIO 服务的 `erp` 私有 bucket。完整测试启用真实 MinIO 相关用例；JAR 的 `/health/ready` 实际返回 database=UP、objectStorage=UP。没有新建 MinIO、公开 bucket 或变更其他项目对象。
- `scripts/backup-mysql.ps1` 对指定库实际执行 MySQL 8.0.46 `mysqldump`，生成 Git 忽略的 `.local/backups/erp-20261015-115750.sql`，110040 字节；完成标记、Flyway 历史及 SHA-256 `D079FBBC17D7FB945B0B136D466168970485DCA0C6735AAA63B72B8842E774F1` 均已检查。没有隔离恢复实例，未执行恢复演练。

## 构建与回归

运行 `scripts/build-release.ps1`：`npm ci` PASS；`npm run build` PASS，生成 `dist/index.html`、JS、CSS、favicon；现有 JS chunk 大于 500 kB 的 Vite 警告仍在。完整 `mvnw.cmd test` PASS，181 项、0 失败/错误/跳过；`mvnw.cmd clean package` PASS，再运行 181 项、0 失败/错误/跳过。环境中 `ERP_MYSQL_TEST=true`、`ERP_MINIO_TEST=true`，Phase 4～10C 当前相关测试均由全量回归执行，Phase 10C 的 9 项测试包括真实对象上传、下载、删除、回滚补偿及权限，并验证匿名直接访问对象返回 403。新增的 SPA/API 404、readiness 和 request_id 清理断言也通过。lint：NOT CONFIGURED。

发布 JAR 约 69.5 MB，已直接读取 ZIP 条目及 Manifest：有 `JarLauncher`、`ErpApplication.class`、依赖、`static/index.html`、构建后的 JS/CSS 和 favicon。前端构建未使用固定本机 API 地址，服务端凭据没有进入前端资源。最终发布 JAR 的实际文件名、字节数及 SHA-256 以本轮最终构建输出和交付报告为准；JAR 留在本地 `.local/releases/`，不提交 Git。

## 实际 JAR 启动探测

使用 Java 17 将本次 JAR 以 `prod` profile 在本机 18080 端口启动，不连接远程服务器。`/`、`/login`、`/assets/devices`、`/reports`、`/assets/qr` 返回 HTML 200；首页引用的 JS 和 CSS 分别返回 200 及正确资源类型。`/health/live` 200，`/health/ready` 200 且 MySQL/MinIO 均 UP；未认证 `/api/auth/me` 返回 JSON 401，未知 `/api/missing-phase10d` 返回 JSON 404，缺失 JS 返回 404，未被 SPA 回退吞掉。核对进程命令行与临时端口后，仅停止本次烟雾测试 PID。上述是 HTTP 探测；未执行浏览器 E2E、真实打印机或目标服务器部署。

测试后只读 SQL 再次确认 Flyway 18 条成功，`sys_attachments`、`sys_notifications` 均为 0；`P10C_`/`P10D_` 前缀用户、产品、设备均为 0。测试自身仅操作其明确 fixture；没有清库或删除业务数据。MinIO 对象由现有集成测试按本轮随机键清理；没有对 bucket 作全量清理。

## 尚未执行及限制

无已确认的服务器、域名、证书、维护窗口、生产数据库或隔离恢复目标，因此未执行远程/生产部署、TLS、正式 MySQL 恢复、MinIO 对象备份或恢复演练。当前机器未安装 `mc` 客户端，也未确认 MinIO 宿主存储位置；对象备份需要存储管理员提供机制。没有病毒扫描、后台对象一致性修复、日志平台或告警平台。前端 chunk 体积提示仍在，浏览器 E2E 未执行。所有环境结果仅代表上述指定开发库、MinIO 和本机 JAR 烟雾测试。

## 2026-10-16 外部配置交付复验

针对容器日志中的 `jdbcUrl, ${DB_URL}` 启动失败，发布脚本现从 Git 忽略的本地 `.env` 生成独立的 `config/application.properties`。该文件包含真实凭据，仅供手动安全上传，不进入 JAR 或 Git。实测仅在工作目录放置此文件时，应用未加载数据库配置而启动失败；通过 `SPRING_CONFIG_ADDITIONAL_LOCATION=file:./config/application.properties` 和 `SPRING_PROFILES_ACTIVE=prod` 显式指定后，Java 17 JAR 启动成功，日志显示 `prod`、Flyway 成功校验 18 项 Migration，`/health/ready` 返回 200，MySQL 与 MinIO 均为 UP。使用等效 Java 启动参数也通过。只停止本次烟雾测试进程；尚未连接或改动用户服务器。
