# Phase 9 二维码、扫码与标签打印

## 范围与身份

二维码只定位既有 `md_products` 或 `asset_devices`，不建立第二套档案。内容分别为 `ERPQR1:P:<token>` 和 `ERPQR1:D:<token>`，其中 `<token>` 为服务端 `SecureRandom` 生成的 32 字节随机数，使用无填充 Base64url 编码为 43 字符。二维码总长度 52 字符，不含数据库 ID、SN、MAC、人员资料、密码或 Bearer Token。`P` 和 `D` 区分 Product 与 Device；服务端按令牌反查数据库，客户端提交的任意资源 ID 不能充当扫码结果。

内容是稳定文本，不是绝对 URL，因此不需要 `ERP_PUBLIC_BASE_URL`，已印标签不会因域名变化而改变指向。普通手机扫码器只会读出文本，须在登录 ERP 的 `/assets/qr` 页面提交；支持扫码枪输入并回车。设备维修、盘点、报废、离库或换货不改写令牌和身份。换货前后的两个 Device 拥有独立二维码，旧标签始终定位旧 Device 并展示其当前状态。

V17 的 `sys_qr_tokens` 保存明文不透明令牌以支持稳定重印；`ascii_bin` 唯一键保证大小写精确匹配，Product/Device 各自的资源列使用唯一键和外键，CHECK 限制资源类型与两列恰好匹配。`format_version=1`、`active` 和 `created_at` 用于识别版本、停用及审计。首次生成前锁定对应资源行，批量按资源 ID 升序锁定；数据库唯一键兜底并发。重复生成返回原令牌，停用令牌不能扫描或预览。Product `enable_qrcode` 仅控制首次生成；已印标签在该配置关闭后仍可重印和扫码。停用的 Product 不能生成或扫码；历史 Device 的扫码不因其状态为 `SHIPPED`、`SCRAPPED` 或其 Product 后续停用而重新指向别的资源。

## API、权限与数据范围

所有接口要求登录，并继承统一 `ApiResponse`、`BusinessException`、Request ID 和操作日志机制。

| 接口 | 行为 | 权限 |
| --- | --- | --- |
| `GET /api/assets/qr/{PRODUCT\|DEVICE}/{id}` | 预览已有标签；未生成时返回明确错误 | `asset.qr.view` 与原资源 view |
| `POST /api/assets/qr/{PRODUCT\|DEVICE}/{id}` | 首次生成或稳定重取单张标签 | `asset.qr.view`、`asset.qr.generate` 与原资源 view |
| `POST /api/assets/qr/{PRODUCT\|DEVICE}/labels` | `{"ids":[...]}`，原子生成 1～30 张标签预览 | `asset.qr.view`、`asset.qr.print`、`asset.qr.generate` 与每个原资源 view |
| `POST /api/assets/qr/scan` | `{"content":"ERPQR1:..."}`，解析并返回有权查看的档案 | `asset.qr.view`、`asset.qr.scan` 与原资源 view |

原资源权限为 `system.product.view` 或 `system.device.view`。Device 继续执行已有 `ALL/DEPARTMENT/SELF` 数据范围；Product 库存概况仅在拥有 `inventory.stock.view` 时返回，仓库范围由现有 Stock 查询执行。当前概况取第 1 页、最多 100 行，并返回 total。扫码令牌不是认证凭据，不开放匿名完整档案接口。无效格式、未知令牌、资源失效、无权访问或版本不支持由统一异常处理返回业务提示。输入最长 256 字符；图像由固定 52 字符内容生成，不接受客户端控制图像尺寸。

V17 将四个二维码权限加入既有 RBAC。系统管理员、仓库管理员、部门负责人默认获得四项；管理层与普通员工默认获得查看、扫码。实际是否可读取 Product/Device 仍由原资源权限和数据范围决定；不能仅凭二维码菜单权限扩大访问。首次生成写 `QR_GENERATE`，成功扫码写 `QR_SCAN`，批量预览写 `QR_PRINT_PREVIEW`，随现有机制保存 request_id。二维码操作不写 `inv_stocks`、`inv_stock_ledgers`、`asset_device_history`，不改变维修/保修/换货等业务状态。

## 页面与打印

`/assets/qr` 提供文本/扫码枪输入。浏览器具备 `BarcodeDetector` 且摄像头授权成功时可识别 QR；不支持、未安装摄像头或拒绝授权时仍可用输入框。停止扫码、离开页面或组件卸载时停止 MediaStream。结果显示当前用户有权查看的 Product/Device 字段，详情链接进入既有页面并再次受路由与后端权限控制。Device 与 Product 列表中提供单张二维码入口；Device 列表可多选进入批量预览。

标签使用服务端 ZXing 标准 QR SVG，纠错等级 M、四模块静区。默认设备/物资标签 60 × 35 mm、QR 25 mm、边距 3 mm；页面可调宽 60～100 mm、高 35～70 mm、边距 2～5 mm，QR 为 18 mm 至布局允许的上限。标签显示“ERP 资产管理”、编号、产品名及可读辅助标识。批量最多 30 张，预览连续排列；浏览器 `window.print()` 与 `@media print` 隐藏导航、页头和操作控件，标签设置分页避免跨页。没有设备厂商打印指令或服务器 PDF 服务。浏览器与打印机的纸张设置、缩放及实机效果须在目标设备上另行验收。

后端新增 `com.google.zxing:core:3.5.4` 生成标准 QR；测试范围使用同系 `javase:3.5.4` 从生成图像真实解码。项目原依赖没有二维码编码器；服务端生成便于统一格式和权限，避免前端独立生成不同内容。依赖不改变返回结构、异常处理、目录职责或库存事务。
