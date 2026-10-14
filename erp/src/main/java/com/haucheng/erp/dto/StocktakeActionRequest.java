package com.haucheng.erp.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class StocktakeActionRequest {
    @NotNull @Min(0) private Long version;
}
