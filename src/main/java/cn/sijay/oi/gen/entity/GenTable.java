package cn.sijay.oi.gen.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 代码生成表配置实体，映射表 {@code gen_table}，一张待生成业务表对应一行。
 *
 * <p>与 {@link GenTableColumn} 为一对多关系（业务表 → 字段配置）；
 * 元数据配置不参与软删与审计人记录，删除即物理删除。
 *
 * <p>主子表场景：{@code parentTableId} 指向主表配置且 {@code fkColumn} 指向本表外键列，
 * 渲染引擎据此生成主表的级联软删逻辑与前端双栏联动页面。
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "gen_table")
public class GenTable {

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 业务表名；如 sys_notice；唯一（uk_gen_table_name），同一表仅允许导入一次。
     */
    @Column(name = "table_name", nullable = false, unique = true, length = 100)
    private String tableName;

    /**
     * 业务表注释；如"通知公告"。
     */
    @Column(name = "table_comment", length = 200)
    private String tableComment;

    /**
     * 实体类名主干（不含 Sys 前缀与模型后缀）；如 Notice → SysNotice / SysNoticeVO。
     */
    @Column(name = "class_name", nullable = false, length = 100)
    private String className;

    /**
     * 业务模块名；对应后端包名与前端路由段（如 sys）。
     */
    @Column(name = "module_name", nullable = false, length = 50)
    private String moduleName;

    /**
     * 业务名；对应接口路径段与前端文件名（如 notice → /notice）。
     */
    @Column(name = "business_name", nullable = false, length = 50)
    private String businessName;

    /**
     * 功能名；界面展示用（如"通知公告"）。
     */
    @Column(name = "function_name", nullable = false, length = 100)
    private String functionName;

    /**
     * 生成代码的 @author 署名；默认 sijay。
     */
    @Column(nullable = false, length = 50)
    private String author = "sijay";

    /**
     * 主子表场景：主表 gen_table.id；单表场景为 null。
     */
    @Column(name = "parent_table_id")
    private Long parentTableId;

    /**
     * 主子表场景：子表外键列名（关联主表主键）；如 dict_type。
     */
    @Column(name = "fk_column", length = 100)
    private String fkColumn;

    /**
     * 创建时间；审计字段。
     */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间；行被 UPDATE 时自动刷新。
     */
    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
