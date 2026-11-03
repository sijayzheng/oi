package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysRoleMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 角色-菜单关联数据访问层，映射表 {@code sys_role_menu}。
 *
 * @author sijay
 */
public interface SysRoleMenuRepository extends JpaRepository<SysRoleMenu, SysRoleMenu.Pk> {

    /**
     * 查询一组角色持有的全部菜单关联（多角色取并集，重复由调用方去重）。
     *
     * @param roleIds 角色 id 集合
     * @return 角色-菜单关联行列表；无关联时为空列表
     */
    List<SysRoleMenu> findByRoleIdIn(Collection<Long> roleIds);
}
