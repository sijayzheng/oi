package cn.sijay.oi.${moduleName}.repository;

import cn.sijay.oi.${moduleName}.entity.${entityName};
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * ${functionName}数据访问层，映射表 {@code ${tableName}}。
 *
 * <p>查询约定：实体标注 {@code @SQLRestriction("deleted = 0")}，本接口所有查询方法
 * 自动过滤软删行，无需在方法名或 JPQL 中重复条件；删除统一走 {@link #softDelete(Long)}。
 *
 * <p>分页条件查询使用 Query-by-Example（{@code findAll(Example, Pageable)}），
 * 由 Service 层装配 {@code ExampleMatcher}（StringMatcher.CONTAINING 模糊匹配）。
 *
 * @author ${author}
 */
public interface ${entityName}Repository extends JpaRepository<${entityName}, Long> {

    /**
     * 软删除：将 deleted 置为本行主键 id（与 DDL 约定一致，物理数据保留供审计）。
     *
     * <p>使用场景：删除${functionName}的唯一入口；不使用物理 DELETE，保留审计痕迹。
     * 注意：JPQL 批量更新不走 @SQLRestriction，故条件中显式携带 deleted = 0，
     * 防止对已软删行重复执行。
     *
     * @param id ${functionName} id
     * @return 影响行数；0 表示记录不存在或已软删
     */
    @Modifying
    @Query("update ${entityName} e set e.deleted = e.id, e.updateTime = CURRENT_TIMESTAMP where e.id = :id and e.deleted = 0")
    int softDelete(@NonNull Long id);
<#if isChildTable>

    /**
     * 级联软删：按外键将关联的${functionName}全部软删（主表删除时同事务调用，保证主子表一致性）。
     *
     * <p>注意：JPQL 批量更新不走 @SQLRestriction，故条件中显式携带 deleted = 0，
     * 防止对已软删行重复执行。
     *
     * @param parentId 主表 id
     * @return 影响行数
     */
    @Modifying
    @Query("update ${entityName} e set e.deleted = e.id, e.updateTime = CURRENT_TIMESTAMP where e.${fkJavaField} = :parentId and e.deleted = 0")
    int cascadeSoftDeleteBy${fkJavaField?cap_first}(@Param("parentId") @NonNull Long parentId);
</#if>}
