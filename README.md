# ERP 内部物资管理系统

公司内部物资、库存、设备及生命周期管理系统。当前备份中的源码大致覆盖 Phase 0～Phase 6：认证与权限、主数据、设备、仓库库存、入库/出库和领用申请/发放。根据当前备份未发现 Phase 7～10C 的完整实现；Phase 6 的历史最终验收曾受 MySQL 连接故障阻塞，本文不将未重新执行的测试或构建称为当前通过。

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

完整接口以 `erp/src/main/java/com/haucheng/erp/controller/` 和前端路由为准；数据库版本以实际 Flyway history 为准，不能仅凭 Migration 文件名判断。后续 Phase 按需求逐步实现，当前备份未发现 Phase 7～10C 的完整源码、页面和测试。
