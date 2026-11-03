package cn.sijay.oi.${moduleName}.service;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.common.model.PageQuery;
<#if masterDetail && child??>
import cn.sijay.oi.${child.moduleName}.repository.Sys${child.className}Repository;
</#if>
import cn.sijay.oi.${moduleName}.entity.${entityName};
import cn.sijay.oi.${moduleName}.model.${businessName}.Sys${className}CreateCmd;
import cn.sijay.oi.${moduleName}.model.${businessName}.Sys${className}Query;
import cn.sijay.oi.${moduleName}.model.${businessName}.Sys${className}UpdateCmd;
import cn.sijay.oi.${moduleName}.repository.${entityName}Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ${functionName}业务层，提供${functionName}模块的增删改查与业务校验。
 *
 * <p>分层约定：参数格式校验（非空/长度/格式）由入参模型的 Bean Validation 完成；
 * 本层负责<b>业务合法性校验</b>与落库编排，失败抛出 {@link BusinessException}
 * （标准错误码），由全局异常处理器统一包装为 {@code Result}。
 *
 * <p>事务约定：写操作 {@code @Transactional}，读操作 {@code @Transactional(readOnly = true)}。
 *
 * @author ${author}
 */
@Service
@RequiredArgsConstructor
public class Sys${className}Service {

    private final ${entityName}Repository ${businessName}Repository;
<#if masterDetail && child??>

    /**
     * 子表数据访问层：主表删除时级联软删${child.functionName}，保证主子表数据一致性。
     */
    private final Sys${child.className}Repository ${child.businessName?replace('_', '')}Repository;
</#if>

    /**
     * 创建${functionName}。
     *
     * @param cmd 创建入参（格式校验已由 Bean Validation 完成）
     * @return 已落库的${functionName}实体（含自增 id）
     */
    @Transactional
    public ${entityName} create(Sys${className}CreateCmd cmd) {
        ${entityName} entity = new ${entityName}();
<#list insertColumns as col>
        entity.set${col.javaField?cap_first}(cmd.get${col.javaField?cap_first}());
</#list>
        return ${businessName}Repository.save(entity);
    }

    /**
     * 按 id 查询${functionName}详情。
     *
     * @param id ${functionName} id
     * @return ${functionName}实体
     * @throws BusinessException ${functionName}不存在或已软删（错误码 600002）
     */
    @Transactional(readOnly = true)
    public ${entityName} getById(Long id) {
        return ${businessName}Repository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.COMMON_DATA_NOT_FOUND));
    }

    /**
     * 按 id 更新${functionName}档案。
     *
     * <p>实现说明：依托 JPA 持久化上下文脏检查，事务提交时自动生成 UPDATE，
     * 无需显式 save；updateTime 由 Hibernate 时间戳注解自动刷新。
     *
     * @param id  ${functionName} id
     * @param cmd 更新入参（格式校验已由 Bean Validation 完成）
     * @return 更新后的${functionName}实体
     * @throws BusinessException ${functionName}不存在或已软删（错误码 600002）
     */
    @Transactional
    public ${entityName} update(Long id, Sys${className}UpdateCmd cmd) {
        ${entityName} entity = getById(id);
<#list editColumns as col>
        entity.set${col.javaField?cap_first}(cmd.get${col.javaField?cap_first}());
</#list>
        return entity;
    }

    /**
     * 按 id 软删除${functionName}（deleted 置为本行主键 id，物理数据保留供审计）。
     *<#if masterDetail && child??>
     *
     * <p>级联约定：同事务内级联软删其关联的${child.functionName}，防止子表孤儿数据。
     *</#if>
     *
     * @param id ${functionName} id
     * @throws BusinessException ${functionName}不存在或已软删（错误码 600002）
     */
    @Transactional
    public void delete(Long id) {
        if (${businessName}Repository.softDelete(id) == 0) {
            throw new BusinessException(BusinessErrorCode.COMMON_DATA_NOT_FOUND);
        }
<#if masterDetail && child??>
        ${child.businessName?replace('_', '')}Repository.cascadeSoftDeleteBy${child.fkJavaField?cap_first}(id);
</#if>
    }

    /**
     * 分页条件查询${functionName}列表。
     *
     * <p>实现说明：Query-by-Example——以过滤参数构建探针实体，
     * 未设置的字段自动忽略；软删行由 {@code @SQLRestriction} 全局过滤。
     * 排序仅按显式传入的 sortField 生成（实体属性名），未指定时为数据库自然顺序。
     *
     * @param query 业务过滤条件（字段格式校验已由 Bean Validation 完成）
     * @param page  分页与排序参数（构造期已归一化，此处仅校验排序字段安全性）
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<${entityName}> page(Sys${className}Query query, PageQuery page) {
        page.validate();
        ${entityName} probe = new ${entityName}();
<#list queryColumns as col>
        probe.set${col.javaField?cap_first}(query.get${col.javaField?cap_first}());
</#list>
        ExampleMatcher matcher = ExampleMatcher.matching()
                .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
                .withIgnoreCase()
                .withIgnorePaths("id", "deleted", "createBy", "updateBy", "createTime", "updateTime"<#list columns as col><#if !col.pk && !col.systemHidden && !col.auditTime && !col.query>, "${col.javaField}"</#if></#list>);
        PageRequest pageRequest = page.toPageRequest();
        return ${businessName}Repository.findAll(Example.of(probe, matcher), pageRequest);
    }
}
