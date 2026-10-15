package com.haucheng.erp.service;

import com.haucheng.erp.mapper.ReportFilter;
import com.haucheng.erp.mapper.ReportMapper;
import com.haucheng.erp.vo.ReportRowView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportExcelService {
    private static final int BATCH_SIZE = 500;
    private final ReportMapper mapper;

    private record Column(String title, Function<ReportRowView, Object> value) { }
    private static final List<Column> COMMON = List.of(
            new Column("单号/编号", ReportRowView::getNumber), new Column("状态", ReportRowView::getStatus),
            new Column("产品编码", ReportRowView::getProductCode), new Column("产品名称", ReportRowView::getProductName),
            new Column("仓库", ReportRowView::getWarehouseName), new Column("创建时间", ReportRowView::getCreatedAt));
    private static final Map<String, List<Column>> COLUMNS = Map.of(
            "STOCK", List.of(new Column("产品编码", ReportRowView::getProductCode), new Column("产品名称", ReportRowView::getProductName),
                    new Column("分类", ReportRowView::getCategoryName), new Column("品牌", ReportRowView::getBrandName),
                    new Column("类型", ReportRowView::getProductType), new Column("仓库", ReportRowView::getWarehouseName),
                    new Column("库存数量", ReportRowView::getQuantity), new Column("锁定数量", ReportRowView::getLockedQuantity),
                    new Column("可用数量", ReportRowView::getAvailableQuantity), new Column("安全库存", ReportRowView::getSafetyStock),
                    new Column("低库存", r -> Boolean.TRUE.equals(r.getLowStock()) ? "是" : "否")),
            "DEVICE", List.of(new Column("设备编号", ReportRowView::getDeviceCode), new Column("产品编码", ReportRowView::getProductCode),
                    new Column("产品名称", ReportRowView::getProductName), new Column("设备状态", ReportRowView::getStatus),
                    new Column("部门", ReportRowView::getDepartmentName), new Column("使用人", ReportRowView::getUserName),
                    new Column("仓库", ReportRowView::getWarehouseName), new Column("保修状态", ReportRowView::getWarrantyStatus),
                    new Column("SN / MAC / 资产编号", ReportRowView::getReason), new Column("登记时间", ReportRowView::getCreatedAt)),
            "STOCKTAKE", List.of(new Column("盘点单号", ReportRowView::getNumber), new Column("状态", ReportRowView::getStatus),
                    new Column("仓库", ReportRowView::getWarehouseName), new Column("产品编码", ReportRowView::getProductCode),
                    new Column("产品名称", ReportRowView::getProductName), new Column("账面快照", ReportRowView::getSnapshotQuantity),
                    new Column("实盘数量", ReportRowView::getCountedQuantity), new Column("待确认差异", ReportRowView::getDifferenceQuantity),
                    new Column("差异方向", ReportRowView::getDifferenceDirection), new Column("最终差异", ReportRowView::getFinalDifferenceQuantity),
                    new Column("设备异常代码", ReportRowView::getDifferenceCode),
                    new Column("设备异常数", ReportRowView::getDeviceExceptions), new Column("确认时间", ReportRowView::getCompletedAt)),
            "SERVICE", List.of(new Column("业务类型", ReportRowView::getServiceKind), new Column("业务编号", ReportRowView::getNumber),
                    new Column("状态", ReportRowView::getStatus), new Column("旧设备编号", ReportRowView::getDeviceCode),
                    new Column("新设备编号", ReportRowView::getNewDeviceCode), new Column("产品编码", ReportRowView::getProductCode),
                    new Column("产品名称", ReportRowView::getProductName), new Column("申请人", ReportRowView::getUserName),
                    new Column("原因", ReportRowView::getReason), new Column("结果", ReportRowView::getResult),
                    new Column("备注", ReportRowView::getRemark), new Column("维修注意事项", ReportRowView::getNotes),
                    new Column("保修状态", ReportRowView::getWarrantyStatus),
                    new Column("保修决定", ReportRowView::getDifferenceCode), new Column("申请时间", ReportRowView::getSubmittedAt),
                    new Column("完成时间", ReportRowView::getCompletedAt)));

    public byte[] create(ReportFilter filter, long rows) {
        List<Column> columns = columns(filter.getType());
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("业务报表");
            sheet.createFreezePane(0, 1);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            CellStyle headerStyle = workbook.createCellStyle();
            var font = workbook.createFont(); font.setBold(true); headerStyle.setFont(font);
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                var cell = header.createCell(i); cell.setCellValue(columns.get(i).title()); cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 22 * 256);
            }
            int rowNumber = 1;
            for (int offset = 0; offset < rows; offset += BATCH_SIZE) {
                filter.setOffset(offset); filter.setLimit(BATCH_SIZE);
                List<ReportRowView> batch = mapper.selectPage(filter);
                if (batch.isEmpty()) throw new IllegalStateException("报表数据在导出期间发生变化，请重试");
                for (ReportRowView item : batch) {
                    Row excelRow = sheet.createRow(rowNumber++);
                    for (int column = 0; column < columns.size(); column++) {
                        Object value = columns.get(column).value().apply(item);
                        if (value == null) continue;
                        var cell = excelRow.createCell(column);
                        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
                        else if (value instanceof LocalDateTime time) {
                            cell.setCellValue(java.sql.Timestamp.valueOf(time)); cell.setCellStyle(dateStyle);
                        } else cell.setCellValue(value.toString());
                    }
                }
            }
            if (rowNumber - 1 != rows) throw new IllegalStateException("报表数据在导出期间发生变化，请重试");
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(1, rowNumber - 1), 0, columns.size() - 1));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Excel 文件生成失败", e);
        } finally {
            workbook.dispose();
        }
    }

    private List<Column> columns(String type) {
        if ("INBOUND".equals(type) || "OUTBOUND".equals(type) || "REQUISITION".equals(type)) {
            var result = new java.util.ArrayList<>(COMMON);
            result.addAll(List.of(new Column("申请/创建人", ReportRowView::getUserName),
                    new Column("申请数量", ReportRowView::getQuantity), new Column("实际执行数量", ReportRowView::getExecutedQuantity),
                    new Column("设备明细数", ReportRowView::getDeviceCount), new Column("审批时间", ReportRowView::getApprovedAt),
                    new Column("执行时间", ReportRowView::getExecutedAt)));
            return result;
        }
        return COLUMNS.get(type);
    }
}
