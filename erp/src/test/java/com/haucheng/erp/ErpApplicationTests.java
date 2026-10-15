package com.haucheng.erp;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@AutoConfigureMockMvc
class ErpApplicationTests {
	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void healthReturnsUnifiedResponse() throws Exception {
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.status").value("UP"));
	}

	@Test
	void protectedEndpointRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(40100));
	}

	@Test
	void loginValidatesRequiredFields() throws Exception {
		mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"\",\"password\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(40001));
	}

	@Test
	void unknownApiReturnsUnifiedNotFoundResponse() throws Exception {
		mockMvc.perform(get("/api/does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(40400));
	}

	@Test
	void knownVueRouteForwardsButUnknownApiAndAssetsDoNot() throws Exception {
		mockMvc.perform(get("/assets/devices"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/missing-phase10d"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(40400));
		mockMvc.perform(get("/assets/missing-phase10d.js"))
				.andExpect(status().isNotFound());
	}

	@Test
	void readinessDoesNotClaimStorageIsReadyWhenItIsUnconfigured() throws Exception {
		mockMvc.perform(get("/health/ready"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.data.database").value("UP"))
				.andExpect(jsonPath("$.data.objectStorage").value("DOWN"));
	}

	@Test
	void requestIdIsReturnedAndClearedAfterRequest() throws Exception {
		mockMvc.perform(get("/health/live").header("X-Request-Id", "p10d-check"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Request-Id", "p10d-check"));
		assertNull(MDC.get("request_id"));
	}

}
