# Phase 10B 实现与验证记录

日期：2026-10-15（Asia/Shanghai）。工作区 `F:\codexerp`，分支 `feature/phase10b-reports-excel`。本文件仅记录本轮实际执行结果，旧文档与旧 `target/surefire-reports` 不替代本轮证据。

## 基线与数据库

开始时工作区干净，HEAD 与远端 Phase 10A 提交 `90b460373a3171722afa522de39af0e38e3f3168` 一致，Phase 10B 分支原本不存在。本分支从该提交创建；未操作 main。数据库连接从 Git 忽略的本地 `.env` 读入进程环境，实际目标为唯一指定的 `192.168.1.247:3307/erp`、root，未把秘密写入仓库。开始前和最终独立 `flyway:validate flyway:info` 均通过；只读 JDBC 实查 Flyway 成功记录 17、失败记录 0、最高 V17。V1～V17 未修改，本阶段无 V18、新迁移、repair、重建或清库，无需迁移前备份。

## 实现与测试

后端新增 `ReportController`、`ReportService`、`ReportExcelService`、`ReportMapper`、`ReportSqlProvider`、`ReportFilter`、`ReportRequest` 和三个报表 VO；POM 增加 Apache POI `poi-ooxml` 5.4.1。前端新增 `/reports` 页面，更新路由、菜单和原生 fetch API。同步 README、database-plan、report-management、excel-export 与本文件；AGENTS 仍适用，未修改原业务模块。

七类报表在真实 MySQL 上以 `P10B_`＋UUID 的事务夹具验证。夹具包括一个 Product 多台 Device、一单多 Item 与设备快照、一台 Device 多条 DeviceHistory、同产品多条 StockLedger、跨部门仓库、草稿/完成单、待确认盘点与结构化 MISSING 异常、维修/过保保修/换货旧新设备。检验页面分页明细与汇总对同一筛选口径一致、完成量仅计已执行单据、报废设备仍可查、盘点最终差异只计已确认、ALL/DEPARTMENT/SELF 返回不同数据、无权访问被拒绝、特殊字符为字面匹配、日期/状态/页码限制有效。

实际生成 `.xlsx` 后由 Apache POI 再次读取，核查工作簿名“业务报表”、中文表头、筛选后行数、七类报表关键编号与网页排序一致、编码文本保留前导零、数量为数字单元格、时间为日期数字单元格并带统一格式、冻结行与筛选范围。HTTP 测试核查登录、403、JSON 与带 UTF-8 编码的中文附件文件名；导出超过 50,000 行的单元测试在创建工作簿前拒绝。数据库故障单元测试确认故障向上交由统一异常处理且不产生导出文件。

首轮定向测试编译失败：测试误调用 POI 不存在的 `XSSFSheet.getAutoFilter()`，改为读取实际 OOXML 结构。第二轮真实 MySQL 失败：动态 SQL 的 `<` 未 XML 转义，已修正 Provider 中固定 SQL 表达式的 XML 转义。下一轮 1 项断言错误：设备资产总数包含测试构造的一台已报废设备，断言改为核实总资产数 3、IN_STOCK 设备数 2，没有删除终态设备。之后定向测试、全量测试和 package 均重新通过，未删测试、跳过测试或降低断言。

| 验证 | 本轮结果 |
| --- | --- |
| Phase 10B 真实 MySQL 回归 | PASS，9 项，0 失败/错误/跳过 |
| 导出上限与数据库异常单元测试 | PASS，2 项 |
| Phase 4～9 真实 MySQL 回归 | PASS，19＋15＋14＋13＋36＋14＝111 项 |
| Phase 10A 真实 MySQL 回归 | PASS，11 项 |
| 完整 `mvnw.cmd -q test` | PASS，169 项，0 失败/错误/跳过 |
| 未跳过测试的 `mvnw.cmd -q package` | PASS，再次执行 169 项，0 失败/错误/跳过；生成本轮 jar |
| 前端 `npm run build` | PASS，Vite 8.2.2；主 JS 包仍有超过 500 kB 的提示 |
| 最终 Flyway validate/info | PASS，V1～V17 checksum 一致，全部 Success |
| 夹具、库存与导出临时文件 | PASS，`P10B_` 夹具 0，Device/Stock 差异 0，POI 临时目录内文件 0 |
| lint | NOT CONFIGURED，`erp-vue/package.json` 无 lint 脚本 |
| 浏览器 E2E / 真实打印 | NOT RUN；本阶段无打印业务 |

最终项目外只读 JDBC 核查确认：V17，`P1_`～`P9_`、`P10A_`、`P10B_` 主数据与单据夹具 0；当前 Product、Device、Warehouse、Stock、StockLedger、DeviceHistory、二维码令牌、业务单据及明细均为 0 行；设备在库数量与 Stock 差异 0，缺失库存行及异常仓库归属 0。这些零值是测试事务回滚与既有测试清理后的观察，不是执行清空操作。测试前已检查现有回归的清理逻辑按测试生成的 ID、前缀和关联资源限定，未见 TRUNCATE 或无条件业务全表删除。本轮新夹具全部在测试事务中回滚，并以 `@AfterTransaction` 复核。

## 已知限制

- 库存报表逐仓库 Product 展示已有 Stock 行，不为无 Stock 行的全部仓库和 Product 生成笛卡尔积；低库存也按当前行与 Product 阈值比较。
- 报表日期筛选使用主记录创建时间；近期完成量依状态与执行时间字段判断，不生成历史任意时点库存快照。
- 不同 Product 数量可能使用不同单位，跨产品合计仅是原单位数值的概览，不能直接解释为统一件数。
- 除设备外，现有业务没有统一详情路由，报表入口跳原业务列表；用户再按编号定位。未做浏览器 E2E 或大数据量性能专项。
- SXSSF 分批写入、导出上限 50,000 行；最终 HTTP 仍需要在内存持有文件字节。尚未在接近上限的实际数据量下做内存与响应时间专项验收。
- Phase 10C 附件/通知和 10D 未实现。
