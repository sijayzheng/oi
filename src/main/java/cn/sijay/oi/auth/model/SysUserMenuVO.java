package cn.sijay.oi.auth.model;

import cn.sijay.oi.sys.entity.SysMenu;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前登录用户的菜单树节点，前端动态路由与侧边栏导航的数据源。
 *
 * <p>输出约定：仅包含路由生成与导航渲染所需字段（不含审计字段）；
 * children 永不为 null（叶子节点为空列表）；DISABLED 节点及其子树
 * 在服务端剪枝，不会出现在返回结果中。
 *
 * @author sijay
 */
@Data
public class SysUserMenuVO {

    /**
     * 菜单 id；Long 由全局 Jackson 配置序列化为字符串。
     */
    private Long id;

    /**
     * 菜单/目录名称。
     */
    private String name;

    /**
     * 节点类型；DIRECTORY / MENU / BUTTON。
     */
    private String type;

    /**
     * 前端路由路径；DIRECTORY/MENU 使用。
     */
    private String path;

    /**
     * 前端组件路径；MENU 使用（相对 src/views 的视图路径，如 system/UserView）。
     */
    private String component;

    /**
     * 权限标识；BUTTON 使用（如 user:add），前端按钮级权限依据。
     */
    private String perms;

    /**
     * 图标标识；Element Plus 图标组件名。
     */
    private String icon;

    /**
     * 显示状态；true=显示 / false=隐藏（false 的节点整棵子树在服务端剪枝，
     * 不会出现在返回结果中：不渲染、不生成路由、不参与权限计算）。
     */
    private Boolean visible;

    /**
     * 子节点列表；叶子节点为空列表。
     */
    private List<SysUserMenuVO> children = new ArrayList<>();

    /**
     * 由菜单实体构建视图对象。
     *
     * @param entity 菜单实体
     * @return 树节点视图对象
     */
    public static SysUserMenuVO from(SysMenu entity) {
        SysUserMenuVO vo = new SysUserMenuVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setType(entity.getType() == null ? null : entity.getType().name());
        vo.setPath(entity.getPath());
        vo.setComponent(entity.getComponent());
        vo.setPerms(entity.getPerms());
        vo.setIcon(entity.getIcon());
        vo.setVisible(entity.getVisible());
        return vo;
    }
}
