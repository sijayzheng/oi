package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysDictItem;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 字典项数据访问层，映射表 {@code sys_dict_item}。
 *
 * <p>查询约定：实体标注 {@code @SQLRestriction("deleted = 0")}，本接口所有查询方法
 * 自动过滤软删行，无需在方法名或 JPQL 中重复条件；删除统一走 {@link #softDelete(Long)}。
 *
 * <p>分页条件查询使用 Query-by-Example（{@code findAll(Example, Pageable)}），
 * 由 Service 层装配 {@code ExampleMatcher}（StringMatcher.CONTAINING 模糊匹配）。
 *
 * @author sijay
 */
public interface SysDictItemRepository extends JpaRepository<SysDictItem, Long> {

    /**
     * 按字典编码查询全部有效项（自动过滤软删行）。
     *
     * <p>使用场景：下拉框数据源——按字典编码取全部项后按 sort 升序返回。
     *
     * @param dictType 字典编码
     * @return 该字典下的全部有效项（调用方负责排序）
     */
    List<SysDictItem> findByDictType(@NonNull String dictType);

    /**
     * 判断同字典内项编码是否已存在（自动过滤软删行）。
     *
     * <p>使用场景：创建字典项时的业务合法性校验，配合唯一键 uk_dict_item 双保险。
     *
     * @param dictType 字典编码
     * @param itemCode 项编码
     * @return 已存在返回 true
     */
    boolean existsByDictTypeAndItemCode(@NonNull String dictType, @NonNull String itemCode);

    /**
     * 软删除单个字典项：将 deleted 置为本行主键 id。
     *
     * <p>注意：JPQL 批量更新不走 @SQLRestriction，故条件中显式携带 deleted = 0，
     * 防止对已软删行重复执行。
     *
     * @param id 字典项 id
     * @return 影响行数；0 表示字典项不存在或已软删
     */
    @Modifying
    @Query("update SysDictItem i set i.deleted = i.id where i.id = :id and i.deleted = 0")
    int softDelete(@NonNull Long id);

    /**
     * 级联软删除：将指定字典下全部有效项的 deleted 置为各自主键 id。
     *
     * <p>使用场景：删除字典类型时级联软删其全部字典项（保持两表一致性）；
     * 软删后同 (dict_type, item_code) 可重建。
     *
     * @param dictType 字典编码
     * @return 影响行数
     */
    @Modifying
    @Query("update SysDictItem i set i.deleted = i.id where i.dictType = :dictType and i.deleted = 0")
    int cascadeSoftDeleteByDictType(@NonNull String dictType);
}
