package cn.sijay.oi.common.web;

import cn.sijay.oi.common.exception.ErrorCode;
import cn.sijay.oi.common.model.Result;

/**
 * 基础抽象控制器，项目中所有业务控制器的统一父类。
 *
 * <p><b>强制约定</b>：所有业务控制器必须继承本类，并通过本类提供的 protected 包装方法
 * 返回标准化的 {@link Result} 响应；<b>禁止在子类中直接构造 Result 结构
 * （{@code new Result<>(...)} 或直接调用 {@code Result.ok/fail} 静态工厂）</b>，
 * 以保证全局接口响应格式的一致性与演进收口点唯一。
 *
 * <p>使用示例：
 * <pre>{@code
 * @RestController
 * @RequestMapping("/user")
 * public class UserController extends BaseController {
 *
 *     @GetMapping("/{id}")
 *     public Result<SysUserVO> get(@PathVariable Long id) {
 *         return ok(userService.getById(id));          // 成功（带数据）
 *     }
 *
 *     @GetMapping("/page")
 *     public Result<List<SysUserVO>> page(SysUserQuery query) {
 *         query.validate();
 *         Page<SysUserVO> page = userService.page(query);
 *         return ok(page.getRecords(), page.getTotal()); // 成功（带分页总数）
 *     }
 *
 *     @PostMapping
 *     public Result<Void> create(@RequestBody @Valid UserSaveCmd cmd) {
 *         if (exists(cmd.getUsername())) {
 *             return fail(BusinessErrorCode.USER_USERNAME_EXISTS); // 标准错误码
 *         }
 *         return ok();                                   // 成功（无数据）
 *     }
 * }</pre>
 *
 * <p><b>扩展性设计</b>：本类是响应包装的唯一收口层——后续新增通用响应场景
 * （如特定错误码段、带额外头的响应、国际化消息等）时，仅需在本类追加 protected 方法，
 * 子类零改动即可调用。方法命名与 {@link Result} 工厂保持一致（ok / fail），薄委托不引入额外逻辑。
 *
 * @author sijay
 */
public abstract class BaseController {

    /**
     * 构建成功响应（无数据、无分页）。
     *
     * <p>使用场景：新增、删除、修改等只关心执行结果、无需回传数据的操作。
     *
     * @param <T> 响应数据类型；由调用处按需推断
     * @return code=200、data=null、total=null 的成功响应
     */
    protected <T> Result<T> ok() {
        return Result.ok();
    }

    /**
     * 构建成功响应（带返回数据、无分页）。
     *
     * <p>使用场景：查询单个对象、非分页列表等需要回传业务数据的接口。
     *
     * @param data 业务数据；允许为 null（表示业务上无结果）
     * @param <T>  响应数据类型
     * @return code=200、data=入参、total=null 的成功响应
     */
    protected <T> Result<T> ok(T data) {
        return Result.ok(data);
    }

    /**
     * 构建成功响应（带返回数据和分页总条数）。
     *
     * <p>使用场景：分页查询接口；data 携带当前页集合，total 携带总记录数。
     * 分页请求参数请使用 {@code PageQuery}（调用 {@code validate()} 后执行查询）。
     *
     * @param data  当前页业务数据集合
     * @param total 分页总记录数；取值 >=0
     * @param <T>   响应数据类型
     * @return code=200、data=入参、total=入参 的成功响应
     */
    protected <T> Result<T> ok(T data, Long total) {
        return Result.ok(data, total);
    }

    /**
     * 构建失败响应（仅错误消息，状态码取通用失败码 500）。
     *
     * <p>使用场景：只需提示错误文案、无需自定义错误码的失败场景。
     * 优先使用 {@link #fail(ErrorCode)} 走标准错误码体系。
     *
     * @param message 面向用户的错误描述
     * @param <T>     响应数据类型
     * @return code=500、data=null、total=null 的失败响应
     */
    protected <T> Result<T> fail(String message) {
        return Result.fail(message);
    }

    /**
     * 构建失败响应（自定义状态码与错误消息）。
     *
     * <p>使用场景：调用方明确知道错误码与提示语的失败场景；
     * 错误码取值遵循 {@code Result} 类级注释约定（业务码 >=600）。
     *
     * @param code    失败状态码；不应使用 200
     * @param message 面向用户的错误描述
     * @param <T>     响应数据类型
     * @return code=入参、message=入参、data=null、total=null 的失败响应
     */
    protected <T> Result<T> fail(int code, String message) {
        return Result.fail(code, message);
    }

    /**
     * 构建失败响应（基于标准错误码枚举，推荐方式）。
     *
     * <p>使用场景：业务失败场景已由 {@link cn.sijay.oi.common.exception.BusinessErrorCode}
     * 登记；错误码与消息统一来源于枚举定义，避免硬编码散落。
     *
     * @param errorCode 业务错误码；实现 {@link ErrorCode} 接口的枚举
     * @param <T>       响应数据类型
     * @return code=错误码、message=错误描述、data=null、total=null 的失败响应
     */
    protected <T> Result<T> fail(ErrorCode errorCode) {
        return Result.fail(errorCode.getCode(), errorCode.getMessage());
    }

    /**
     * 构建失败响应（基于异常对象，状态码取通用失败码 500）。
     *
     * <p>使用场景：Controller 内 catch 到异常后快速包装返回；
     * 注意：未捕获异常无需手动处理，全局异常处理器会统一兜底，
     * 仅在需要就地降级响应时使用本方法。
     *
     * @param e   异常对象；取其 message 作为响应消息，空消息回退为"系统异常"
     * @param <T> 响应数据类型
     * @return code=500、message=异常消息、data=null、total=null 的失败响应
     */
    protected <T> Result<T> fail(Exception e) {
        return Result.fail(e);
    }
}
