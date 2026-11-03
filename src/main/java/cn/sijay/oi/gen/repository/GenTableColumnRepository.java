package cn.sijay.oi.gen.repository;

import cn.sijay.oi.gen.entity.GenTableColumn;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 代码生成字段配置数据访问层，映射表 {@code gen_table_column}。
 *
 * <p>元数据配置无软删语义，物理删除；列按 {@code columnSort} 升序返回，与数据库定义顺序一致。
 *
 * @author sijay
 */
public interface GenTableColumnRepository extends JpaRepository<GenTableColumn, Long> {

    /**
     * 按表配置 id 查询全部字段配置（列序号升序）。
     *
     * @param tableId 表配置 id
     * @return 字段配置列表
     */
    List<GenTableColumn> findByTableIdOrderByColumnSortAsc(@NonNull Long tableId);

    /**
     * 删除指定表配置下的全部字段配置（重新导入/删除表配置时联动清理）。
     *
     * @param tableId 表配置 id
     * @return 影响行数
     */
    @Modifying
    @Query("delete from GenTableColumn c where c.tableId = :tableId")
    int deleteByTableId(@Param("tableId") @NonNull Long tableId);
}
