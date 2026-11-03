package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysDictType;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * 字典类型数据访问层，映射表 {@code sys_dict_type}。
 *
 * <p>查询约定：实体标注 {@code @SQLRestriction("deleted = 0")}，本接口所有查询方法
 * 自动过滤软删行，无需在方法名或 JPQL 中重复条件；删除统一走 {@link #softDelete(Long)}。
 *
 * <p>分页条件查询使用 Query-by-Example（{@code findAll(Example, Pageable)}），
 * 由 Service 层装配 {@code ExampleMatcher}（StringMatcher.CONTAINING 模糊匹配）。
 *
 * @author sijay
 */
public interface SysDictTypeRepository extends JpaRepository<SysDictType, Long> {

    /**
     * 判断字典编码是否已存在（自动过滤软删行；软删后的同编码字典不算存在）。
     *
     * <p>使用场景：创建字典类型时的业务合法性校验，配合唯一键 uk_dict_type_code 双保险。
     *
     * @param dictType 字典编码
     * @return 已存在返回 true
     */
    boolean existsByDictType(@NonNull String dictType);

    /**
     * 按字典编码查询字典类型（自动过滤软删行）。
     *
     * <p>使用场景：字典项联动校验（创建项时确认所属类型存在）与按编码读取字典。
     *
     * @param dictType 字典编码
     * @return 命中的字典类型；不存在或已软删返回 {@link Optional#empty()}
     */
    Optional<SysDictType> findByDictType(@NonNull String dictType);

    /**
     * 软删除：将 deleted 置为本行主键 id（与 DDL 约定一致，软删后原字典编码可重建）。
     *
     * <p>使用场景：删除字典类型的入口；字典项的级联软删由 Service 层配合
     * SysDictItemRepository 完成。注意：JPQL 批量更新不走 @SQLRestriction，
     * 故条件中显式携带 deleted = 0，防止对已软删行重复执行。
     *
     * @param id 字典类型 id
     * @return 影响行数；0 表示字典类型不存在或已软删
     */
    @Modifying
    @Query("update SysDictType t set t.deleted = t.id, t.updateTime = CURRENT_TIMESTAMP "
            + "where t.id = :id and t.deleted = 0")
    int softDelete(@NonNull Long id);
}
