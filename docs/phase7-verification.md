# Phase 7 实现与验证记录

日期：2026-10-14（Asia/Shanghai）。工作区：`F:\codexerp`，分支 `feature/phase7-stocktake`。本记录只描述本次在指定开发 MySQL 上重新执行的结果；旧 `target` 和 Phase 4～6 历史文档未被当作当前验收证据。

## 数据库基线与迁移

- 仅连接 `192.168.1.247:3307`、数据库 `erp`、账号 `root`；凭据只进入命令进程环境，不写入源码、配置、文档或测试。没有连接 localhost 或 Docker MySQL。
- 建库前通过 `information_schema.schemata` 确认 `erp` 不存在；创建 `utf8mb4 / utf8mb4_unicode_ci` 空库，建库后表数为 0。因原库不存在，无既有业务数据需要备份。
- 空库 Flyway info 显示 V1～V11 全部 Pending；按正常 migrate 一次应用 V1～V11，独立 validate 11 项通过。开发前基线完整 Maven 测试：84 项、0 失败、0 错误、0 跳过；测试后业务 fixture 均为 0 行。
- V12 创建盘点单、明细、设备核对表和权限。V12 应用后独立 validate 12 项通过。V13 修正盘点设备编号唯一键作用范围，使同一设备可在不同产品明细中记录“错产品”差异；V13 应用后独立 validate 13 项通过。V1～V11 与已经应用的 V12 未改写，也未执行 Flyway repair、baseline 或重放迁移。

## 实现和验证范围

后端新增 `StocktakeController`、`StocktakeService`、`StocktakeMapper`、DTO/VO/Entity，沿用 `StockMapper`、`AssetDeviceMapper`、仓库、产品、数据范围、权限、操作日志和现有设备历史。前端增加 `/inventory/stocktakes` 路由、菜单、API 与盘点页面；设备页面显示 `MISSING` 状态。数据库表、接口、状态和异常语义见 [stocktake-management.md](stocktake-management.md)。

Phase 7 真实 MySQL 定向用例覆盖：草稿编辑/提交/取消/冻结、客户端唯一请求、版本冲突、数量盘盈盘亏和零差异、空账面确认、负数及锁定数量保护、设备逐台录入与缺失确认、未登记/错产品/库外/状态异常/账外差异、同设备跨产品明细、权限和部门数据范围、HTTP 操作日志 `request_id`、重复及并发确认、入库执行与盘点确认竞争、快照后库存先变后回原值、审计失败导致库存/设备/流水/单据回滚、设备数量与库存一致性。

| 验证 | 当前结果 |
| --- | --- |
| Flyway V1～V13 migrate / validate | PASS；13 项成功，无失败记录 |
| Phase 0～6 开发前基线完整测试 | PASS；84 项，0 失败/错误/跳过 |
| Phase 7 定向真实 MySQL 回归 | PASS；13 项，0 失败/错误/跳过 |
| 完整 Maven/JVM 测试 | PASS；97 项，0 失败/错误/跳过，含 Phase 1～7 真实 MySQL 回归 |
| 未跳过测试的 `mvnw package` | PASS；再次执行 97 项测试，0 失败/错误/跳过 |
| 前端 `npm ci` 与 `npm run build` | PASS；Vite 8.2.2 成功构建，主 JS 包超过 500 kB 的现有大小提示 |
| 前端 lint | NOT CONFIGURED；`package.json` 无 lint 脚本 |
| 浏览器端到端测试 | NOT RUN |

测试 fixture 使用唯一 `P7_` 前缀及测试生成的主键，清理 SQL 只针对本次生成的单据、仓库、产品、设备和用户；未执行 `TRUNCATE`、全表清空或删除非测试数据。最终 package 后复核：Flyway history 13 行且均成功，最高版本 V13；盘点单、明细、设备核对、仓库、库存、流水、设备、设备历史、用户、审计与 `P7_` 分类/产品均为 0 行；设备库存数量不一致为 0。随后独立执行 Flyway validate，退出码为 0。
