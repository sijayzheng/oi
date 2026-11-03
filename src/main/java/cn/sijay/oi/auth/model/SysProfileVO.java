package cn.sijay.oi.auth.model;

import cn.sijay.oi.sys.entity.SysUser;
import lombok.Data;

/**
 * 当前登录用户的个人资料出参，个人中心页回显数据源。
 *
 * @author sijay
 */
@Data
public class SysProfileVO {

    /**
     * 用户 id；Long 由全局 Jackson 配置序列化为字符串。
     */
    private Long id;

    /**
     * 登录名；不可修改。
     */
    private String username;

    /**
     * 用户昵称。
     */
    private String nickname;

    /**
     * 手机号。
     */
    private String mobile;

    /**
     * 邮箱。
     */
    private String email;

    /**
     * 头像文件 id（字符串形态）；统一文件管理服务的唯一引用，展示时经
     * GET /file/{id}/url 换取临时访问地址。
     */
    private String avatar;

    /**
     * 由实体构建视图对象。
     *
     * @param entity 用户实体
     * @return 视图对象
     */
    public static SysProfileVO from(SysUser entity) {
        SysProfileVO vo = new SysProfileVO();
        vo.setId(entity.getId());
        vo.setUsername(entity.getUsername());
        vo.setNickname(entity.getNickname());
        vo.setMobile(entity.getMobile());
        vo.setEmail(entity.getEmail());
        vo.setAvatar(entity.getAvatar());
        return vo;
    }
}
