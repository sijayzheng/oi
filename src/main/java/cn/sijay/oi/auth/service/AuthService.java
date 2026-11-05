package cn.sijay.oi.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.sijay.oi.auth.model.*;
import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.common.util.SmUtil;
import cn.sijay.oi.sys.entity.*;
import cn.sijay.oi.sys.model.SysFileVO;
import cn.sijay.oi.sys.repository.SysMenuRepository;
import cn.sijay.oi.sys.repository.SysRoleMenuRepository;
import cn.sijay.oi.sys.repository.SysUserRepository;
import cn.sijay.oi.sys.repository.SysUserRoleRepository;
import cn.sijay.oi.sys.service.SysFileService;
import cn.sijay.oi.sys.service.SysLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 认证业务层，提供用户登录认证、菜单权限树与强制踢人下线能力。
 *
 * <p><b>登录</b>：锁定校验（连续失败 5 次锁定 10 分钟，{@link LoginLockService}）→
 * 按用户名定位用户 → BCrypt 密文比对 → 账号状态校验 →
 * {@link StpUtil#login} 生成令牌。用户不存在与密码错误统一抛 601003，
 * 不区分具体原因，防止账号枚举探测；禁用账号抛 601004。
 *
 * <p><b>菜单权限树</b>：超管（用户 ID=1）返回全部菜单；普通用户经
 * 用户-角色 → 角色-菜单关联取并集后装配树形结构；无角色或角色未授权菜单
 * 返回空列表。DISABLED 节点整棵子树不生效（服务端剪枝）。
 *
 * <p><b>踢人下线</b>：仅允许用户 ID=1 的超管账号调用（其他账号抛 403），
 * 调用 sa-token {@link StpUtil#kickout(Object)} 作废目标用户全部登录令牌，
 * 被踢用户后续请求由全局鉴权拦截器统一返回 401。
 *
 * @author sijay
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * 超管用户 id；唯一允许执行踢人下线的账号（DDL 约定首个初始化用户），
     * 同时拥有全量菜单权限。
     */
    private static final long ADMIN_USER_ID = 1L;

    /**
     * 非管理员调用受限接口的失败码；语义与 HTTP 403 对齐（与 GlobalExceptionHandler 的 400/500 约定一致）。
     */
    private static final int CODE_FORBIDDEN = 403;

    /**
     * 两次密码不一致的失败码；语义与 HTTP 400 对齐（与 GlobalExceptionHandler 的 400 约定一致）。
     */
    private static final int CODE_BAD_REQUEST = 400;

    /**
     * 操作日志模块/动作标识（登录留痕）。
     */
    private static final String LOG_MODULE_AUTH = "auth";
    private static final String LOG_ACTION_LOGIN = "login";

    private final SysUserRepository userRepository;
    private final SysMenuRepository menuRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final SysRoleMenuRepository roleMenuRepository;
    private final SysFileService sysFileService;
    private final SysLogService logService;
    private final LoginLockService loginLockService;
    private final SmLoginKeyManager smLoginKeyManager;

    /**
     * 密码编码器（BCrypt）；与 SysUserService 落库编码保持同一算法，
     * 校验走 matches（salt 随机，密文不可直接比对）。
     */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户登录认证。
     *
     * <p>校验顺序：锁定校验（连续失败 5 次锁定 10 分钟，见 {@link LoginLockService}）→
     * 用户定位 + 密文比对（合并为 601003）→ 账号状态（禁用返回 601004）→
     * 全部通过后 {@link StpUtil#login} 生成令牌（并发登录共享令牌，见 application.yaml 配置）。
     * 登录成功后将客户端 IP 写入 Token-Session（在线用户列表展示与溯源用）。
     * 凭据失败累计计数（达到阈值锁定），登录成功重置计数。
     *
     * @param cmd      登录入参（格式校验已由 Bean Validation 完成）
     * @param clientIp 客户端 IP；在线用户展示与操作溯源用
     * @return 令牌对（tokenName + tokenValue）与用户昵称
     * @throws BusinessException 账号锁定中（601007）、用户名或密码错误（601003）、账号已禁用（601004）
     */
    public SysLoginVO login(SysLoginCmd cmd, String clientIp) {
        // 锁定校验前置：锁定期间拒绝一切登录请求（含密码正确的请求）
        loginLockService.checkNotLocked(cmd.getUsername());
        String rawPassword = resolvePassword(cmd);
        SysUser user = userRepository.findByUsername(cmd.getUsername()).orElse(null);
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            // 连续失败计数（达到 5 次触发锁定）；登录失败留痕（ERROR 级别，防枚举文案不入库——只记尝试用户名与 IP，
            // 密码（明文/密文/中间值）一律不入日志）
            loginLockService.recordFailure(cmd.getUsername());
            logService.record(LOG_MODULE_AUTH, LOG_ACTION_LOGIN, SysLog.LEVEL_ERROR, null, cmd.getUsername(),
                    clientIp, "/auth/login", "POST", "尝试登录用户: " + cmd.getUsername(),
                    SysLog.RESULT_FAIL, "用户名或密码错误");
            throw new BusinessException(BusinessErrorCode.USER_CREDENTIALS_ERROR);
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            logService.record(LOG_MODULE_AUTH, LOG_ACTION_LOGIN, SysLog.LEVEL_WARN, user.getId(), user.getUsername(),
                    clientIp, "/auth/login", "POST", "尝试登录用户: " + user.getUsername(),
                    SysLog.RESULT_FAIL, "账号已禁用");
            throw new BusinessException(BusinessErrorCode.USER_DISABLED);
        }
        StpUtil.login(user.getId());
        loginLockService.recordSuccess(cmd.getUsername());
        // 登录 IP 快照写入 Token-Session（并发共享令牌下随令牌续写，在线用户列表展示）
        StpUtil.getTokenSession().set("loginIp", clientIp);
        // 登录成功留痕（INFO 级别）
        logService.record(LOG_MODULE_AUTH, LOG_ACTION_LOGIN, SysLog.LEVEL_INFO, user.getId(), user.getUsername(),
                clientIp, "/auth/login", "POST", "用户登录成功", SysLog.RESULT_SUCCESS, null);
        log.info("[AuthService] 用户登录成功: id={}, username={}, ip={}", user.getId(), user.getUsername(), clientIp);
        return new SysLoginVO(StpUtil.getTokenName(), StpUtil.getTokenValue(), user.getNickname());
    }

    /**
     * 还原登录密码明文：携带 keyId 时按 SM2 私钥解密（国密加密登录），
     * 缺省时按明文兼容处理。
     *
     * <p>解密失败（keyId 未知/在途窗口已过、密文损坏）抛 601008 并计入登录
     * 失败锁定计数；解密过程不产生任何含密码明文/密文/中间值的日志。
     *
     * @param cmd 登录入参
     * @return 明文密码（供 BCrypt 校验）
     * @throws BusinessException 密码密文无效或已过期（601008）
     */
    private String resolvePassword(SysLoginCmd cmd) {
        if (cmd.getKeyId() == null || cmd.getKeyId().isBlank()) {
            return cmd.getPassword();
        }
        String privateKeyHex = smLoginKeyManager.privateKeyOf(cmd.getKeyId());
        if (privateKeyHex == null) {
            loginLockService.recordFailure(cmd.getUsername());
            throw new BusinessException(BusinessErrorCode.USER_PASSWORD_DECRYPT_FAILED);
        }
        try {
            byte[] plainBytes = SmUtil.sm2Decrypt(SmUtil.hexToBytes(cmd.getPassword()), privateKeyHex);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 密文损坏/格式非法：按登录失败处理（计入锁定计数），日志只记原因类型不记内容
            loginLockService.recordFailure(cmd.getUsername());
            log.warn("[AuthService] SM2 密码解密失败: username={}, reason={}", cmd.getUsername(), e.getClass().getSimpleName());
            throw new BusinessException(BusinessErrorCode.USER_PASSWORD_DECRYPT_FAILED);
        }
    }

    /**
     * 注销当前登录会话（令牌作废，前端退出登录入口）。
     */
    public void logout() {
        StpUtil.logout();
        log.info("[AuthService] 用户退出登录: id={}", StpUtil.getLoginIdDefaultNull());
    }

    /**
     * 获取当前登录用户的个人资料（个人中心回显）。
     *
     * @return 个人资料
     */
    @Transactional(readOnly = true)
    public SysProfileVO getProfile() {
        SysUser user = userRepository.findById(StpUtil.getLoginIdAsLong())
                                     .orElseThrow(() -> new BusinessException(BusinessErrorCode.USER_NOT_FOUND));
        return SysProfileVO.from(user);
    }

    /**
     * 更新当前登录用户的个人资料（昵称/手机/邮箱/头像；username/status/password 不可经此修改）。
     *
     * <p>实现说明：依托 JPA 持久化上下文脏检查，事务提交时自动生成 UPDATE；
     * 昵称变更后前端需同步刷新顶栏展示。
     *
     * @param cmd 资料修改入参（格式校验已由 Bean Validation 完成）
     * @return 更新后的个人资料
     */
    @Transactional
    public SysProfileVO updateProfile(SysProfileUpdateCmd cmd) {
        SysUser user = userRepository.findById(StpUtil.getLoginIdAsLong())
                                     .orElseThrow(() -> new BusinessException(BusinessErrorCode.USER_NOT_FOUND));
        user.setNickname(cmd.getNickname());
        user.setMobile(cmd.getMobile());
        user.setEmail(cmd.getEmail());
        user.setAvatar(cmd.getAvatar());
        user.setUpdateBy(user.getId());
        return SysProfileVO.from(user);
    }

    /**
     * 修改当前登录用户的密码。
     *
     * <p>校验顺序：原密码 BCrypt 比对（失败返回 601005）→ 两次新密码一致 →
     * 新密码 BCrypt 加密落库 → 作废当前登录会话（强制重新登录，保障账号安全）。
     *
     * @param cmd 修改密码入参（格式与强度校验已由 Bean Validation 完成）
     * @throws BusinessException 原密码错误（601005）、两次输入的密码不一致（400）
     */
    @Transactional
    public void changePassword(SysChangePasswordCmd cmd) {
        SysUser user = userRepository.findById(StpUtil.getLoginIdAsLong())
                                     .orElseThrow(() -> new BusinessException(BusinessErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(cmd.getOldPassword(), user.getPassword())) {
            throw new BusinessException(BusinessErrorCode.USER_OLD_PASSWORD_ERROR);
        }
        if (!cmd.getNewPassword().equals(cmd.getConfirmPassword())) {
            throw new BusinessException(CODE_BAD_REQUEST, "两次输入的密码不一致");
        }
        user.setPassword(passwordEncoder.encode(cmd.getNewPassword()));
        user.setUpdateBy(user.getId());
        StpUtil.logout();
        log.info("[AuthService] 密码修改成功并已注销当前会话: id={}", user.getId());
    }

    /**
     * 上传头像（统一文件管理服务委托）。
     *
     * <p>调用规范约定：认证模块不再自行处理文件实体或存储路径，仅调用
     * {@link SysFileService#uploadImage} 完成上传（图片白名单校验、大小限制、
     * 唯一标识与落盘均由文件服务统一实现），落库只保存返回的文件唯一 id。
     *
     * @param file 上传的图片文件
     * @return 文件信息（含唯一 id 与临时访问地址）
     * @throws BusinessException 文件为空（606001）、类型不支持（606002）、
     *                           大小超限（606003）、落盘失败（606006）
     */
    public SysFileVO uploadAvatar(MultipartFile file) {
        return sysFileService.uploadImage(file, "avatar");
    }

    /**
     * 获取当前登录用户的菜单权限树。
     *
     * <p>权限规则：
     * <ul>
     *   <li>超管（用户 ID=1）返回全部菜单；</li>
     *   <li>普通用户取其全部角色关联菜单的并集（去重）；无角色或未授权菜单返回空列表，
     *       前端仅有登录页与 404 可达；</li>
     *   <li>授权子集不含父目录时，该菜单上浮为根节点（保证已授权菜单可达）；</li>
     *   <li>status=DISABLED 的节点整棵子树剪枝（权限不生效，含已授权的子孙节点）；</li>
     *   <li>visible=false（隐藏）的节点整棵子树剪枝（不渲染、不生成路由、不参与权限计算，
     *       含已授权的子孙节点）。</li>
     * </ul>
     *
     * @return 菜单树根节点列表（children 永不为 null）；同级按 sort 升序、id 升序稳定排序
     */
    @Transactional(readOnly = true)
    public List<SysUserMenuVO> getCurrentUserMenus() {
        long loginId = StpUtil.getLoginIdAsLong();
        List<SysMenu> menus;
        if (loginId == ADMIN_USER_ID) {
            menus = menuRepository.findAll();
        } else {
            List<Long> roleIds = userRoleRepository.findByUserId(loginId)
                                                   .stream().map(SysUserRole::getRoleId).distinct().toList();
            if (roleIds.isEmpty()) {
                return List.of();
            }
            List<Long> menuIds = roleMenuRepository.findByRoleIdIn(roleIds)
                                                   .stream().map(SysRoleMenu::getMenuId).distinct().toList();
            if (menuIds.isEmpty()) {
                return List.of();
            }
            menus = menuRepository.findAllById(menuIds);
        }
        return buildMenuTree(menus);
    }

    /**
     * 将菜单平铺集合装配为树形结构（含 DISABLED / 隐藏子树剪枝）。
     *
     * <p>实现说明：先按 sort/id 排序保证同级顺序稳定；以 id 为键建索引，
     * 父节点不在授权子集内的节点上浮为根节点；递归装配时跳过 DISABLED 节点
     * 与 visible=false（隐藏）节点（整棵子树不输出）。
     *
     * @param menus 授权菜单平铺集合（可能乱序）
     * @return 菜单树根节点列表
     */
    private List<SysUserMenuVO> buildMenuTree(List<SysMenu> menus) {
        List<SysMenu> sorted = menus.stream()
                                    .sorted(Comparator.comparing(SysMenu::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                                                      .thenComparing(SysMenu::getId))
                                    .toList();
        Map<Long, SysMenu> menuById = sorted.stream()
                                            .collect(Collectors.toMap(SysMenu::getId, Function.identity()));
        Map<Long, List<SysMenu>> childrenByParent = sorted.stream()
                                                          .collect(Collectors.groupingBy(menu -> {
                                                              // 父节点不在授权子集内（未授权目录/根节点）时上浮为根
                                                              Long parentId = menu.getParentId() == null ? 0L : menu.getParentId();
                                                              return menuById.containsKey(parentId) ? parentId : 0L;
                                                          }));
        return buildChildren(childrenByParent.getOrDefault(0L, List.of()), childrenByParent);
    }

    /**
     * 递归装配子节点列表；DISABLED 节点与 visible=false（隐藏）节点整棵子树剪枝。
     */
    private List<SysUserMenuVO> buildChildren(List<SysMenu> siblings, Map<Long, List<SysMenu>> childrenByParent) {
        List<SysUserMenuVO> result = new ArrayList<>();
        for (SysMenu menu : siblings) {
            if (Boolean.FALSE.equals(menu.getEnabled()) || Boolean.FALSE.equals(menu.getVisible())) {
                continue;
            }
            SysUserMenuVO vo = SysUserMenuVO.from(menu);
            vo.setChildren(buildChildren(childrenByParent.getOrDefault(menu.getId(), List.of()), childrenByParent));
            result.add(vo);
        }
        return result;
    }

    /**
     * 强制踢人下线：作废目标用户的全部登录令牌。
     *
     * <p><b>权限校验</b>：仅当前登录用户 ID=1（超管）可调用，其余账号统一抛 403；
     * 目标用户当前未登录时视为"已下线"，静默成功（幂等，不向调用方暴露目标在线状态）。
     *
     * @param targetUserId 目标用户 id；被踢用户后续请求将统一返回 401
     * @throws BusinessException 当前登录人非超管（403）
     */
    public void kick(Long targetUserId) {
        if (StpUtil.getLoginIdAsLong() != ADMIN_USER_ID) {
            throw new BusinessException(CODE_FORBIDDEN, "仅管理员可执行踢人下线操作");
        }
        try {
            StpUtil.kickout(targetUserId);
            log.info("[AuthService] 踢人下线成功: 操作人={}, 目标={}", ADMIN_USER_ID, targetUserId);
        } catch (RuntimeException e) {
            // 目标用户当前无会话（未登录/已下线）时框架抛 NotLoginException，业务语义为"已是离线状态"，幂等成功
            log.info("[AuthService] 踢人下线目标无有效会话，视为已离线: 目标={}", targetUserId);
        }
    }
}
