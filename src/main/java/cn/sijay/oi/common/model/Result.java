package cn.sijay.oi.common.model;

import java.io.Serializable;

/**
 * 统一响应结果类，用于标准化后端接口的返回格式。
 *
 * <p>基于 Java {@link Record} 实现，组件不可变，可作为任意 Controller 层方法的返回值，
 * 由 Jackson 序列化为如下 JSON 结构：
 * <pre>{@code
 * {
 *   "code": 200,
 *   "message": "操作成功",
 *   "data": { ... },       // 可为 null
 *   "total": 100           // 可为 null
 * }
 * }</pre>
 *
 * <p>使用示例：
 * <pre>{@code
 * @GetMapping("/user/{id}")
 * public Result<SysUserVO> getUser(@PathVariable Long id) {
 *     return Result.ok(userService.getById(id));
 * }
 *
 * @GetMapping("/user/page")
 * public Result<List<SysUserVO>> pageUsers(PageParam param) {
 *     Page<SysUserVO> page = userService.page(param);
 *     return Result.ok(page.getRecords(), page.getTotal());
 * }
 * }</pre>
 *
 * <p>本类仅承载响应结构本身，禁止在其中追加业务字段或业务逻辑；
 * 失败场景的状态码取值请遵循项目错误码规范，通用系统错误使用 {@link #fail(Exception)}。
 *
 * @param code    响应状态码；200=成功，其余=失败。取值范围：200 表示请求处理成功；
 *                400 表示客户端参数错误；401 表示未认证；403 表示无权限；
 *                500 表示服务器内部错误；其余自定义业务错误码建议 >=600，
 *                并在项目错误码规范中登记。使用场景：前端据此判断请求是否成功（code == 200）。
 * @param message 响应消息；成功时为"操作成功"等固定提示，失败时为面向调用方的错误描述；
 *                不含堆栈等敏感信息（敏感排查信息仅入日志）。取值范围：非空字符串，
 *                长度建议不超过 200 字符。使用场景：前端 toast/提示条直接展示给用户。
 * @param data    响应数据；泛型参数 T 表示任意业务数据类型（VO、List、Map 等）；
 *                无数据返回时为 {@code null}。使用场景：承载接口的业务返回值，
 *                分页列表时为当前页数据集合。
 * @param total   分页总记录数；仅分页查询成功响应时有值，其余场景为 {@code null}；
 *                取值范围：>=0 的整数。使用场景：前端据此计算分页页数与总数展示。
 * @param <T>     响应数据的业务类型
 * @author sijay
 */
public record Result<T>(int code, String message, T data, Long total) implements Serializable {

    /**
     * 成功状态码；所有成功响应统一使用，语义与 HTTP 200 对齐。
     */
    private static final int CODE_SUCCESS = 200;

    /**
     * 通用失败状态码；未指定错误码的失败场景统一使用，语义与 HTTP 500 对齐。
     */
    private static final int CODE_ERROR = 500;

    /**
     * 默认成功消息；成功工厂方法未显式指定消息时使用。
     */
    private static final String MSG_SUCCESS = "操作成功";

    /**
     * 默认失败消息；异常消息为空时的兜底文案，避免序列化出空消息。
     */
    private static final String MSG_ERROR = "系统异常";

    /**
     * 构建成功响应（无数据、无分页）。
     *
     * <p>使用场景：新增、删除、修改等只关心执行结果、无需回传数据的操作。
     *
     * @param <T> 响应数据的业务类型；无数据时由调用处按需推断
     * @return code=200、message=操作成功、data=null、total=null 的成功响应
     */
    public static <T> Result<T> ok() {
        return new Result<>(CODE_SUCCESS, MSG_SUCCESS, null, null);
    }

    /**
     * 构建成功响应（带返回数据、无分页）。
     *
     * <p>使用场景：查询单个对象、非分页列表等需要回传业务数据的接口。
     *
     * @param data 业务数据；允许为 {@code null}（表示业务上无结果）
     * @param <T>  响应数据的业务类型
     * @return code=200、message=操作成功、data=入参、total=null 的成功响应
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(CODE_SUCCESS, MSG_SUCCESS, data, null);
    }

    /**
     * 构建成功响应（带返回数据和分页总条数）。
     *
     * <p>使用场景：分页查询接口；data 携带当前页集合，total 携带总记录数。
     *
     * @param data  当前页业务数据集合
     * @param total 分页总记录数；取值 >=0，通常来自分页插件的 {@code getTotal()}
     * @param <T>   响应数据的业务类型
     * @return code=200、message=操作成功、data=入参、total=入参 的成功响应
     */
    public static <T> Result<T> ok(T data, Long total) {
        return new Result<>(CODE_SUCCESS, MSG_SUCCESS, data, total);
    }

    /**
     * 构建失败响应（仅状态码和错误消息）。
     *
     * <p>使用场景：业务校验不通过等可预期的失败，调用方明确知道错误码与提示语。
     *
     * @param code    失败状态码；取值见类级 {@code @param code} 说明，不应使用 200
     * @param message 面向用户的错误描述；建议非空且不含敏感信息
     * @param <T>     响应数据的业务类型
     * @return data=null、total=null 的失败响应
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, null);
    }

    /**
     * 构建失败响应（仅错误消息，状态码取通用失败码 500）。
     *
     * <p>使用场景：只需提示错误文案、无需自定义错误码的失败场景。
     *
     * @param message 面向用户的错误描述
     * @param <T>     响应数据的业务类型
     * @return code=500、data=null、total=null 的失败响应
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(CODE_ERROR, message, null, null);
    }

    /**
     * 基于异常对象构建失败响应（状态码取通用失败码 500）。
     *
     * <p>使用场景：全局异常处理器（@RestControllerAdvice）或 Controller 内
     * catch 到异常后快速包装返回；异常消息为空时回退为默认文案"系统异常"。
     * 注意：异常详情（堆栈）不应进入响应体，仅入日志。
     *
     * @param e   异常对象；取其 {@code getMessage()} 作为响应消息
     * @param <T> 响应数据的业务类型
     * @return code=500、message=异常消息、data=null、total=null 的失败响应
     */
    public static <T> Result<T> fail(Exception e) {
        String message = e.getMessage() == null || e.getMessage().isBlank() ? MSG_ERROR : e.getMessage();
        return new Result<>(CODE_ERROR, message, null, null);
    }
}
