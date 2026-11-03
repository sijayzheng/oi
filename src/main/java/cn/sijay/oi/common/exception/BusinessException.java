package cn.sijay.oi.common.exception;

import lombok.Getter;

/**
 * 业务自定义异常，用于在业务逻辑中主动抛出可预期的失败。
 *
 * <p>使用场景：业务校验不通过（如用户名已存在）、资源状态不允许当前操作等
 * <b>可预期</b>的业务失败；系统级不可预期错误不应使用本类，直接抛出运行时异常
 * 由 {@link GlobalExceptionHandler} 兜底处理。
 *
 * <p>使用示例（优先使用标准错误码枚举）：
 * <pre>{@code
 * // 推荐：错误码与消息统一来源于 BusinessErrorCode 枚举
 * if (userMapper.existsByUsername(username)) {
 *     throw new BusinessException(BusinessErrorCode.USER_USERNAME_EXISTS);
 * }
 * // 尚未登记到枚举的临时场景：手工指定错误码
 * throw new BusinessException(601, "用户名已存在");
 * // 未指定错误码时使用默认业务失败码 600
 * throw new BusinessException("余额不足");
 * }</pre>
 *
 * <p>被 {@link GlobalExceptionHandler#handleBusinessException(BusinessException)} 捕获后，
 * 错误码与消息原样透传至前端，日志按统一格式记录。
 *
 * @author sijay
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 序列化版本号；本类继承 RuntimeException，参与 Java 序列化。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 默认业务失败码；未显式指定错误码时使用。
     * 取值 600，遵循 {@code Result} 类级注释中的错误码约定（业务码 >=600）。
     */
    public static final int DEFAULT_CODE = 600;

    /**
     * 业务错误码；透传至响应体的 code 字段。
     * 取值范围：>=600 的自定义业务码（与 Result 错误码约定一致）。
     */
    private final int code;

    /**
     * 构建业务异常（使用默认业务失败码 600）。
     *
     * @param message 面向用户的错误描述；将原样返回给前端
     */
    public BusinessException(String message) {
        super(message);
        this.code = DEFAULT_CODE;
    }

    /**
     * 构建业务异常（指定错误码）。
     *
     * @param code    业务错误码；取值 >=600，需在项目错误码规范中登记
     * @param message 面向用户的错误描述；将原样返回给前端
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构建业务异常（基于标准错误码枚举，推荐方式）。
     *
     * <p>使用场景：业务失败场景已由 {@link BusinessErrorCode} 登记；错误码与消息
     * 统一来源于枚举定义，避免硬编码散落。等价于
     * {@code new BusinessException(errorCode.getCode(), errorCode.getMessage())}。
     *
     * @param errorCode 业务错误码；实现 {@link ErrorCode} 接口的枚举（如 {@link BusinessErrorCode}）
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }
}
