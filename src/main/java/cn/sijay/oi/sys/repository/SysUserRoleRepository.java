package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysUserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 用户-角色关联数据访问层，映射表 {@code sys_user_role}。
 *
 * @author sijay
 */
public interface SysUserRoleRepository extends JpaRepository<SysUserRole, SysUserRole.Pk> {

    /**
     * 查询用户拥有的全部角色关联。
     *
     * @param userId 用户 id
     * @return 角色-用户关联行列表；无角色时为空列表
     */
    List<SysUserRole> findByUserId(Long userId);
}
