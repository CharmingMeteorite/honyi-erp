package com.haucheng.erp.controller;
import com.haucheng.erp.common.ApiResponse;
import com.haucheng.erp.service.MinioObjectStorageService;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequiredArgsConstructor
public class HealthController {
    private final DataSource dataSource;
    private final MinioObjectStorageService objectStorage;
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() { return ApiResponse.success(Map.of("status", "UP")); }
    @GetMapping("/health/live")
    public ApiResponse<Map<String, String>> live() { return ApiResponse.success(Map.of("status", "UP")); }

    @GetMapping("/health/ready")
    public ResponseEntity<ApiResponse<Map<String, String>>> ready() {
        boolean databaseReady = databaseReady();
        boolean storageReady = objectStorage.isAvailable();
        Map<String, String> result = Map.of("status", databaseReady && storageReady ? "UP" : "DOWN",
                "database", databaseReady ? "UP" : "DOWN", "objectStorage", storageReady ? "UP" : "DOWN");
        return ResponseEntity.status(databaseReady && storageReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.success(result));
    }

    private boolean databaseReady() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(2);
            statement.execute("SELECT 1");
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
