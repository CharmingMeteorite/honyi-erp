package com.haucheng.erp.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class QrBatchRequest {
    @NotEmpty(message = "请选择标签")
    @Size(max = 30, message = "每次最多打印30张标签")
    private List<@NotNull Long> ids;
}
