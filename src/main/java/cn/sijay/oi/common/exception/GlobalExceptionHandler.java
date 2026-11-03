package cn.sijay.oi.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import cn.sijay.oi.common.Result;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器，实现系统异常的统一管理机制。
 *
 * <p>标注 {@link RestControllerAdvice}，拦截所有 Controller 层抛出的异常并按类型分类处理，
 * 各业务模块无需再编写 try-catch。所有处理路径统一：
 * <ol>
 *   <li>按统一格式以 ERROR 级别打印完整异常堆栈（见 {@link #logError(Exception)}）；</li>
 *   <li>返回标准化 {@link Result} 响应体（HTTP 状态码保持 200，业务语义由 body 中的 code 表达）。</li>
 * </ol>
 *
 * <p>异常分类与错误码约定（与 {@link Result} 类级注释一致）：
 * <table border="1">
 *   <tr><th>异常类型</th><th>code</th><th>说明</th></tr>
 *   <tr><td>{@link BusinessException}</td><td>异常自带（>=600；401/403 为 HTTP 语义码）</td><td>可预期业务失败，消息原样透传</td></tr>
 *   <tr><td>{@code NotLoginException}（sa-token）</td><td>401</td><td>未登录 / 令牌无效或过期 / 已被踢下线</td></tr>
 *   <tr><td>{@code NotPermissionException} / {@code NotRoleException}（sa-token）</td><td>403</td><td>已登录但无权限执行当前操作</td></tr>
 *   <tr><td>{@link MethodArgumentNotValidException} / {@code BindException}</td><td>400</td><td>@Valid @RequestBody / 表单绑定参数校验失败</td></tr>
 *   <tr><td>{@link HandlerMethodValidationException}</td><td>400</td><td>@RequestParam / @PathVariable 上的约束校验失败</td></tr>
 *   <tr><td>{@link ConstraintViolationException}</td><td>400</td><td>@Validated 方法级参数校验失败</td></tr>
 *   <tr><td>{@link MissingServletRequestParameterException}</td><td>400</td><td>缺少必填请求参数</td></tr>
 *   <tr><td>{@link HttpMessageNotReadableException}</td><td>400</td><td>请求体格式错误（如非法 JSON）</td></tr>
 *   <tr><td>{@link MethodArgumentTypeMismatchException}</td><td>400</td><td>请求参数类型不匹配</td></tr>
 *   <tr><td>{@code NoResourceFoundException}</td><td>404</td><td>请求路径无对应处理器</td></tr>
 *   <tr><td>{@code HttpRequestMethodNotSupportedException}</td><td>405</td><td>HTTP 方法与接口不匹配</td></tr>
 *   <tr><td>{@link NullPointerException}</td><td>500</td><td>空指针，对外仅提示"系统异常"，细节仅入日志</td></tr>
 *   <tr><td>{@link Exception}（兜底）</td><td>500</td><td>未识别异常，对外仅提示"系统异常"</td></tr>
 * </table>
 *
 * <p>注意：参数校验类异常的提示消息面向用户展示，可透传校验注解中的 message；
 * 系统级异常一律返回固定文案，避免堆栈、类名等敏感信息泄露。
 *
 * @author sijay
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 客户端参数错误状态码；语义与 HTTP 400 对齐。
     */
    private static final int CODE_BAD_REQUEST = 400;

    /**
     * 系统内部错误状态码；语义与 HTTP 500 对齐。
     */
    private static final int CODE_SYSTEM_ERROR = 500;

    /**
     * 资源不存在状态码；语义与 HTTP 404 对齐（请求路径无对应处理器）。
     */
    private static final int CODE_NOT_FOUND = 404;

    /**
     * 请求方法不支持状态码；语义与 HTTP 405 对齐（路径存在但 HTTP 方法不匹配）。
     */
    private static final int CODE_METHOD_NOT_ALLOWED = 405;

    /**
     * 未授权状态码；语义与 HTTP 401 对齐（未登录 / 令牌失效 / 已被踢下线）。
     */
    private static final int CODE_UNAUTHORIZED = 401;

    /**
     * 禁止访问状态码；语义与 HTTP 403 对齐（已登录但无权限）。
     */
    private static final int CODE_FORBIDDEN = 403;

    /**
     * 未授权的统一对外提示；不区分具体失效原因，防止令牌状态信息泄露。
     */
    private static final String MSG_UNAUTHORIZED = "未登录或登录状态已失效";

    /**
     * 无权限的统一对外提示；不透传所需权限标识，防止权限模型探测。
     */
    private static final String MSG_FORBIDDEN = "暂无权限执行该操作";

    /**
     * 系统级异常的统一对外提示；不携带任何内部细节，防止信息泄露。
     */
    private static final String MSG_SYSTEM_ERROR = "系统异常";

    /**
     * 参数校验兜底提示；无法提取到具体校验消息时使用。
     */
    private static final String MSG_VALIDATION_FALLBACK = "参数校验失败";

    /**
     * 处理 sa-token 未登录异常。
     *
     * <p>使用场景：全局鉴权拦截器（SaTokenConfigure）对受保护接口执行
     * {@code StpUtil.checkLogin()} 时令牌缺失/无效/过期/已被踢下线；
     * 统一返回 401，不区分具体失效原因，防止令牌状态信息泄露。
     *
     * @param e 未登录异常
     * @return code=401、message=固定提示 的失败响应
     */
    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLoginException(NotLoginException e) {
        log.error("[GlobalExceptionHandler] 异常类型: {}, 消息: {}", e.getClass().getName(), e.getMessage());
        return Result.fail(CODE_UNAUTHORIZED, MSG_UNAUTHORIZED);
    }

    /**
     * 处理 sa-token 无权限/无角色异常。
     *
     * <p>使用场景：已登录用户执行权限注解（@SaCheckPermission/@SaCheckRole）校验失败；
     * 统一返回 403，不透传所需权限标识。
     *
     * @param e 无权限异常
     * @return code=403、message=固定提示 的失败响应
     */
    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public Result<Void> handleNotPermissionException(RuntimeException e) {
        log.error("[GlobalExceptionHandler] 异常类型: {}, 消息: {}", e.getClass().getName(), e.getMessage());
        return Result.fail(CODE_FORBIDDEN, MSG_FORBIDDEN);
    }

    /**
     * 处理业务自定义异常。
     *
     * <p>使用场景：业务代码中主动抛出的可预期失败；错误码与消息原样透传给前端。
     *
     * @param e 业务异常
     * @return code=异常自带业务码、message=异常消息、data=null、total=null 的失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        logError(e);
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理 @Valid @RequestBody 与表单绑定的参数校验异常。
     *
     * <p>说明：Spring 6 起 {@link MethodArgumentNotValidException} 继承自 BindException，
     * 因此本方法同时覆盖两种场景；返回第一个字段错误的 defaultMessage 作为提示。
     *
     * @param e 参数绑定/校验异常
     * @return code=400、message=首个字段校验提示 的失败响应
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        logError(e);
        FieldError fieldError = e.getFieldError();
        String message = fieldError == null || fieldError.getDefaultMessage() == null
                ? MSG_VALIDATION_FALLBACK
                : fieldError.getDefaultMessage();
        return Result.fail(CODE_BAD_REQUEST, message);
    }

    /**
     * 处理 @RequestParam / @PathVariable 上的约束校验异常（Spring 6.1+ 方法级校验）。
     *
     * <p>使用场景：Controller 方法参数直接声明 @NotBlank、@Size 等约束注解时校验失败。
     *
     * @param e 方法级参数校验异常
     * @return code=400、message=首个校验提示 的失败响应
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Result<Void> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        logError(e);
        String message = e.getAllErrors().isEmpty() ? MSG_VALIDATION_FALLBACK : e.getAllErrors().getFirst().getDefaultMessage();
        return Result.fail(CODE_BAD_REQUEST, message == null ? MSG_VALIDATION_FALLBACK : message);
    }

    /**
     * 处理 @Validated 标注类的约束校验异常。
     *
     * <p>使用场景：Service/Controller 类标注 @Validated 后，方法参数约束校验失败；
     * 返回第一条违规项的提示，无违规详情时回退为兜底文案。
     *
     * @param e 约束校验异常
     * @return code=400、message=首个违规提示 的失败响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        logError(e);
        String message = e.getConstraintViolations() == null || e.getConstraintViolations().isEmpty()
                ? MSG_VALIDATION_FALLBACK
                : e.getConstraintViolations().iterator().next().getMessage();
        return Result.fail(CODE_BAD_REQUEST, message == null || message.isBlank() ? MSG_VALIDATION_FALLBACK : message);
    }

    /**
     * 处理缺少必填请求参数异常。
     *
     * @param e 缺参异常
     * @return code=400、message=含缺失参数名的提示 的失败响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException e) {
        logError(e);
        return Result.fail(CODE_BAD_REQUEST, "缺少必填参数: " + e.getParameterName());
    }

    /**
     * 处理请求体不可读异常（如非法 JSON、编码错误）。
     *
     * <p>注意：不透传底层解析细节，仅返回固定提示。
     *
     * @param e 请求体解析异常
     * @return code=400、message=固定提示 的失败响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        logError(e);
        return Result.fail(CODE_BAD_REQUEST, "请求体格式错误");
    }

    /**
     * 处理请求参数类型不匹配异常。
     *
     * @param e 类型不匹配异常
     * @return code=400、message=含参数名的提示 的失败响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        logError(e);
        return Result.fail(CODE_BAD_REQUEST, "参数类型错误: " + e.getName());
    }

    /**
     * 处理上传文件大小超出限制异常。
     *
     * <p>使用场景：上传文件超过 multipart 硬上限（application.yaml
     * {@code spring.servlet.multipart.max-file-size}），请求未达业务控制器即被
     * Servlet 容器拦截；统一返回文件模块的 606003，与业务级大小限制
     * （FileService 的 606003）语义一致。
     *
     * @param e 上传大小超限异常
     * @return code=606003、message=固定提示 的失败响应
     */
    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        logError(e);
        return Result.fail(BusinessErrorCode.FILE_SIZE_EXCEEDED.getCode(), BusinessErrorCode.FILE_SIZE_EXCEEDED.getMessage());
    }

    /**
     * 处理请求路径无对应处理器异常（404）。
     *
     * <p>使用场景：访问不存在的接口路径（Spring 6.1+ 静态资源链抛出
     * {@link NoResourceFoundException}）；此前落入兜底处理器返回 500"系统异常"，
     * 误导前端与调用方（回归测试 2026-11-03 发现），现显式映射为 404。
     *
     * @param e 资源不存在异常
     * @return code=404、message=资源不存在 的失败响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.error("[GlobalExceptionHandler] 异常类型: {}, 消息: {}", e.getClass().getName(), e.getMessage());
        return Result.fail(CODE_NOT_FOUND, "资源不存在");
    }

    /**
     * 处理请求方法不支持异常（405）。
     *
     * <p>使用场景：路径存在但 HTTP 方法不匹配（如对 POST 接口发起 PUT 请求）；
     * 此前落入兜底处理器返回 500"系统异常"，现显式映射为 405。
     *
     * @param e 方法不支持异常
     * @return code=405、message=请求方法不支持 的失败响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.error("[GlobalExceptionHandler] 异常类型: {}, 消息: {}", e.getClass().getName(), e.getMessage());
        return Result.fail(CODE_METHOD_NOT_ALLOWED, "请求方法不支持");
    }

    /**
     * 处理空指针异常。
     *
     * <p>使用场景：未捕获的 NPE；对外统一返回固定文案，避免暴露内部调用细节。
     *
     * @param e 空指针异常
     * @return code=500、message=系统异常 的失败响应
     */
    @ExceptionHandler(NullPointerException.class)
    public Result<Void> handleNullPointerException(NullPointerException e) {
        logError(e);
        return Result.fail(CODE_SYSTEM_ERROR, MSG_SYSTEM_ERROR);
    }

    /**
     * 兜底处理其余全部未识别异常。
     *
     * <p>使用场景：未被上方任何 handler 覆盖的异常；Spring 按异常继承关系选择
     * 最具体的 handler，因此本方法仅作最终兜底，不影响前面的分类处理。
     *
     * @param e 未识别异常
     * @return code=500、message=系统异常 的失败响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        logError(e);
        return Result.fail(CODE_SYSTEM_ERROR, MSG_SYSTEM_ERROR);
    }

    /**
     * 统一日志输出：所有异常处理路径共用本方法，保证日志格式一致。
     *
     * <p>格式：{@code [GlobalExceptionHandler] 异常类型: {全限定类名}, 消息: {异常消息}}，
     * 级别 ERROR，并以最后一个参数携带完整异常堆栈。
     *
     * @param e 已拦截的异常
     */
    private void logError(Exception e) {
        log.error("[GlobalExceptionHandler] 异常类型: {}, 消息: {}", e.getClass().getName(), e.getMessage(), e);
    }
}
