# ERP 内部物资管理系统

公司内部物资、库存、设备及生命周期管理系统。当前源码覆盖 Phase 0～9：认证与权限、主数据、设备、仓库库存、入库/出库、领用申请/发放、库存盘点、设备维修/保修/换货，以及二维码识别和标签打印。Phase 9 在 `feature/phase9-qr-scan-print` 分支开发；Phase 10 尚未实现。各阶段历史记录不能替代当前验证，Phase 9 结果见 [docs/phase9-verification.md](docs/phase9-verification.md)。

## 技术栈

- 后端：Java 17、Spring Boot 4.1.1、Spring MVC、MyBatis、Jakarta Validation、Lombok、MySQL 8
- 前端：Vue 3、JavaScript、Vite、Vue Router、Element Plus、原生 `fetch`
- 数据库：MySQL 8、InnoDB、utf8mb4、Flyway Migration

## 开发环境

需要 Java 17、Node.js 22+、npm 和 MySQL 8。先将 `.env.example` 复制为 `.env`，设置本机密码；`DB_PASSWORD` 与 `MYSQL_PASSWORD` 必须一致。`.env` 已被 Git 忽略，不要提交真实密码或 Token 密钥。

```powershell
Copy-Item .env.example .env
```

启动开发数据库：

```powershell
docker compose -f docker/docker-compose.yml up -d mysql
```

Docker Compose 会从仓库根目录的 `.env` 读取数据库容器配置。Spring Boot 不会自动读取该 `.env` 文件；本地启动后端时，还需在当前 shell 设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 等应用环境变量。

## 后端

在 `erp` 目录执行：

```powershell
./mvnw.cmd test
./mvnw.cmd spring-boot:run
```

应用默认监听 `http://localhost:8080`。Flyway 启动时自动执行 `src/main/resources/db/migration`。可通过环境变量 `ERP_SEED_ADMIN_USERNAME` 和 `ERP_SEED_ADMIN_PASSWORD` 初始化开发管理员；两个变量都为空时不会创建用户。

## 前端

在 `erp-vue` 目录执行：

```powershell
npm install
npm run dev
```

前端默认监听 `http://localhost:5173`，开发代理将 `/api` 和 `/health` 转发到后端。生产构建使用 `npm run build`。

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
