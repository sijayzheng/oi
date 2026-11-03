package cn.sijay.oi.gen.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 保存代码生成表配置入参（record 不可变对象），承载表级配置与全量字段配置，编辑页一次性提交。
 *
 * <p>字段格式校验由 Bean Validation 完成；字段配置全量覆盖保存（先删后插），
 * 未提交的列将被删除，前端编辑页始终提交完整列清单。
 *
 * @param tableName    业务表名；如 sys_notice（保存时不可修改，仅导入时确定）
 * @param tableComment 业务表注释
 * @param className    实体类名主干（不含 Sys 前缀与模型后缀）；如 Notice
 * @param moduleName   业务模块名；如 sys
 * @param businessName 业务名；如 notice
 * @param functionName 功能名；如"通知公告"
 * @param author       生成代码署名
 * @param columns      全量字段配置（按 columnSort 升序提交）
 * @author sijay
 */
public record GenTableSaveCmd(
        String tableName,
        @NotBlank(message = "实体类名不能为空") String className,
        @NotBlank(message = "业务模块名不能为空") String moduleName,
        @NotBlank(message = "业务名不能为空") String businessName,
        @NotBlank(message = "功能名不能为空") String functionName,
        String author,
        List<ColumnCmd> columns) {

    /**
     * 字段配置入参（record 不可变对象），对应 gen_table_column 的可编辑列。
     *
     * @param id            字段配置 id；已有列必传，新增列不传
     * @param columnName    列名
     * @param columnComment 列注释；生成字段注释与表单标签
     * @param columnSort    列序号
     * @param javaField     Java/TS 驼峰字段名
     * @param javaType      Java 类型；String/Long/Integer/BigDecimal/Boolean/LocalDateTime
     * @param tsType        TS 类型；string/number/boolean
     * @param isPk          是否主键
     * @param isRequired    是否必填
     * @param isInsert      新增表单是否显示
     * @param isEdit        编辑表单是否显示
     * @param isList        列表是否展示
     * @param isQuery       是否查询条件
     * @param queryType     查询匹配方式；EQ/LIKE
     * @param htmlType      表单组件；input/textarea/select/radio/radio-button/switch/number/datetime
     * @param optionsJson   select/radio 静态选项 JSON
     * @param dictType      select/radio 字典编码
     * @param minLength     字符串最小长度 / 数值最小值
     * @param maxLength     字符串最大长度
     * @param regex         校验正则
     */
    public record ColumnCmd(
            Long id,
            @NotBlank(message = "列名不能为空") String columnName,
            String columnComment,
            Integer columnSort,
            @NotBlank(message = "Java 字段名不能为空") String javaField,
            @NotBlank(message = "Java 类型不能为空") String javaType,
            @NotBlank(message = "TS 类型不能为空") String tsType,
            @NotNull(message = "是否主键不能为空") Boolean isPk,
            Boolean isRequired,
            Boolean isInsert,
            Boolean isEdit,
            Boolean isList,
            Boolean isQuery,
            String queryType,
            String htmlType,
            String optionsJson,
            String dictType,
            Integer minLength,
            Integer maxLength,
            String regex) {
    }
}
 