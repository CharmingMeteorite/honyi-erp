# ERP 内部物资管理系统

公司内部物资、库存、设备及生命周期管理系统。当前源码覆盖 Phase 0～10C；Phase 10D 将 Vue 生产产物和 Spring Boot 装入一个可执行 JAR，供手动上传与启动。实际验证见 [docs/phase10d-verification.md](docs/phase10d-verification.md)。

## 技术栈

- 后端：Java 17、Spring Boot 4.1.1、Spring MVC、MyBatis、Jakarta Validation、Lombok、MySQL 8
- 前端：Vue 3、JavaScript、Vite、Vue Router、Element Plus、原生 `fetch`
- 数据库：MySQL 8、InnoDB、utf8mb4、Flyway Migration

## 开发环境

需要 Java 17、Node.js 22+、npm、MySQL 8，以及使用附件时可访问的私有 MinIO。当前恢复版本唯一指定开发库为 `192.168.1.247:3307/erp`，用户名 `root`；通过本地 `.env` 或进程环境设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，不使用应用的默认本机连接。附件还需 `MINIO_ENDPOINT`、`MINIO_BUCKET`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`。`.env` 已被 Git 忽略，不要提交真实密码或密钥。已有本地配置时保留原文件；新环境才从 `.env.example` 创建配置并填写指定连接。

```powershell
Copy-Item .env.example .env
```

仓库仍保留 `docker/` 环境模板；当前阶段使用上述指定 MySQL，不启动或切换 Docker 数据库。Spring Boot 不会自动读取根目录 `.env` 文件，本地启动后端前需把应用连接配置加载到当前 shell 的进程环境。

## 后端

在 `erp` 目录执行：

```powershell
./mvnw.cmd test
./mvnw.cmd spring-boot:run
```

应用默认监听 `http://localhost:8080`。启动前必须提供 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`；不再回退到本机数据库。Flyway 启动时校验并执行 `src/main/resources/db/migration` 中的待执行迁移，禁用对非空库的自动 baseline。可通过环境变量 `ERP_SEED_ADMIN_USERNAME` 和 `ERP_SEED_ADMIN_PASSWORD` 初始化开发管理员；两个变量都为空时不会创建用户。

## 前端

在 `erp-vue` 目录执行：

```powershell
npm install
npm run dev
```

前端默认监听 `http://localhost:5173`，开发代理将 `/api` 和 `/health` 转发到后端。生产版使用同源 API，并由单 JAR 提供静态资源。

## 单 JAR 手动交付

在 Windows PowerShell 中用 Java 17、Node.js 22.18+ 运行 `./scripts/build-release.ps1`。脚本执行 `npm ci`、前端 build、指定开发库 Flyway 校验、完整 Maven 测试与 `clean package`，再核查 JAR 内容、SHA-256，将可上传的 JAR 放入 Git 忽略的 `.local/releases/`。生产配置使用外部环境变量；配置模板为 [.env.release.example](.env.release.example)。手动上传、启动、健康检查、备份和恢复步骤见 [部署指南](docs/deployment-guide.md) 与 [运维手册](docs/operations-runbook.md)。本仓库不自动远程部署服务器。

## Phase 0 / Phase 1 基础 API

