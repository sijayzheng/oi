package cn.sijay.oi.sys.model.role;

import cn.sijay.oi.sys.entity.SysRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色出参模型（视图对象），字段为 {@code sys_role} 的对外全量子集。
 *
 * <p>输出约定：id 为 {@code Long}，由全局 Jackson 配置序列化为字符串（防前端精度丢失）；
 * 时间字段为 {@link LocalDateTime}，由全局 Jackson 配置序列化为 {@code yyyy-MM-dd HH:mm:ss}；
 * status 同时输出枚举名与中文描述（statusDesc），供前端下拉回显与列表展示。
 *
 * @author sijay
 */
@Data
public class SysRoleVO {

    /**
     * 角色 id。
     */
    private Long id;

    /**
     * 角色编码。
     */
    private String code;

    /**
     * 角色显示名。
     */
    private String name;

    /**
     * 排序号。
     */
    private Integer sort;

    /**
     * 启用状态；true=正常（原 NORMAL）/ false=禁用（原 DISABLED，该角色的权限不再生效）。
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
     * @param entity 角色实体
     * @return 视图对象
     */
    public static SysRoleVO from(SysRole entity) {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setSort(entity.getSort());
        vo.setEnabled(entity.getEnabled());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
