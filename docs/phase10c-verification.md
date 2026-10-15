# Phase 10C 实现与验证记录

日期：2026-10-15（Asia/Shanghai），工作区 `F:\codexerp`，分支 `feature/phase10c-attachments-notifications`。本轮从干净的 Phase 10B 提交 `5d3ac7289fa72fb1f713d401916114934e9aa4d4` 创建分支；远端 Phase 10B HEAD 与该提交一致，未修改 main。

## 迁移与环境

配置从 Git 忽略的本地 `.env` 加载到进程环境。唯一数据库为 `192.168.1.247:3307/erp`、root。迁移前 `flyway:validate flyway:info` 验证 V1～V17 全部 Success、checksum 匹配；只读 SQL 再次查询 `flyway_schema_history` 17 条成功记录，且不存在附件/通知表。先以 `mysqldump --single-transaction` 备份到 Git 忽略的 `.local/backups/`，文件约 105 KiB，检查 MySQL dump 头并计算 SHA-256，未提交备份。随后追加应用 V18；迁移后和最终再次 `flyway:validate flyway:info`，18 条全 Success。实际核查新增两表 14/10 个字段、唯一键、查询索引与四项权限及五个角色授权。V1～V17 未修改。

MinIO 真实健康接口返回 200，当前本地私有 bucket 配置已连接。真实测试将带随机键的 TXT 对象上传到 MinIO，读回字节并验证 MySQL 元数据；未授权的对象 HTTP 访问返回 403。还实际执行了删除、删除后读取失败和 MySQL 状态/审计核对，以及上传事务回滚后对象不存在。密码、对象键与文件内容未写入 Git 或文档。Java 17 已用于最终测试与 package。

## 测试结果

| 项目 | 实际结果 |
| --- | --- |
| Phase 10C 定向 MySQL + MinIO 测试 | PASS，7 项，0 失败、错误或跳过 |
| Phase 4～10B 真实 MySQL 回归 | PASS；完整 Maven 测试覆盖现有全部阶段 |
| 完整 `mvnw.cmd test` | PASS，176 项，0 失败、错误或跳过 |
| 未跳过测试的 `mvnw.cmd package` | PASS，再次执行 176 项，生成 Spring Boot jar |
| 前端 `npm run build` | PASS，Vite 8.2.2；现有主包超过 500 kB 提示仍在 |
| Flyway validate/info | PASS，V1～V18 全 Success，最高 V18 |
| lint | NOT CONFIGURED，前端 `package.json` 无 lint 脚本 |
| 浏览器 E2E | NOT RUN；未宣称浏览器交互已自动化验证 |

新增测试以 `P10C_`＋UUID 生成用户、部门、产品、设备和附件。事务内夹具回滚并由 `@AfterTransaction` 检查；唯一一次提交型测试按精确 ID 清理自己的对象、附件、审计和基础数据。最终只读 SQL 核查 `sys_attachments`、`sys_notifications` 皆为 0 行，`P10C_` 与本轮残留的 `P7_` 用户、部门、物资、设备、仓库均为 0。旧 Phase 4～10B 测试的清理逻辑按 fixture ID/前缀限定，无业务表 TRUNCATE。第一轮全量回归发现两次 Phase 7 测试在清理用户时受新增通知外键阻止；修复 Phase 7 夹具清理顺序，并只对那两次已确认 ID 的残留记录做事务内定向清理。一次后续 package 遇到既有 Phase 9 随机二维码 SVG 解码的 `ZXing NotFoundException`；同一测试单独重跑通过，随后全量 package 重跑 176 项全部通过。未跳过或改弱该断言；该历史测试在随机内容下可能偶发失败，需后续独立跟踪。

附件测试覆盖文件名路径穿越、空文件、超限、HTML/伪造 MIME、跨部门与 SELF 授权、重复请求、实际下载响应头、私有 bucket、删除与回滚补偿、未配置存储时的明确失败；通知测试覆盖本人隔离、未读数、单条/批量已读、重复事件和停用用户；原有 Phase 5/6/8 回归新增成功业务触发通知的断言。浏览器端上传进度目前以忙碌状态呈现，未做字节百分比。没有病毒扫描与后台对象一致性修复服务；极端进程崩溃可能留下待人工核查的对象或 `DELETE_PENDING` 元数据，这一状态在附件列表可见并支持重试删除。

## 交付检查

最终检查 `git diff --check`、旧 Migration 差异、凭据关键字、本地 `.env` 与备份的 ignore 状态。`.env`、数据库备份、Maven/Vite 产物及测试日志均在 Git 忽略目录。代码与文档中没有真实数据库或 MinIO 凭据。本文件只记录已执行结果；Git commit 和 push 状态以最终交付回复为准。
