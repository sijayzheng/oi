package cn.sijay.oi.gen.repository;

import cn.sijay.oi.gen.entity.GenTable;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 代码生成表配置数据访问层，映射表 {@code gen_table}。
 *
 * <p>元数据配置无软删语义，物理删除；分页查询使用 {@code findAll(Pageable)}。
 *
 * @author sijay
 */
public interface GenTableRepository extends JpaRepository<GenTable, Long> {

    /**
     * 按业务表名查询配置（导入查重用；同一表仅允许导入一次）。
     *
     * @param tableName 业务表名
     * @return 命中的表配置
     */
    Optional<GenTable> findByTableName(@NonNull String tableName);

    /**
     * 判断业务表名是否已导入。
     *
     * @param tableName 业务表名
     * @return 已导入返回 true
     */
    boolean existsByTableName(@NonNull String tableName);

    /**
     * 判断表名是否存在（主子表关联校验用）。
     *
     * @param id 表配置 id
     * @return 存在返回 true
     */
    boolean existsById(@NonNull Long id);
}
