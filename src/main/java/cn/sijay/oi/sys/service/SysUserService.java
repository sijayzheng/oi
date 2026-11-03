package cn.sijay.oi.sys.service;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.sys.entity.SysUser;
import cn.sijay.oi.sys.model.user.SysUserCreateCmd;
import cn.sijay.oi.sys.model.user.SysUserQuery;
import cn.sijay.oi.sys.model.user.SysUserUpdateCmd;
import cn.sijay.oi.sys.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户业务层，提供用户模块的增删改查与业务校验。
 *
 * <p>分层约定：参数格式校验（非空/长度/格式）由入参模型的 Bean Validation 完成；
 * 本层负责<b>业务合法性校验</b>（用户名唯一、用户存在性），失败抛出
 * {@link BusinessException}（标准错误码），由全局异常处理器统一包装为 {@code Result}。
 *
 * <p>事务约定：写操作 {@code @Transactional}，读操作 {@code @Transactional(readOnly = true)}
 * （驱动层只读优化，且语义上声明无写意图）。
 *
 * @author sijay
 */
@Service
@RequiredArgsConstructor
public class SysUserService {

    /**
     * 系统操作人 id；0=系统初始化（DDL 约定），接入 sa-token 后替换为当前登录人 id。
     */
    private static final long SYSTEM_OPERATOR_ID = 0L;

    /**
     * 超级管理员固定 id（与 sql/data.sql 种子数据、AuthService 约定一致）。
     */
    private static final long ADMIN_USER_ID = 1L;

    private final SysUserRepository userRepository;

    /**
     * 密码编码器（BCrypt）；线程安全，实例级复用。
     * salt 随机生成，同一明文每次密文不同，校验走 matches 而非密文比对。
     */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 创建用户。
     *
     * <p>业务校验：用户名唯一（查询层自动过滤软删行，软删后的同名用户可重建，
     * 与唯一键 uk_user_username 语义一致）。密码 BCrypt 加密后落库，明文不存储。
     *
     * @param cmd 创建入参（格式校验已由 Bean Validation 完成）
     * @return 已落库的用户实体（含自增 id）
     * @throws BusinessException 用户名已存在（错误码 601001）
     */
    @Transactional
    public SysUser create(SysUserCreateCmd cmd) {
        if (userRepository.existsByUsername(cmd.getUsername())) {
            throw new BusinessException(BusinessErrorCode.USER_USERNAME_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(cmd.getUsername());
        user.setPassword(passwordEncoder.encode(cmd.getPassword()));
        user.setNickname(cmd.getNickname() == null ? "" : cmd.getNickname());
        user.setMobile(cmd.getMobile());
        user.setEmail(cmd.getEmail());
        user.setEnabled(cmd.getEnabled());
        user.setRemark(cmd.getRemark());
        user.setCreateBy(SYSTEM_OPERATOR_ID);
        return userRepository.save(user);
    }

    /**
     * 按 id 查询用户详情。
     *
     * @param id 用户 id
     * @return 用户实体
     * @throws BusinessException 用户不存在或已软删（错误码 601002）
     */
    @Transactional(readOnly = true)
    public SysUser getById(Long id) {
        return userRepository.findById(id)
                             .orElseThrow(() -> new BusinessException(BusinessErrorCode.USER_NOT_FOUND));
    }

    /**
     * 按 id 更新用户档案（username/password 不可通过本方法修改，见 SysUserUpdateCmd 约定）。
     *
     * <p>实现说明：依托 JPA 持久化上下文脏检查，事务提交时自动生成 UPDATE，
     * 无需显式 save；updateTime 由 Hibernate 时间戳注解自动刷新。
     *
     * @param id  用户 id
     * @param cmd 更新入参（格式校验已由 Bean Validation 完成）
     * @return 更新后的用户实体
     * @throws BusinessException 用户不存在或已软删（错误码 601002）
     */
    @Transactional
    public SysUser update(Long id, SysUserUpdateCmd cmd) {
        SysUser user = getById(id);
        user.setNickname(cmd.getNickname());
        user.setMobile(cmd.getMobile());
        user.setEmail(cmd.getEmail());
        user.setEnabled(cmd.getEnabled());
        user.setRemark(cmd.getRemark());
        user.setUpdateBy(SYSTEM_OPERATOR_ID);
        return user;
    }

    /**
     * 按 id 软删除用户（deleted 置为本行主键 id，物理数据保留供审计）。
     *
     * @param id 用户 id
     * @throws BusinessException 目标为超管（错误码 601006）或用户不存在/已软删（错误码 601002）
     */
    @Transactional
    public void delete(Long id) {
        if (id == ADMIN_USER_ID) {
            throw new BusinessException(BusinessErrorCode.USER_ADMIN_PROTECTED);
        }
        if (userRepository.softDelete(id) == 0) {
            throw new BusinessException(BusinessErrorCode.USER_NOT_FOUND);
        }
    }

    /**
     * 分页条件查询用户列表。
     *
     * <p>实现说明：Query-by-Example——以过滤参数构建探针实体，
     * username/nickname/mobile/email 走 {@code CONTAINING} 模糊匹配（忽略大小写），
     * status 枚举精确匹配，未设置的字段自动忽略；软删行由 {@code @SQLRestriction} 全局过滤。
     * 排序仅按显式传入的 sortField 生成（实体属性名，如 createTime），未指定时为数据库自然顺序。
     *
     * @param query 业务过滤条件（字段格式校验已由 Bean Validation 完成）
     * @param page  分页与排序参数（构造期已归一化，此处仅校验排序字段安全性）
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<SysUser> page(SysUserQuery query, PageQuery page) {
        page.validate();
        SysUser probe = new SysUser();
        probe.setUsername(normalizeBlank(query.getUsername()));
        probe.setNickname(normalizeBlank(query.getNickname()));
        probe.setMobile(normalizeBlank(query.getMobile()));
        probe.setEmail(normalizeBlank(query.getEmail()));
        probe.setEnabled(query.getEnabled());
        ExampleMatcher matcher = ExampleMatcher.matching()
                                               .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
                                               .withIgnoreCase()
                                               .withIgnorePaths("id", "password", "createBy", "updateBy",
                                                       "createTime", "updateTime", "deleted");
        PageRequest pageRequest = page.toPageRequest();
        return userRepository.findAll(Example.of(probe, matcher), pageRequest);
    }

    /**
     * 空字符串过滤条件归一化为 null。
     *
     * <p>必要性：前端搜索表单会把未填写的字段以空字符串提交（{@code username=}），
     * 空串在 {@code CONTAINING} 匹配下仍参与条件（{@code LIKE '%%'}），
     * 对应列为 NULL 的行（如未填手机/邮箱的用户）会被误过滤掉；置 null 后由
     * Example 机制自动忽略该字段，语义为"未设置的条件不参与匹配"。
     *
     * @param value 原始过滤条件
     * @return 非空白字符串原样返回；null 或空白返回 null
     */
    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
