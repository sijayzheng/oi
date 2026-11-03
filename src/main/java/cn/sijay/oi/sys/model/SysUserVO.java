package cn.sijay.oi.sys.model.user;

import cn.sijay.oi.sys.entity.SysUser;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户出参模型（视图对象），字段为 {@code sys_user} 的对外可见子集。
 *
 * <p>安全约定：<b>不含 password 字段</b>——密码密文不出服务层，杜绝序列化泄露；
 * 时间字段为 {@link LocalDateTime}，由全局 Jackson 配置序列化为 {@code yyyy-MM-dd HH:mm:ss}；
 * id 为 {@code Long}，由全局 Jackson 配置序列化为字符串（防前端精度丢失）。
 *
 * @author sijay
 */
@Data
public class SysUserVO {

    /**
     * 用户 id。
     */
    private Long id;

    /**
     * 登录名。
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
     * 头像文件 id（字符串形态）；统一文件管理服务（sys_file）的唯一引用。
     */
    private String avatar;

    /**
     * 启用状态；true=正常（原 NORMAL，可登录）/ false=禁用（原 DISABLED，拒绝登录）。
     */
    private Boolean enabled;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 由实体构建视图对象（实体 → VO 的唯一映射入口，新增对外字段只改此处）。
     *
     * @param entity 用户实体
     * @return 视图对象
     */
    public static SysUserVO from(SysUser entity) {
        SysUserVO vo = new SysUserVO();
        vo.setId(entity.getId());
        vo.setUsername(entity.getUsername());
        vo.setNickname(entity.getNickname());
        vo.setMobile(entity.getMobile());
        vo.setEmail(entity.getEmail());
        vo.setAvatar(entity.getAvatar());
        vo.setEnabled(entity.getEnabled());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
