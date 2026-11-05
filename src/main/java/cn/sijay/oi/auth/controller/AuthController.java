package cn.sijay.oi.auth.controller;

import cn.sijay.oi.auth.model.*;
import cn.sijay.oi.auth.service.AuthService;
import cn.sijay.oi.auth.service.SmLoginKeyManager;
import cn.sijay.oi.common.model.Result;
import cn.sijay.oi.common.util.IpUtils;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.sys.model.SysFileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 认证控制器，提供登录认证、个人中心与强制踢人下线接口。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装方法构建
 * {@link Result}；入参格式校验由 Bean Validation（@Valid）触发，校验失败与业务异常
 * 均由全局异常处理器统一包装，控制器内不写 try-catch。
 *
 * <p>鉴权约定：{@code /auth/login} 为全局唯一公开接口（见
 * {@code SaTokenConfigure} 豁免清单）；其余 /auth/** 接口需登录态，
 * 其中 {@code /auth/kick} 仅用户 ID=1 的超管可调用（非超管返回 403）。
 *
 * <p>接口清单：
 * <pre>
 * POST   /auth/login         用户登录，返回 sa-token 令牌（Result&lt;SysLoginVO&gt;）
 * POST   /auth/logout        退出登录，作废当前令牌（Result&lt;Void&gt;）
 * GET    /auth/profile       当前用户个人资料（Result&lt;SysProfileVO&gt;）
 * PUT    /auth/profile       修改个人资料（Result&lt;SysProfileVO&gt;）
 * POST   /auth/password      修改密码，成功后注销当前会话（Result&lt;Void&gt;）
 * POST   /auth/avatar        上传头像（委托统一文件服务），返回文件 id（Result&lt;SysFileVO&gt;）
 * GET    /auth/menus         当前用户菜单权限树，动态路由数据源（Result&lt;List&lt;SysUserMenuVO&gt;&gt;）
 * POST   /auth/kick/{userId} 强制踢人下线，仅超管可用（Result&lt;Void&gt;）
 * </pre>
 *
 * @author sijay
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController extends BaseController {

    private final AuthService authService;
    private final SmLoginKeyManager smLoginKeyManager;

    /**
     * 用户登录。
     *
     * <p>校验通过后生成 sa-token 令牌；前端后续访问受保护接口时携带
     * {@code satoken: <tokenValue>} 请求头。用户名或密码错误返回 601003
     * （不区分具体原因，防账号枚举），账号禁用返回 601004；
     * 客户端 IP 随登录写入 Token-Session（在线用户展示用）。
     *
     * @param cmd     登录入参；username/password 必填
     * @param request 当前 HTTP 请求（解析客户端 IP）
     * @return 令牌对（tokenName + tokenValue）
     */
    @PostMapping("/login")
    public Result<SysLoginVO> login(@RequestBody @Valid SysLoginCmd cmd, HttpServletRequest request) {
        return ok(authService.login(cmd, IpUtils.getClientIp(request)));
    }

    /**
     * 下发登录密码加密用 SM2 公钥（国密加密登录第一步）。
     *
     * <p>公开端点（登录前调用）；响应携带 keyId、公钥（C1C3C2 非压缩格式 hex）
     * 与完整性摘要 {@code digest = SM3(publicKey)}——前端经独立 SM3 实现复算
     * 摘要比对，校验公钥在传输链路中未被篡改。公钥由 SmLoginKeyManager 定期
     * 轮换；配套私钥仅存于服务端，永不出现在响应或日志中。
     *
     * @return keyId + 公钥 + SM3 完整性摘要 + 过期时间
     */
    @GetMapping("/sm2/public-key")
    public Result<SmKeyVO> sm2PublicKey() {
        SmLoginKeyManager.SmKey key = smLoginKeyManager.currentKey();
        return ok(new SmKeyVO(key.keyId(), key.publicHex(), SmLoginKeyManager.digestOf(key.publicHex()),
                key.expireAt().toString()));
    }

    /**
     * 退出登录。
     *
     * <p>作废当前登录会话的全部令牌；前端调用后清除本地令牌并跳转登录页。
     *
     * @return 成功响应（无数据）
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return ok();
    }

    /**
     * 查询当前登录用户的个人资料。
     *
     * @return 个人资料（不含密码）
     */
    @GetMapping("/profile")
    public Result<SysProfileVO> profile() {
        return ok(authService.getProfile());
    }

    /**
     * 修改当前登录用户的个人资料（昵称/手机/邮箱/头像）。
     *
     * @param cmd 资料修改入参；昵称必填
     * @return 更新后的个人资料
     */
    @PutMapping("/profile")
    public Result<SysProfileVO> updateProfile(@RequestBody @Valid SysProfileUpdateCmd cmd) {
        return ok(authService.updateProfile(cmd));
    }

    /**
     * 修改当前登录用户的密码。
     *
     * <p>原密码错误返回 601005、两次新密码不一致返回 400；成功后注销当前会话，
     * 前端需引导用户重新登录。
     *
     * @param cmd 修改密码入参；新密码须同时包含字母和数字
     * @return 成功响应（无数据）
     */
    @PostMapping("/password")
    public Result<Void> changePassword(@RequestBody @Valid SysChangePasswordCmd cmd) {
        authService.changePassword(cmd);
        return ok();
    }

    /**
     * 上传头像（委托统一文件管理服务）。
     *
     * <p>仅支持 jpg/jpeg/png/gif/webp（图片白名单），大小限制由文件服务配置约束；
     * 返回的文件 id 通过 PUT /auth/profile 保存到个人资料（只存 id，不存 URL）。
     *
     * @param file 头像图片文件（multipart 字段名 file）
     * @return 文件信息（含唯一 id 与临时访问地址）
     */
    @PostMapping("/avatar")
    public Result<SysFileVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ok(authService.uploadAvatar(file));
    }

    /**
     * 获取当前登录用户的菜单权限树。
     *
     * <p>动态路由数据源：超管返回全部菜单，普通用户返回其角色关联菜单的并集
     * （无角色或未授权返回空列表）；前端据此动态注册路由与渲染侧边栏，
     * 未授权路由不可达（直接 404）。
     *
     * @return 菜单树根节点列表
     */
    @GetMapping("/menus")
    public Result<List<SysUserMenuVO>> menus() {
        return ok(authService.getCurrentUserMenus());
    }

    /**
     * 强制踢人下线（仅超管可用）。
     *
     * <p>作废目标用户当前全部登录令牌，被踢用户后续请求由全局鉴权拦截器
     * 统一返回 401。当前登录人非用户 ID=1 时返回 403；目标用户未在线时幂等成功。
     *
     * @param userId 目标用户 id；必须为正整数
     * @return 成功响应（无数据）
     */
    @PostMapping("/kick/{userId}")
    public Result<Void> kick(@PathVariable @Positive(message = "目标用户 id 必须为正整数") Long userId) {
        authService.kick(userId);
        return ok();
    }
}
