package com.haucheng.erp.controller;

import com.haucheng.erp.common.PageResult;
import com.haucheng.erp.exception.GlobalExceptionHandler;
import com.haucheng.erp.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest {
    private UserService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"page,invalid", "size,invalid", "page,1.5", "size,1.5",
            "page,2147483648", "size,2147483648", "departmentId,invalid", "status,invalid"})
    void rejectsInvalidParameterTypes(String name, String value) throws Exception {
        mockMvc.perform(get("/api/system/users").param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value("请求参数类型不正确"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(service);
    }

    @Test
    void passesNormalParametersToService() throws Exception {
        when(service.getPage("张", true, 10L, 2, 5))
                .thenReturn(new PageResult<>(List.of(), 2, 5, 0));
        mockMvc.perform(get("/api/system/users").param("keyword", "张").param("status", "true")
                        .param("departmentId", "10").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5));
        verify(service).getPage("张", true, 10L, 2, 5);
    }
}
