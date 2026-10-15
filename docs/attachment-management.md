# 业务附件管理

Phase 10C 使用 `sys_attachments` 保存元数据，用单一 MinIO SDK 将内容放入私有 bucket。后端从 `MINIO_ENDPOINT`、`MINIO_BUCKET`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY` 读取配置；根目录 `.env.example` 只有占位符，实际值放进已被 Git 忽略的 `.env` 并加载为后端进程环境。服务未配置或不可用时上传、下载、删除返回明确业务错误，不会改用本地文件系统。MinIO 8.5.17 是本阶段唯一新增对象存储依赖；现有技术栈没有 SDK，直接调用 HTTP/S3 签名协议会增加维护和安全风险。

## API 与接入点

所有 `/api/attachments` 接口先执行 Bearer 认证。列表 `GET /api/attachments?businessType=DEVICE&businessId=123`，元数据 `GET /api/attachments/{id}`，上传 `POST /api/attachments`（multipart 字段 `businessType`、`businessId`、`clientRequestId`、`file`），下载 `GET /api/attachments/{id}/download`，普通图片可加 `preview=true`，删除 `DELETE /api/attachments/{id}`。JSON 接口沿用 `ApiResponse`；下载返回受控字节流及 UTF-8 文件名、`nosniff`、禁止缓存、安全内容策略。前端已有的 Product、Device、入出库、领用、盘点、维修、保修、换货页面接入 `AttachmentPanel`，显示上传状态、名称、大小、下载与删除确认。

允许的 `businessType` 为 `PRODUCT`、`DEVICE`、`INBOUND`、`OUTBOUND`、`REQUISITION`、`STOCKTAKE`、`REPAIR`、`WARRANTY`、`EXCHANGE`。服务端必须先查真实对象。查看、下载使用 `attachment.view`、原业务查看权限和原详情服务的数据范围；上传、删除还需 `attachment.upload` 或 `attachment.delete`、原业务的 `*.update` 权限与数据范围。`ALL/DEPARTMENT/SELF` 均沿用原服务。当前角色授权：系统管理员、仓库管理员、部门负责人拥有附件查看、上传、删除；管理层与普通员工只拥有附件查看。角色授权只是入口，原业务权限与对象范围仍由后端逐次校验。删除他人上传但属于本人有权维护的业务附件允许执行，并写操作日志。

## 限制与一致性

单文件 20 MiB，请求 21 MiB，单业务对象最多 20 个 READY 附件；显示文件名最长 200 字符，拒绝路径分隔符、`..` 和控制字符。允许 PDF、PNG、JPG/JPEG、WebP、DOCX、XLSX、UTF-8 TXT；服务端核对扩展名、请求 MIME 和文件签名/结构，不信任浏览器 MIME。HTML、SVG、脚本与可执行文件不允许。对象键为服务器随机 UUID，与原文件名无关；返回元数据不含对象键、密钥或永久公开 URL。同一上传请求标识在同一用户范围唯一，网络重试且内容相同时返回原附件，不同内容返回冲突。

上传先锁定同业务已有 READY 附件并检查数量，再写对象，随后写入同一 MySQL 事务中的元数据与 `OperationLog/request_id`；事务回滚时删除新对象。若删除补偿失败，日志记录对象键用于人工核查，不记录文件内容或凭据。下载时检查对象长度和 SHA-256。删除先在事务内置 `DELETE_PENDING` 并写审计，提交后删对象，再以独立事务置 `DELETED`；对象存储故障时保留 `DELETE_PENDING`，列表展示“删除待重试”并可用同一删除接口重试。MinIO 与 MySQL 无原生分布式事务，极端进程崩溃窗口可能留下待核查对象/元数据，当前没有后台自动修复任务。测试环境无病毒扫描服务，文件未经过恶意软件扫描；Office 格式仅做容器和关键结构核查。运行环境如使用明文 HTTP endpoint，应由部署方提供内网隔离或 TLS 代理。
