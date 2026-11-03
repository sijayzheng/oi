package cn.sijay.oi.online.controller;

import cn.sijay.oi.common.Result;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.online.model.OnlineUserVO;
import cn.sijay.oi.online.service.OnlineService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在线用户控制器，提供在线会话实时列表与强制下线接口（仅系统管理员可用）。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装方法构建
 * {@link Result}；业务异常由全局异常处理器统一包装，控制器内不写 try-catch。
 *
 * <p>鉴权约定：本模块全部接口仅允许超管（用户 ID=1）访问，其余登录账号统一返回 403
 * （见 {@code OnlineService.checkAdmin}）；菜单侧通过动态路由权限（viewer 角色未授权）
 * 实现入口隔离，后端硬校验兜底防越权。
 *
 * <p>接口清单：
 * <pre>
 * GET  /online/list          在线用户实时列表（Result&lt;List&lt;OnlineUserVO&gt;&gt;）
 * POST /online/kick/{userId} 强制下线目标用户，写入操作日志（Result&lt;Void&gt;）
 * </pre>
 *
 * @author sijay
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/online")
public class OnlineController extends BaseController {

    private final OnlineService onlineService;

    /**
     * 查询在线用户实时列表。
     *
     * <p>数据以 sa-token 会话为准实时同步：会话被踢出/注销/过期后即从列表消失。
     *
     * @return 在线用户列表（含用户名/昵称/角色/登录 IP/登录时间/在线时长）
     */
    @GetMapping("/list")
    public Result<List<OnlineUserVO>> list() {
        onlineService.checkAdmin();
        return ok(onlineService.list());
    }

    /**
     * 强制下线目标用户（仅系统管理员，操作前前端二次确认）。
     *
     * <p>作废目标用户全部登录令牌，被踢用户后续请求统一返回 401 并跳转登录页；
     * 操作写入 sys_log 留痕（操作人/目标/客户端 IP），目标不在线时幂等成功。
     *
     * @param userId  目标用户 id
     * @param request 当前 HTTP 请求（记录操作人客户端 IP）
     * @return 成功响应（无数据）
     */
    @PostMapping("/kick/{userId}")
    public Result<Void> kick(@PathVariable @Positive(message = "目标用户 id 必须为正整数") Long userId,
                             HttpServletRequest request) {
        onlineService.kick(userId, request);
        return ok();
    }
}
