package com.haucheng.erp.config;
import com.haucheng.erp.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
@RequiredArgsConstructor
public class SeedConfig {
    private final AuthService authService;
    @Value("${erp.auth.seed-username:}") private String username;
    @Value("${erp.auth.seed-password:}") private String password;
    @Value("${erp.auth.seed-real-name:}") private String realName;
    @Value("${erp.auth.seed-employee-no:}") private String employeeNo;
    @Bean public CommandLineRunner seedAdmin() {
        return args -> {
            if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
                authService.seedAdmin(username, password, realName, employeeNo);
            }
        };
    }
}
