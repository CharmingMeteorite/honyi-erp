package com.haucheng.erp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Only known Vue routes are forwarded; API, health and missing assets retain their own status codes. */
@Controller
public class SpaController {
    @GetMapping({"/login", "/query", "/reports", "/system", "/notifications", "/403", "/404",
            "/system/users", "/system/departments", "/system/roles", "/system/permissions",
            "/system/warehouses", "/master-data/categories", "/master-data/units",
            "/master-data/brands", "/master-data/products", "/inventory/stocks",
            "/inventory/ledgers", "/inventory/inbounds", "/inventory/outbounds",
            "/inventory/stocktakes", "/inventory/openings", "/apply/requisitions",
            "/assets/repairs", "/assets/warranties", "/assets/exchanges",
            "/assets/devices", "/assets/qr"})
    public String page() {
        return "forward:/index.html";
    }
}
