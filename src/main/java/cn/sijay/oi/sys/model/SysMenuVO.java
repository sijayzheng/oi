package cn.sijay.oi.sys.model.menu;

import cn.sijay.oi.common.model.TreeNode;
import cn.sijay.oi.sys.entity.SysMenu;
import cn.sijay.oi.sys.enums.MenuType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单出参模型（视图对象），字段为 {@code sys_menu} 的对外全量子集。
 *
 * <p>输出约定：id/parentId 为 {@code Long}，由全局 Jackson 配置序列化为字符串
 * （防前端精度丢失）；时间字段由全局 Jackson 配置序列化为 {@code yyyy-MM-dd HH:mm:ss}；
 * type/status 同时输出枚举名与中文描述，visible 为布尔值（true=显示 / false=隐藏）。
 *
 * @author sijay
 */
@Data
public class SysMenuVO {

    /**
     * 菜单 id。
     */
    private Long id;

    /**
     * 父菜单 id；0=根节点。
     */
    private Long parentId;

    /**
     * 菜单/按钮名称。
     */
    private String name;

    /**
     * 节点类型；DIRECTORY / MENU / BUTTON。
     */
    private String type;

    /**
     * 节点类型中文描述；目录 / 菜单 / 按钮。
     */
    private String typeDesc;

    /**
     * 前端路由路径。
     */
    private String path;

    /**
     * 前端组件路径。
     */
    private String component;

    /**
     * 权限标识。
     */
    private String perms;

    /**
     * 图标标识。
     */
    private String icon;

    /**
     * 同级排序号。
     */
    private Integer sort;

    /**
     * 显示状态；true=显示 / false=隐藏。
     */
    private Boolean visible;

    /**
     * 启用状态；true=正常（原 NORMAL）/ false=禁用（原 DISABLED，整棵子树权限不生效）。
     */
    private Boolean enabled;

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
     * @param entity 菜单实体
     * @return 视图对象
     */
    public static SysMenuVO from(SysMenu entity) {
        SysMenuVO vo = new SysMenuVO();
        vo.setId(entity.getId());
        vo.setParentId(entity.getParentId());
        vo.setName(entity.getName());
        vo.setType(entity.getType() == null ? null : entity.getType().name());
        vo.setTypeDesc(entity.getType() == null ? null : entity.getType().getDesc());
        vo.setPath(entity.getPath());
        vo.setComponent(entity.getComponent());
        vo.setPerms(entity.getPerms());
        vo.setIcon(entity.getIcon());
        vo.setSort(entity.getSort());
        vo.setVisible(entity.getVisible());
        vo.setEnabled(entity.getEnabled());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 转换为通用树节点（树形接口装配用）。
     *
     * <p>映射规则：id=菜单 id、label=菜单名称、parentId（0=根 映射为 null）、
     * disabled=状态为 DISABLED；icon/perms 等附加字段如需输出，
     * 按约定定义组合节点 record（见 TreeNode 类注释的扩展方式）。
     *
     * @param entity 菜单实体
     * @return 通用树节点（children 为空可变列表，供 buildTree 装配）
     */
    public static TreeNode<Long> toTreeNode(SysMenu entity) {
        return new TreeNode<>(entity.getId(), entity.getName(),
                entity.getParentId() == null || entity.getParentId() == 0 ? null : entity.getParentId(),
                Boolean.FALSE.equals(entity.getEnabled()), new ArrayList<>());
    }
}
