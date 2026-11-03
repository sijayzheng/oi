package cn.sijay.oi.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GlobalExceptionHandler} 单元测试。
 *
 * <p>覆盖两类验证：
 * <ol>
 *   <li>直调各 handler 方法，断言不同异常类型下 {@code Result} 的 code / message / data / total 结构；</li>
 *   <li>通过 Logback ListAppender 断言 ERROR 级别日志与完整堆栈正常输出。</li>
 * </ol>
 * 另含 MockMvc 独立环境端到端用例，验证 @RestControllerAdvice 在 MVC 层真实拦截生效。
 */
@DisplayName("全局异常处理器测试")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class)).addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class)).detachAppender(logAppender);
    }

    // ==================== 直调单元测试 ====================

    @Test
    @DisplayName("业务异常：透传自定义错误码与消息")
    void businessException_returnsCodeAndMessage() {
        var result = handler.handleBusinessException(new BusinessException(601, "用户名已存在"));

        assertThat(result.code()).isEqualTo(601);
        assertThat(result.message()).isEqualTo("用户名已存在");
        assertThat(result.data()).isNull();
        assertThat(result.total()).isNull();
    }

    @Test
    @DisplayName("业务异常：未指定错误码时使用默认业务码 600")
    void businessException_defaultCode() {
        assertThat(new BusinessException("余额不足").getCode()).isEqualTo(600);
    }

    @Test
    @DisplayName("参数校验异常（@Valid @RequestBody）：返回首个字段错误与 400")
    void bindException_returnsFirstFieldError() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(TestController.class.getDeclaredMethod("valid", TestUser.class), 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new TestUser(), "testUser");
        bindingResult.addError(new FieldError("testUser", "username", "用户名不能为空"));

        var result = handler.handleBindException(new MethodArgumentNotValidException(parameter, bindingResult));

        assertThat(result.code()).isEqualTo(400);
        assertThat(result.message()).isEqualTo("用户名不能为空");
        assertThat(result.data()).isNull();
        assertThat(result.total()).isNull();
    }

    @Test
    @DisplayName("约束校验异常：无违规详情时回退兜底文案与 400")
    void constraintViolation_empty_returnsFallback() {
        var result = handler.handleConstraintViolationException(new ConstraintViolationException("校验失败", Set.of()));

        assertThat(result.code()).isEqualTo(400);
        assertThat(result.message()).isEqualTo("参数校验失败");
    }

    @Test
    @DisplayName("缺少必填参数：返回含参数名的提示与 400")
    void missingParameter_returnsParameterName() {
        var result = handler.handleMissingParameter(new MissingServletRequestParameterException("id", "Long"));

        assertThat(result.code()).isEqualTo(400);
        assertThat(result.message()).isEqualTo("缺少必填参数: id");
    }

    @Test
    @DisplayName("请求体不可读：返回固定提示与 400")
    void messageNotReadable_returnsFixedMessage() {
        var result = handler.handleMessageNotReadable(new HttpMessageNotReadableException("parse error", new MockHttpInputMessage(new byte[0])));

        assertThat(result.code()).isEqualTo(400);
        assertThat(result.message()).isEqualTo("请求体格式错误");
    }

    @Test
    @DisplayName("类型不匹配：返回含参数名的提示与 400")
    void typeMismatch_returnsParameterName() {
        var result = handler.handleTypeMismatch(new MethodArgumentTypeMismatchException("abc", Long.class, "id", null, new NumberFormatException("abc")));

        assertThat(result.code()).isEqualTo(400);
        assertThat(result.message()).isEqualTo("参数类型错误: id");
    }

    @Test
    @DisplayName("空指针异常：返回系统异常固定文案与 500")
    void nullPointer_returnsSystemError() {
        var result = handler.handleNullPointerException(new NullPointerException());

        assertThat(result.code()).isEqualTo(500);
        assertThat(result.message()).isEqualTo("系统异常");
    }

    @Test
    @DisplayName("兜底异常：返回系统异常固定文案与 500")
    void fallbackException_returnsSystemError() {
        var result = handler.handleException(new IllegalStateException("boom"));

        assertThat(result.code()).isEqualTo(500);
        assertThat(result.message()).isEqualTo("系统异常");
    }

    @Test
    @DisplayName("日志：ERROR 级别输出统一格式并携带完整堆栈")
    void logOutputsErrorLevelWithThrowable() {
        handler.handleBusinessException(new BusinessException(601, "用户名已存在"));

        assertThat(logAppender.list).isNotEmpty();
        ILoggingEvent event = logAppender.list.getLast();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("[GlobalExceptionHandler]");
        assertThat(event.getFormattedMessage()).contains(BusinessException.class.getName());
        assertThat(event.getThrowableProxy()).isNotNull();
    }

    // ==================== MockMvc 端到端测试 ====================

    @Test
    @DisplayName("MVC 拦截：业务异常经 @RestControllerAdvice 返回标准化 Result")
    void mockMvc_businessException_intercepted() throws Exception {
        MockMvc mockMvc = buildMockMvc();

        mockMvc.perform(get("/test/business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(601))
                .andExpect(jsonPath("$.message").value("用户名已存在"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.total").doesNotExist());
    }

    @Test
    @DisplayName("MVC 拦截：@Valid 请求体校验失败返回 400 提示")
    void mockMvc_validationFailure_intercepted() throws Exception {
        MockMvc mockMvc = buildMockMvc();

        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名不能为空"));
    }

    @Test
    @DisplayName("MVC 拦截：非法 JSON 请求体返回 400 固定提示")
    void mockMvc_illegalJson_intercepted() throws Exception {
        MockMvc mockMvc = buildMockMvc();

        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求体格式错误"));
    }

    @Test
    @DisplayName("MVC 拦截：空指针异常返回 500 系统异常")
    void mockMvc_nullPointer_intercepted() throws Exception {
        MockMvc mockMvc = buildMockMvc();

        mockMvc.perform(get("/test/npe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("系统异常"));
    }

    private MockMvc buildMockMvc() {
        return MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * 测试专用 Controller；仅用于触发各类异常，无业务逻辑。
     */
    @RestController
    static class TestController {

        @GetMapping("/test/business")
        public String business() {
            throw new BusinessException(601, "用户名已存在");
        }

        @PostMapping("/test/valid")
        public String valid(@Valid @RequestBody TestUser user) {
            return "ok";
        }

        @GetMapping("/test/npe")
        public String npe() {
            throw new NullPointerException();
        }

        @GetMapping("/test/param")
        public String param(@RequestParam @Size(min = 2) String name) {
            return "ok";
        }
    }

    /**
     * 测试专用请求体对象。
     */
    @Data
    static class TestUser {

        @NotBlank(message = "用户名不能为空")
        private String username;
    }
}