- `GET /health`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/system/users`
- `GET /api/system/users/{id}`
- `POST /api/system/users`
- `PUT /api/system/users/{id}`
- `POST /api/system/users/{id}/enable|disable`
- `POST /api/system/users/{id}/reset-password`
- `PUT /api/system/users/{id}/roles`
- `GET /api/system/departments/tree`
- `POST /api/system/departments`
- `PUT /api/system/departments/{id}`
- `POST /api/system/departments/{id}/enable|disable`
- `GET /api/system/roles`
- `POST /api/system/roles`
- `PUT /api/system/roles/{id}`
- `POST /api/system/roles/{id}/enable|disable`
- `DELETE /api/system/roles/{id}`
- `PUT /api/system/roles/{id}/permissions`
- `GET /api/system/permissions/tree`

认证使用 Bearer Token，服务端只保存 Token 的 SHA-256 摘要。密码使用 BCrypt，响应统一为 `ApiResponse`。

## 目录结构

```text
erp/       Spring Boot 后端
erp-vue/   Vue 前端
database/  预留数据库文档目录
docker/    开发环境容器配置
docs/      编码规范、参考索引和数据库蓝图
```

## 测试与构建

后端使用 `./mvnw.cmd test` 和 `./mvnw.cmd package`；前端使用 `npm run build`。本仓库当前未配置独立 lint 命令，验证结果会明确标记为 `NOT CONFIGURED`。需要真实 MySQL 才能验证 Migration 执行结果。

管理员通过 `ERP_SEED_ADMIN_USERNAME`、`ERP_SEED_ADMIN_PASSWORD`、`ERP_SEED_ADMIN_REAL_NAME` 和 `ERP_SEED_ADMIN_EMPLOYEE_NO` 初始化，密码使用 BCrypt，不会写入源码或日志。用户、部门、角色和权限变更会记录到 `sys_operation_log`；停用用户或修改密码/角色后，该用户已有 Token 会失效。

库存盘点使用 `/api/inventory/stocktakes` 和前端 `/inventory/stocktakes`，支持草稿、账面快照、实盘、结构化设备差异、确认调整和历史查询。设备异常中的账外、未登记、错产品、库外与状态异常须先人工核查；确认设备缺失时将原设备标记为 `MISSING` 并记录历史和库存流水。实现边界见 [docs/stocktake-management.md](docs/stocktake-management.md)。完整接口以 `erp/src/main/java/com/haucheng/erp/controller/` 和前端路由为准；数据库版本以实际 Flyway history 为准。当前 Phase 7 使用 V12 和修正盘点设备唯一键的 V13。

## Phase 8 设备异常处理

维修、保修、换货分别使用 `/api/assets/repairs`、`/api/assets/warranties`、`/api/assets/exchanges` 和对应的 `/assets/repairs`、`/assets/warranties`、`/assets/exchanges` 页面。三类单据独立保存，维修/换货复用设备占用、库存与历史。Phase 8 结束时最高 Migration 为 V16。状态规则及当时的验证见 `docs/repair-management.md`、`docs/warranty-management.md`、`docs/exchange-management.md`、`docs/phase8-verification.md`。

## Phase 9 二维码与标签

`/api/assets/qr` 提供 Product/Device 二维码生成、标签数据和登录后扫码查询；前端 `/assets/qr` 支持手动/扫码枪输入、浏览器支持时的摄像头识别，以及单张和最多 30 张标签预览与打印。二维码使用不依赖域名的 `ERPQR1` 不透明随机令牌格式，须通过 ERP 扫码页面解析，不能作为匿名访问凭据。当前最高 Migration 为 V17。格式、权限、打印参数和限制见 [docs/qr-management.md](docs/qr-management.md)。

## Phase 10A 首页与综合查询

登录后首页调用 `GET /api/dashboard/summary` 展示当前账号可查看的统计、设备状态与近期业务动态。`/query` 页面调用 `GET /api/query`，统一搜索物资、设备、仓库、入库、出库、领用、盘点、维修、保修、换货；支持类型、状态、日期和分页。接口只读，沿用各模块既有查看权限及数据范围，没有新增 Migration，最高仍为 V17。统计口径与跳转限制见 [docs/dashboard-management.md](docs/dashboard-management.md) 和 [docs/global-query.md](docs/global-query.md)。

## Phase 10B 业务报表与 Excel

`/reports` 提供库存、入库、出库、领用、设备资产、盘点差异和维修/保修/换货报表。`GET /api/reports/{type}` 返回分页明细与同条件汇总，`GET /api/reports/{type}/export` 导出相同筛选范围的 `.xlsx`，最多 50,000 行。报表按现有资源查看权限及数据范围只读查询，没有新增 Migration，最高仍为 V17。口径见 [docs/report-management.md](docs/report-management.md)，导出格式见 [docs/excel-export.md](docs/excel-export.md)。

## Phase 10C 附件与站内通知

`/api/attachments` 提供业务附件上传、列表、授权下载及删除；对象放在私有 MinIO bucket，元数据放在 MySQL。设备、物资、入出库、领用、盘点、维修、保修、换货现有页面的适当入口展示附件。`/notifications` 提供登录用户自己的持久化通知、未读数和已读操作；入出库完成、领用发放，以及维修/保修/换货处理结果会触发通知。V18 新增附件与通知表和权限。接口与限制见 [docs/attachment-management.md](docs/attachment-management.md)、[docs/notification-management.md](docs/notification-management.md)。
