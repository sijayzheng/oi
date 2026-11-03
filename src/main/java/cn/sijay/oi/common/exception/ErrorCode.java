package cn.sijay.oi.common.exception;

/**
 * 标准错误码契约，统一业务失败码与错误描述的获取入口。
 *
 * <p>使用场景：由 {@link BusinessErrorCode} 等枚举实现，
 * 供 {@link BusinessException#BusinessException(ErrorCode)} 构造方法直接调用，
 * 避免硬编码错误码散落在业务代码中。
 *
 * <p>扩展方式：新增错误码枚举类时实现本接口即可无缝接入
 * {@link BusinessException} 与全局异常处理链路。
 *
 * @author sijay
 */
public interface ErrorCode {

    /**
     * 获取错误码。
     *
     * @return 错误码；业务失败码取值范围 600000~699999（遵循 {@code Result} 类级注释的错误码约定）
     */
    int getCode();

    /**
     * 获取错误描述。
     *
     * @return 面向用户的错误描述；非空、不含敏感信息，可直接作为前端提示文案
     */
    String getMessage();
}
