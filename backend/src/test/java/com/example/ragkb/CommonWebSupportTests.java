package com.example.ragkb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ragkb.common.api.ApiResponse;
import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import com.example.ragkb.common.exception.GlobalExceptionHandler;
import com.example.ragkb.common.page.PageResponse;
import com.example.ragkb.config.CorsConfig;
import com.example.ragkb.config.SecurityConfig;
import com.example.ragkb.security.JwtAuthenticationFilter;
import com.example.ragkb.security.RestAccessDeniedHandler;
import com.example.ragkb.security.JwtTokenProvider;
import com.example.ragkb.user.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = CommonWebSupportTests.TestController.class)
@Import({
        CommonWebSupportTests.TestController.class,
        GlobalExceptionHandler.class,
        CorsConfig.class,
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAccessDeniedHandler.class
})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class CommonWebSupportTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Validator validator;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @WithMockUser
    void shouldReturnUnifiedSuccessResponse() throws Exception {
        mockMvc.perform(post("/api/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"RAG"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value("RAG"));
    }

    @Test
    @WithMockUser
    void shouldReturnUnifiedValidationError() throws Exception {
        mockMvc.perform(post("/api/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数错误"))
                .andExpect(jsonPath("$.data.name").value("名称不能为空"));
    }

    @Test
    @WithMockUser
    void shouldReturnUnifiedBusinessError() throws Exception {
        mockMvc.perform(get("/api/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900))
                .andExpect(jsonPath("$.message").value("用户名已存在"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @WithMockUser
    void shouldReturnUnifiedMalformedJsonError() throws Exception {
        mockMvc.perform(post("/api/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("请求体格式错误"));
    }

    @Test
    @WithMockUser
    void shouldHideUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/api/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.message").value("服务器内部错误"));
    }

    @Test
    @WithMockUser
    void shouldReturnUnifiedNotFoundError() throws Exception {
        mockMvc.perform(get("/api/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @Test
    void shouldAllowConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/test/success")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"));
    }

    @Test
    void shouldValidateAndConvertPageModels() {
        com.example.ragkb.common.page.PageRequest invalidRequest =
                new com.example.ragkb.common.page.PageRequest(0, 101);
        assertThat(validator.validate(invalidRequest)).hasSize(2);

        PageImpl<String> pageData = new PageImpl<>(
                List.of("a", "b"),
                org.springframework.data.domain.PageRequest.of(1, 2),
                5);
        PageResponse<String> response = PageResponse.from(pageData);

        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @RestController
    @RequestMapping("/api/test")
    static class TestController {

        @PostMapping("/validation")
        ApiResponse<String> validate(@Valid @RequestBody TestRequest request) {
            return ApiResponse.success(request.name());
        }

        @GetMapping("/success")
        ApiResponse<Void> success() {
            return ApiResponse.success();
        }

        @GetMapping("/business")
        ApiResponse<Void> businessError() {
            throw new BusinessException(
                    ErrorCode.RESOURCE_CONFLICT,
                    "用户名已存在");
        }

        @GetMapping("/unexpected")
        ApiResponse<Void> unexpectedError() {
            throw new IllegalStateException("sensitive internal detail");
        }
    }

    record TestRequest(
            @NotBlank(message = "名称不能为空")
            String name) {
    }
}
