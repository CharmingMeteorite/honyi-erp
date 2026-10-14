# Phase 7 库存盘点

## 数据与状态

`V12__create_stocktakes.sql` 建立盘点单、盘点明细和设备核对记录；`V13__scope_stocktake_device_identity_to_item.sql` 把设备编号的唯一键限定在明细行，使同一设备被错误录入另一个产品时仍能保存差异。盘点按单仓库执行，明细按产品唯一。`DRAFT` 可编辑，提交后为 `COUNTING`；所有行均录入后为 `COUNTED`；确认后为 `CONFIRMED`，此前可取消为 `CANCELLED`。已确认与已取消单据不可编辑或重新确认。

提交时按产品记录 `snapshot_quantity`、`snapshot_locked_quantity`、`snapshot_ledger_id`。设备型产品还保存当时仓库内 `IN_STOCK` 设备的编号、SN、MAC、资产号及设备 ID。提交零库存明细不会提前创建库存行。实盘数量和差异写入盘点明细，不直接改变库存。

## 设备核对

设备型产品只能提交具体设备编号清单，不能用总数代替。账面设备未发现时记录 `MISSING`；账面外发现设备时，根据登记信息记录 `EXTRA`、`UNREGISTERED`、`WRONG_PRODUCT`、`OUTSIDE_WAREHOUSE` 或 `STATUS_ABNORMAL`。`MATCH` 表示逐台相符。异常保存在 `inv_stocktake_device_checks`，详情页可查看身份和当前登记状态。

只有 `MATCH` 和 `MISSING` 组成的核对结果可进入确认。确认缺失设备时，原设备保留、状态设为 `MISSING`、保留最后登记仓库，并写设备历史和出库方向的盘点流水；不会自动创建设备、删除设备、报废或转移设备。`MISSING` 不属于可直接通过基础状态接口切换的状态，需后续明确的业务流程处理。账外及其他异常须人工核查后重新录入或重新盘点。

## 确认与并发

确认在一个事务内重新读取盘点单版本、仓库、产品、库存和设备。账面数量、锁定量、最后流水 ID 或设备身份在快照后变化时拒绝确认。即使其他业务使库存先变动又回到原数量，最后流水 ID 仍能识别快照过期。负库存或小于锁定数量的调整由服务和 `inv_stocks` 约束共同阻止。

确认时复用 `StockMapper.addQuantity`、`StockMapper.insertLedger`、`AssetDeviceMapper`、`OperationLogService`；库存、设备状态、设备历史、流水、单据终态和审计同事务提交。流水业务类型为 `STOCKTAKE`，有差异的产品各写一条真实流水；零差异不写虚构流水。确认后继续要求 `DEVICE` 产品的 `Stock.quantity` 等于相应仓库 `status=IN_STOCK` 的设备数。

## 接口与页面

后端 `/api/inventory/stocktakes` 提供列表筛选分页、详情、创建、编辑、提交、逐行录入实盘、确认与取消；详情含差异、流水和操作历史。权限为 `inventory.stocktake.view/create/update/submit/count/confirm`，数据范围沿用仓库所属部门规则，`SELF` 不提供库存范围。前端 `/inventory/stocktakes` 使用当前 ERP 主题并展示上述流程。

开发与验证仅使用指定 MySQL；凭据通过进程环境变量提供。实际验证与未覆盖项以 [phase7-verification.md](phase7-verification.md) 为准。
