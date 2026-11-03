package cn.sijay.oi.sys.service;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.sys.entity.SysRole;
import cn.sijay.oi.sys.model.role.SysRoleCreateCmd;
import cn.sijay.oi.sys.model.role.SysRoleQuery;
import cn.sijay.oi.sys.model.role.SysRoleUpdateCmd;
import cn.sijay.oi.sys.repository.SysRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色业务层，提供角色模块的增删改查与业务校验。
 *
 * <p>分层约定：参数格式校验（非空/长度/格式）由入参模型的 Bean Validation 完成；
 * 本层负责<b>业务合法性校验</b>（编码唯一、角色存在性），失败抛出
 * {@link BusinessException}（标准错误码），由全局异常处理器统一包装为 {@code Result}。
 *
 * <p>事务约定：写操作 {@code @Transactional}，读操作 {@code @Transactional(readOnly = true)}。
 *
 * @author sijay
 */
@Service
@RequiredArgsConstructor
public class SysRoleService {

    /**
     * 系统操作人 id；0=系统初始化（DDL 约定），接入 sa-token 后替换为当前登录人 id。
     */
    private static final long SYSTEM_OPERATOR_ID = 0L;

    private final SysRoleRepository roleRepository;

    /**
     * 创建角色。
     *
     * <p>业务校验：角色编码唯一（查询层自动过滤软删行，软删后的同编码角色可重建，
     * 与唯一键 uk_role_code 语义一致）。
     *
     * @param cmd 创建入参（格式校验已由 Bean Validation 完成）
     * @return 已落库的角色实体（含自增 id）
     * @throws BusinessException 角色编码已存在（错误码 602002）
     */
    @Transactional
    public SysRole create(SysRoleCreateCmd cmd) {
        if (roleRepository.existsByCode(cmd.getCode())) {
            throw new BusinessException(BusinessErrorCode.ROLE_CODE_EXISTS);
        }
        SysRole role = new SysRole();
        role.setCode(cmd.getCode());
        role.setName(cmd.getName());
        role.setSort(cmd.getSort() == null ? 0 : cmd.getSort());
        role.setEnabled(cmd.getEnabled());
        role.setRemark(cmd.getRemark());
        role.setCreateBy(SYSTEM_OPERATOR_ID);
        return roleRepository.save(role);
    }

    /**
     * 按 id 查询角色详情。
     *
     * @param id 角色 id
     * @return 角色实体
     * @throws BusinessException 角色不存在或已软删（错误码 602001）
     */
    @Transactional(readOnly = true)
    public SysRole getById(Long id) {
        return roleRepository.findById(id)
                             .orElseThrow(() -> new BusinessException(BusinessErrorCode.ROLE_NOT_FOUND));
    }

    /**
     * 按 id 更新角色档案（code 不可通过本方法修改，见 SysRoleUpdateCmd 约定）。
     *
     * <p>实现说明：依托 JPA 持久化上下文脏检查，事务提交时自动生成 UPDATE，
     * 无需显式 save；updateTime 由 Hibernate 时间戳注解自动刷新。
     *
     * @param id  角色 id
     * @param cmd 更新入参（格式校验已由 Bean Validation 完成）
     * @return 更新后的角色实体
     * @throws BusinessException 角色不存在或已软删（错误码 602001）
     */
    @Transactional
    public SysRole update(Long id, SysRoleUpdateCmd cmd) {
        SysRole role = getById(id);
        role.setName(cmd.getName());
        role.setSort(cmd.getSort());
        role.setEnabled(cmd.getEnabled());
        role.setRemark(cmd.getRemark());
        role.setUpdateBy(SYSTEM_OPERATOR_ID);
        return role;
    }

    /**
     * 按 id 软删除角色（deleted 置为本行主键 id，物理数据保留供审计）。
     *
     * <p>注意：本方法暂不校验角色是否仍被用户/菜单授权引用；
     * sys_user_role / sys_role_menu 授权模块接入时，应在此前置"存在授权引用则拒绝删除"校验。
     *
     * @param id 角色 id
     * @throws BusinessException 角色不存在或已软删（错误码 602001）
     */
    @Transactional
    public void delete(Long id) {
        if (roleRepository.softDelete(id) == 0) {
            throw new BusinessException(BusinessErrorCode.ROLE_NOT_FOUND);
        }
    }

    /**
     * 分页条件查询角色列表。
     *
     * <p>实现说明：Query-by-Example——以过滤参数构建探针实体，
     * code/name 走 {@code CONTAINING} 模糊匹配（忽略大小写），
     * status 枚举精确匹配，未设置的字段自动忽略；软删行由 {@code @SQLRestriction} 全局过滤。
     * 排序仅按显式传入的 sortField 生成（实体属性名，如 sort/createTime），
     * 未指定时为数据库自然顺序。
     *
     * @param query 业务过滤条件（字段格式校验已由 Bean Validation 完成）
     * @param page  分页与排序参数（构造期已归一化，此处仅校验排序字段安全性）
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<SysRole> page(SysRoleQuery query, PageQuery page) {
        page.validate();
        SysRole probe = new SysRole();
        probe.setCode(query.getCode());
        probe.setName(query.getName());
        probe.setEnabled(query.getEnabled());
        ExampleMatcher matcher = ExampleMatcher.matching()
                                               .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
                                               .withIgnoreCase()
                                               .withIgnorePaths("id", "sort", "createBy", "updateBy",
                                                       "createTime", "updateTime", "deleted");
        PageRequest pageRequest = page.toPageRequest();
        return roleRepository.findAll(Example.of(probe, matcher), pageRequest);
    }
}
