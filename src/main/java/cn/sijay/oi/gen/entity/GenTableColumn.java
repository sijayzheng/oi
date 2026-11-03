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
 * 代码生成字段配置实体，映射表 {@code gen_table_column}，业务表的每个参与生成的字段对应一行。
 *
 * <p>导入表结构时按列类型自动推导默认配置（类型映射/必填/表单组件/查询方式），
 * 后续可通过编辑接口人工微调；渲染引擎按本配置驱动校验注解、表单组件与列表展示的生成。
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "gen_table_column")
public class GenTableColumn {

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属表配置 gen_table.id。
     */
    @Column(name = "table_id", nullable = false)
    private Long tableId;

    /**
     * 列名；如 notice_type。
     */
    @Column(name = "column_name", nullable = false, length = 100)
    private String columnName;

    /**
     * 列注释；生成字段注释与表单标签。
     */
    @Column(name = "column_comment", length = 200)
    private String columnComment;

    /**
     * 列序号；决定生成代码中的字段顺序。
     */
    @Column(name = "column_sort", nullable = false)
    private Integer columnSort = 0;

    /**
     * Java/TS 驼峰字段名；如 noticeType。
     */
    @Column(name = "java_field", nullable = false, length = 100)
    private String javaField;

    /**
     * Java 类型；String/Long/Integer/BigDecimal/Boolean/LocalDateTime。
     */
    @Column(name = "java_type", nullable = false, length = 30)
    private String javaType;

    /**
     * TS 类型；string/number/boolean。
     */
    @Column(name = "ts_type", nullable = false, length = 20)
    private String tsType;

    /**
     * 是否主键；1=是。
     */
    @Column(name = "is_pk", nullable = false)
    private Boolean isPk = false;

    /**
     * 是否必填；1=生成 @NotNull/@NotBlank 与前端 required 规则。
     */
    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = false;

    /**
     * 新增表单是否显示；1=显示。
     */
    @Column(name = "is_insert", nullable = false)
    private Boolean isInsert = false;

    /**
     * 编辑表单是否显示；1=显示。
     */
    @Column(name = "is_edit", nullable = false)
    private Boolean isEdit = false;

    /**
     * 列表是否展示该列；1=展示。
     */
    @Column(name = "is_list", nullable = false)
    private Boolean isList = false;

    /**
     * 是否作为查询条件；1=生成到 Query 模型与搜索表单。
     */
    @Column(name = "is_query", nullable = false)
    private Boolean isQuery = false;

    /**
     * 查询匹配方式；EQ=精确 / LIKE=CONTAINING 模糊。
     */
    @Column(name = "query_type", nullable = false, length = 10)
    private String queryType = "EQ";

    /**
     * 表单组件；input/textarea/select/radio/radio-button/switch/number。
     */
    @Column(name = "html_type", nullable = false, length = 20)
    private String htmlType = "input";

    /**
     * select/radio 静态选项 JSON；如 [{"value":"0","label":"通知"}]；配置 dictType 时优先走字典接口。
     */
    @Column(name = "options_json", length = 1000)
    private String optionsJson;

    /**
     * select/radio 字典编码；配置后前端下拉走 /dict/item/options/{dictType}。
     */
    @Column(name = "dict_type", length = 50)
    private String dictType;

    /**
     * 字符串最小长度 / 数值最小值；生成 @Size(min)/@Min。
     */
    @Column(name = "min_length")
    private Integer minLength;

    /**
     * 字符串最大长度；字符串生成 @Size(max)。
     */
    @Column(name = "max_length")
    private Integer maxLength;

    /**
     * 校验正则；生成 @Pattern 与前端 pattern 规则。
     */
    @Column(length = 500)
    private String regex;

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
