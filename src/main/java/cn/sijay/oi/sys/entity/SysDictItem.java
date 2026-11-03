package cn.sijay.oi.sys.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 字典项实体，映射表 {@code sys_dict_item}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>关键约定（与 DDL 注释对齐）：
 * <ul>
 *   <li><b>无审计字段</b>：DDL 未定义 create_by/create_time 等列，本实体亦不声明；</li>
 *   <li><b>软删除</b>：{@code deleted} 默认 0，删除时写入本行主键 id；
 *       {@link SQLRestriction} 使本实体的所有查询自动携带 {@code deleted = 0} 条件；</li>
 *   <li><b>唯一约束</b>：数据库层 {@code UNIQUE (dict_type, item_code, deleted)}；</li>
 *   <li><b>dict_type 冗余</b>：冗余存储所属字典编码（查询免 join），
 *       字典类型编码不可变，两者始终同步；</li>
 *   <li><b>禁用语义</b>：status=DISABLED 的项不再出现在下拉选项，但历史数据仍可回显。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_dict_item")
@SQLRestriction("deleted = 0")
public class SysDictItem {

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属字典编码；冗余 sys_dict_type.dict_type（查询免 join），两者须保持同步。
     */
    @Column(name = "dict_type", nullable = false, length = 50)
    private String dictType;

    /**
     * 项编码；业务实际存储值，与 dict_type、deleted 组合唯一。
     */
    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;

    /**
     * 项显示名；前端展示的文案（如 NORMAL →"正常"）。
     */
    @Column(name = "item_label", nullable = false, length = 100)
    private String itemLabel;

    /**
     * 项排序号；同字典内越小越靠前，默认 0。
     */
    @Column(nullable = false)
    private Integer sort = 0;

    /**
     * 项状态；NORMAL=正常，DISABLED=禁用（不再出现在下拉选项，历史数据仍可回显）。
     */
    @Column(nullable = false)
    private Boolean enabled;

    /**
     * 备注。
     */
    @Column(length = 255)
    private String remark;

    /**
     * 软删标记；0=未删除，删除时=本行主键 id；唯一键 uk_dict_item 包含本列。
     */
    @Column(nullable = false)
    private Long deleted = 0L;
}
