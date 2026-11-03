package cn.sijay.oi.online.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线用户出参模型（视图对象），在线用户列表的数据载体。
 *
 * <p>数据来源：sa-token 会话（登录时间/会话创建时间）+ sys_user / sys_role（用户与角色信息）；
 * id 为 {@code Long}，由全局 Jackson 配置序列化为字符串（防前端精度丢失）。
 *
 * @author sijay
 */
@Data
public class OnlineUserVO {

    /**
     * 用户 id；强制下线操作的目标标识。
     */
    private Long userId;

    /**
     * 登录名。
     */
    private String username;

    /**
     * 用户昵称。
     */
    private String nickname;

    /**
     * 用户角色名集合（顿号分隔）；无角色时为空串。
     */
    private String roles;

    /**
     * 登录 IP；登录时由服务端记录（Token-Session 快照）。
     */
    private String loginIp;

    /**
     * 登录时间；取会话建立时间（并发共享令牌下为首次登录时间）。
     */
    private LocalDateTime loginTime;

    /**
     * 在线时长（分钟）；服务端按当前时间计算。
     */
    private Long onlineMinutes;

    /**
     * 构建在线用户视图对象。
     *
     * @param userId        用户 id
     * @param username      登录名
     * @param nickname      昵称
     * @param roles         角色名（顿号分隔）
     * @param loginIp       登录 IP
     * @param loginTime     登录时间
     * @param onlineMinutes 在线时长（分钟）
     * @return 视图对象
     */
    public static OnlineUserVO of(Long userId, String username, String nickname, String roles,
                                  String loginIp, LocalDateTime loginTime, Long onlineMinutes) {
        OnlineUserVO vo = new OnlineUserVO();
        vo.setUserId(userId);
        vo.setUsername(username);
        vo.setNickname(nickname);
        vo.setRoles(roles);
        vo.setLoginIp(loginIp);
        vo.setLoginTime(loginTime);
        vo.setOnlineMinutes(onlineMinutes);
        return vo;
    }
}
