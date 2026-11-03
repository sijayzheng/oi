package cn.sijay.oi.online.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.common.util.IpUtils;
import cn.sijay.oi.online.model.OnlineUserVO;
import cn.sijay.oi.sys.entity.SysLog;
import cn.sijay.oi.sys.entity.SysUser;
import cn.sijay.oi.sys.entity.SysUserRole;
import cn.sijay.oi.sys.repository.SysRoleRepository;
import cn.sijay.oi.sys.repository.SysUserRepository;
import cn.sijay.oi.sys.repository.SysUserRoleRepository;
import cn.sijay.oi.sys.service.SysLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 在线用户业务层，提供在线会话实时列表与强制下线能力。
 *
 * <p><b>在线判定</b>：以 sa-token 令牌缓存为准——遍历全部有效令牌
 * （并发共享令牌配置下每用户一个令牌），关联用户与角色信息后输出。
 * 会话被踢出/注销/过期后自然从列表消失，状态实时同步。
 *
 * <p><b>强制下线</b>：sa-token {@link StpUtil#kickout} 作废目标用户全部登录令牌，
 * 被踢用户后续请求由全局鉴权拦截器统一返回 401，前端自动跳转登录页；
 * 幂等语义：目标用户当前不在线视为已下线，静默成功。
 *
 * <p><b>权限约束</b>：本模块全部操作仅允许超管（用户 ID=1）执行，
 * 其余登录账号统一拒绝（403），与 {@code /auth/kick} 口径一致。
 *
 * <p><b>操作留痕</b>：强制下线写入 sys_log（module=online/action=kick），
 * 记录操作人、目标用户与客户端 IP，保证可追溯。
 *
 * @author sijay
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineService {

    /**
     * 超管用户 id；唯一允许访问在线用户模块的账号（与踢人下线口径一致）。
     */
    private static final long ADMIN_USER_ID = 1L;

    /**
     * 非管理员调用受限接口的失败码；语义与 HTTP 403 对齐。
     */
    private static final int CODE_FORBIDDEN = 403;

    /**
     * Token-Session 中记录登录 IP 的键名；与 AuthService.login 写入约定一致。
     */
    private static final String SESSION_KEY_LOGIN_IP = "loginIp";

    /**
     * 操作日志模块/动作标识。
     */
    private static final String LOG_MODULE_ONLINE = "online";
    private static final String LOG_ACTION_KICK = "kick";

    private final SysUserRepository userRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final SysRoleRepository roleRepository;
    private final SysLogService logService;

    /**
     * 查询当前全部在线用户（实时列表）。
     *
     * <p>实现说明：遍历 sa-token 全部有效令牌 → 解析登录人 → 关联用户档案与角色名 →
     * 组装登录 IP（Token-Session 快照，未记录时回退"未知"）、登录时间（会话创建时间）
     * 与在线时长（按当前时间计算）；按登录时间倒序（最新会话在前）。
     *
     * @return 在线用户列表；无在线用户时为空列表
     */
    public List<OnlineUserVO> list() {
        List<String> tokenKeys = StpUtil.searchTokenValue("", 0, -1, false);
        List<OnlineUserVO> result = new ArrayList<>();
        for (String tokenKey : tokenKeys) {
            // searchTokenValue 返回完整缓存键（如 satoken:login:token:{tokenValue}），剥离前缀取令牌值
            String tokenValue = tokenKey.substring(tokenKey.lastIndexOf(':') + 1);
            Object loginIdObj = StpUtil.getLoginIdByToken(tokenValue);
            if (loginIdObj == null) {
                continue;
            }
            long userId = Long.parseLong(loginIdObj.toString());
            // isCreate=false：仅读取会话，避免为异常令牌创建空会话
            SaSession tokenSession = StpUtil.stpLogic.getTokenSessionByToken(tokenValue, false);
            LocalDateTime loginTime = tokenSession == null
                    ? null
                    : LocalDateTime.ofInstant(Instant.ofEpochMilli(tokenSession.getCreateTime()), ZoneId.systemDefault());
            String loginIp = tokenSession == null ? "" : tokenSession.getString(SESSION_KEY_LOGIN_IP);
            result.add(buildOnlineUser(userId, loginIp, loginTime));
        }
        result.sort(Comparator.comparing(OnlineUserVO::getLoginTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    /**
     * 强制下线目标用户（仅超管可执行，操作全程留痕）。
     *
     * <p>执行流程：超管校验（403）→ 作废目标用户全部令牌（幂等：目标不在线视为已下线）→
     * 写入操作日志（sys_log：操作人/目标/客户端 IP）。
     *
     * @param targetUserId 目标用户 id
     * @param request      当前 HTTP 请求（记录操作人客户端 IP）
     * @throws BusinessException 当前登录人非超管（403）
     */
    public void kick(Long targetUserId, HttpServletRequest request) {
        long operatorId = StpUtil.getLoginIdAsLong();
        if (operatorId != ADMIN_USER_ID) {
            throw new BusinessException(CODE_FORBIDDEN, "仅系统管理员可执行强制下线操作");
        }
        SysUser operator = userRepository.findById(operatorId).orElse(null);
        SysUser target = userRepository.findById(targetUserId).orElse(null);
        String targetDesc = target == null
                ? String.valueOf(targetUserId)
                : target.getId() + "(" + target.getUsername() + ")";
        try {
            StpUtil.kickout(targetUserId);
            saveOperationLog(operator, request, "目标用户: " + targetDesc, SysLog.RESULT_SUCCESS, null);
            log.info("[OnlineService] 强制下线成功: 操作人={}, 目标={}", operatorId, targetDesc);
        }
        catch (RuntimeException e) {
            saveOperationLog(operator, request, "目标用户: " + targetDesc, SysLog.RESULT_FAIL, e.getMessage());
            throw e;
        }
    }

    /**
     * 校验当前登录人是否超管（在线用户模块统一准入约束）。
     *
     * @throws BusinessException 当前登录人非超管（403）
     */
    public void checkAdmin() {
        if (StpUtil.getLoginIdAsLong() != ADMIN_USER_ID) {
            throw new BusinessException(CODE_FORBIDDEN, "仅系统管理员可查看在线用户");
        }
    }

    /**
     * 组装单个在线用户视图（会话 + 用户档案 + 角色名）。
     */
    private OnlineUserVO buildOnlineUser(long userId, String loginIp, LocalDateTime loginTime) {
        SysUser user = userRepository.findById(userId).orElse(null);
        String username = user == null ? "未知用户(" + userId + ")" : user.getUsername();
        String nickname = user == null ? username : user.getNickname();
        long onlineMinutes = loginTime == null
                ? 0
                : Math.max(0, Duration.between(loginTime, LocalDateTime.now()).toMinutes());
        return OnlineUserVO.of(userId, username, nickname, resolveRoleNames(userId),
                loginIp == null || loginIp.isBlank() ? "未知" : loginIp, loginTime, onlineMinutes);
    }

    /**
     * 解析用户全部角色名（顿号分隔）；无角色返回空串。
     */
    private String resolveRoleNames(long userId) {
        List<Long> roleIds = userRoleRepository.findByUserId(userId)
                .stream().map(SysUserRole::getRoleId).distinct().toList();
        if (roleIds.isEmpty()) {
            return "";
        }
        return roleRepository.findAllById(roleIds).stream()
                .map(role -> role.getName())
                .collect(Collectors.joining("、"));
    }

    /**
     * 写入强制下线操作日志（经统一日志入口 record，INFO 级别，失败不阻断主流程）。
     */
    private void saveOperationLog(SysUser operator, HttpServletRequest request,
                                  String params, String result, String errorMsg) {
        logService.record(LOG_MODULE_ONLINE, LOG_ACTION_KICK, SysLog.LEVEL_INFO,
                operator == null ? null : operator.getId(),
                operator == null ? null : operator.getUsername(),
                request == null ? "" : IpUtils.getClientIp(request),
                request == null ? "" : request.getRequestURI(),
                request == null ? "" : request.getMethod(),
                params, result, errorMsg);
    }
}
