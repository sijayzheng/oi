package cn.sijay.oi.sys.service;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.sys.entity.SysConfig;
import cn.sijay.oi.sys.model.config.SysConfigCreateCmd;
import cn.sijay.oi.sys.model.config.SysConfigQuery;
import cn.sijay.oi.sys.model.config.SysConfigUpdateCmd;
import cn.sijay.oi.sys.repository.SysConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 参数配置业务层，提供配置模块的增删改查与业务校验。
 *
 * <p>分层约定：参数格式校验（非空/长度/格式）由入参模型的 Bean Validation 完成；
 * 本层负责<b>业务合法性校验</b>（参数键唯一、配置存在性），失败抛出
 * {@link BusinessException}（标准错误码），由全局异常处理器统一包装为 {@code Result}。
 *
 * <p>事务约定：写操作 {@code @Transactional}，读操作 {@code @Transactional(readOnly = true)}。
 *
 * @author sijay
 */
@Service
@RequiredArgsConstructor
public class SysConfigService {

    /**
     * 系统操作人 id；0=系统初始化（DDL 约定），接入 sa-token 后替换为当前登录人 id。
     */
    private static final long SYSTEM_OPERATOR_ID = 0L;

    private final SysConfigRepository configRepository;

    /**
     * 创建配置。
     *
     * <p>业务校验：参数键唯一（查询层自动过滤软删行，软删后的同键配置可重建，
     * 与唯一键 uk_config_key 语义一致）。
     *
     * @param cmd 创建入参（格式校验已由 Bean Validation 完成）
     * @return 已落库的配置实体（含自增 id）
     * @throws BusinessException 参数键已存在（错误码 604001）
     */
    @Transactional
    public SysConfig create(SysConfigCreateCmd cmd) {
        if (configRepository.existsByConfigKey(cmd.getConfigKey())) {
            throw new BusinessException(BusinessErrorCode.CONFIG_KEY_EXISTS);
        }
        SysConfig config = new SysConfig();
        config.setConfigKey(cmd.getConfigKey());
        config.setConfigValue(cmd.getConfigValue());
        config.setValueType(cmd.getValueType());
        config.setName(cmd.getName());
        config.setRemark(cmd.getRemark());
        config.setCreateBy(SYSTEM_OPERATOR_ID);
        return configRepository.save(config);
    }

    /**
     * 按 id 查询配置详情。
     *
     * @param id 配置 id
     * @return 配置实体
     * @throws BusinessException 配置不存在或已软删（错误码 604002）
     */
    @Transactional(readOnly = true)
    public SysConfig getById(Long id) {
        return configRepository.findById(id)
                               .orElseThrow(() -> new BusinessException(BusinessErrorCode.CONFIG_NOT_FOUND));
    }

    /**
     * 按参数键读取配置（运行时取值入口）。
     *
     * <p>使用场景：业务代码按 key 读取运行时可变配置（如 site.name）；
     * 返回统一字符串形态的 configValue，调用方按 valueType 自行解析。
     *
     * @param configKey 参数键
     * @return 配置实体
     * @throws BusinessException 配置不存在或已软删（错误码 604002）
     */
    @Transactional(readOnly = true)
    public SysConfig getByKey(String configKey) {
        return configRepository.findByConfigKey(configKey)
                               .orElseThrow(() -> new BusinessException(BusinessErrorCode.CONFIG_NOT_FOUND));
    }

    /**
     * 按 id 更新配置（configKey 不可通过本方法修改，见 SysConfigUpdateCmd 约定）。
     *
     * <p>实现说明：依托 JPA 持久化上下文脏检查，事务提交时自动生成 UPDATE，
     * 无需显式 save；updateTime 由 Hibernate 时间戳注解自动刷新。
     *
     * @param id  配置 id
     * @param cmd 更新入参（格式校验已由 Bean Validation 完成）
     * @return 更新后的配置实体
     * @throws BusinessException 配置不存在或已软删（错误码 604002）
     */
    @Transactional
    public SysConfig update(Long id, SysConfigUpdateCmd cmd) {
        SysConfig config = getById(id);
        config.setConfigValue(cmd.getConfigValue());
        config.setValueType(cmd.getValueType());
        config.setName(cmd.getName());
        config.setRemark(cmd.getRemark());
        config.setUpdateBy(SYSTEM_OPERATOR_ID);
        return config;
    }

    /**
     * 按 id 软删除配置（deleted 置为本行主键 id，物理数据保留供审计）。
     *
     * @param id 配置 id
     * @throws BusinessException 配置不存在或已软删（错误码 604002）
     */
    @Transactional
    public void delete(Long id) {
        if (configRepository.softDelete(id) == 0) {
            throw new BusinessException(BusinessErrorCode.CONFIG_NOT_FOUND);
        }
    }

    /**
     * 分页条件查询配置列表。
     *
     * <p>实现说明：Query-by-Example——以过滤参数构建探针实体，
     * configKey/name 走 {@code CONTAINING} 模糊匹配（忽略大小写），
     * valueType 枚举精确匹配，未设置的字段自动忽略；软删行由 {@code @SQLRestriction} 全局过滤。
     * 排序仅按显式传入的 sortField 生成（实体属性名，如 configKey/createTime），
     * 未指定时为数据库自然顺序。
     *
     * @param query 业务过滤条件（字段格式校验已由 Bean Validation 完成）
     * @param page  分页与排序参数（构造期已归一化，此处仅校验排序字段安全性）
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<SysConfig> page(SysConfigQuery query, PageQuery page) {
        page.validate();
        SysConfig probe = new SysConfig();
        probe.setConfigKey(query.getConfigKey());
        probe.setName(query.getName());
        probe.setValueType(query.getValueType());
        ExampleMatcher matcher = ExampleMatcher.matching()
                                               .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
                                               .withIgnoreCase()
                                               .withIgnorePaths("id", "createBy", "updateBy",
                                                       "createTime", "updateTime", "deleted");
        PageRequest pageRequest = page.toPageRequest();
        return configRepository.findAll(Example.of(probe, matcher), pageRequest);
    }
}
