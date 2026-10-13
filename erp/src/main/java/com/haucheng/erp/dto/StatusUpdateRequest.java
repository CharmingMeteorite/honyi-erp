package com.haucheng.erp.dto;
import jakarta.validation.constraints.NotNull; import lombok.Data;
@Data public class StatusUpdateRequest { @NotNull(message="状态不能为空") private Boolean status; }
