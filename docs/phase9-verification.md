# Phase 9 实现与验证记录

日期：2026-10-14（Asia/Shanghai）。项目：`F:\codexerp`，分支：`feature/phase9-qr-scan-print`。本文件记录本次真实执行结果；旧 `target/surefire-reports` 和历史阶段文档不作为当前验收证据。

## 基线、备份与迁移

开始时工作区干净，Phase 8 提交为 `6203cf55831e773cd5577fc4257cd10a5dfd4942`，V14～V16 源码存在。只连接指定 MySQL `192.168.1.247:3307/erp`，root 密码由 Git 忽略的本地 `.env` 加载到命令进程；未写入源码、文档或日志。迁移前数据库为 V16，V1～V16 全部 Success，独立 Flyway validate 核对了 16 个 checksum。

执行任何 V17 Migration 前，使用 `mysqldump --single-transaction` 保存真实数据库到 Git 忽略的 `.local/backups/erp-pre-phase9-20261014-153044.sql`，文件非空（102397 字节）。随后按 Flyway 正常顺序应用 `V17__create_qr_tokens.sql`。最终独立 `flyway:validate flyway:info` 通过：V1～V17 共 17 个 Migration 全部 Success，schema version 17。V1～V16 未修改。

真实库元数据复核：`sys_qr_tokens` 有 8 列、4 个唯一索引（含主键）、2 个外键、2 个 CHECK；Product/Device 各自资源唯一，令牌唯一。`asset.qr.*` 权限 4 项、角色授权 16 项：系统管理员、仓库管理员、部门负责人各 4 项，管理层、普通员工各 2 项。权限检查仍叠加 Product/Device 原有 view 权限和数据范围。

## 测试与构建

运行前查看 Phase 4～9 测试中的数据库清理 SQL：只按测试生成 ID、前缀或其关联资源清理；未见 TRUNCATE 或无条件全表删除。Phase 9 使用独立 `P9_` UUID 前缀；测试结束精确删除自己创建的令牌、审计、库存、设备、物资与用户夹具。

| 验证 | 本次结果 |
| --- | --- |
| Phase 9 定向真实 MySQL 回归 | PASS，14 项，0 失败/错误/跳过 |
| 标准 QR 真实解码 | PASS；ZXing 解码由服务端生成的 Product、Device SVG，文本与数据库关联资源一致 |
| Phase 4～8 真实 MySQL 回归 | PASS，19 + 15 + 14 + 13 + 36 = 97 项 |
| 完整 `mvnw.cmd test` | PASS，147 项，0 失败/错误/跳过，Java 17，指定 MySQL |
| 未跳过测试的 `mvnw.cmd package` | PASS，再次执行 147 项，0 失败/错误/跳过，并生成 Spring Boot jar |
| `npm run build` | PASS，Vite 8.2.2 构建成功；主 JS 包超过 500 kB 的提示仍存在 |
| lint | NOT CONFIGURED，前端 `package.json` 没有 lint 脚本 |
| 浏览器摄像头/打印机 E2E | NOT RUN；仅验证了页面打印样式结构和 SVG 可解码性 |

Phase 9 用例覆盖稳定重复生成、Product/Device 类型区分、离库/报废等历史设备指向、Product 启用设置、停用和未知令牌、错误版本与长度、权限及 ALL/DEPARTMENT/SELF 范围、库存概况权限、1～30 张批量限制、失败批量回滚、数据库唯一/CHECK 约束、操作日志失败回滚、并发生成、HTTP 鉴权和 request_id，以及扫码/打印不改库存、流水、设备历史或状态。Phase 8 实际换货执行回归已加入同一流程中的二维码断言：执行前生成旧、新标签，执行后核对两个二维码仍分别定位原设备，并保持原内容与正确的 SHIPPED/IN_USE 状态。

补充跨流程断言后的第一次全量运行因测试在 MockMvc 请求结束后未重新建立 `CurrentUserContext`，出现 1 项错误；已修正测试上下文并完整重跑。上述表格列出最终运行结果，没有跳过或降低该断言。

最终只读数据库复核：`sys_qr_tokens`、QR 操作日志均为 0 行；`P4_`～`P9_` 前缀的 Product、Device、Warehouse、User 夹具均为 0 行；当前库 Product、Device、Stock、StockLedger 和 DeviceHistory 均为 0 行。设备库存行与 `IN_STOCK` 且仓库匹配的设备计数差异为 0，已入库设备缺少库存行的差异也为 0。构建生成的 `target/`、`dist/` 和备份目录均被 Git 忽略。

## 已知限制

二维码内容是 ERP 专用文本，需要在已登录的 ERP 扫码页面解析，普通手机扫码器不会直接打开网页。摄像头识别依赖浏览器原生 `BarcodeDetector` 和摄像头权限，页面提供手动/扫码枪输入回退。批量打印使用浏览器 `window.print()`；实际打印机纸张、缩放、分页及摄像头端到端体验未在本次执行实机验收。Product 库存概况返回有权限范围内的第一页、最多 100 行，并提供 total。
