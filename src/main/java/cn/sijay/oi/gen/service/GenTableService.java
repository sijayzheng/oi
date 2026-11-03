package cn.sijay.oi.gen.service;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.gen.entity.GenTable;
import cn.sijay.oi.gen.entity.GenTableColumn;
import cn.sijay.oi.gen.model.GenTableSaveCmd;
import cn.sijay.oi.gen.repository.GenTableColumnRepository;
import cn.sijay.oi.gen.repository.GenTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 代码生成表配置业务层：负责从数据库导入表结构、推导字段默认配置、维护表/列配置的增删改查。
 *
 * <p>类型映射与默认值推导规则（导入时自动执行，可人工微调）：
 * <ul>
 *   <li>类型映射：BIGINT→Long/string、INT/TINYINT→Integer/number、DECIMAL→BigDecimal/number、
 *       BOOLEAN→Boolean/boolean、TIMESTAMP→LocalDateTime/string、其余字符类型→String/string；</li>
 *   <li>主键列：isPk=true，不参与表单/列表/查询，TS 类型按 Long→string 处理；</li>
 *   <li>必填：NOT NULL 且非主键；</li>
 *   <li>表单组件：数值→number、布尔→switch、时间→datetime、注释含"内容/描述/备注"→textarea、其余→input；</li>
 *   <li>查询方式：String→LIKE（CONTAINING 模糊），其余→EQ（精确）；</li>
 *   <li>列表/表单展示：非主键列默认展示，VARCHAR(2000) 级长文本默认不在列表展示。</li>
 * </ul>
 *
 * <p>事务约定：写操作 {@code @Transactional}，读操作 {@code @Transactional(readOnly = true)}。
 *
 * @author sijay
 */
@Service
@RequiredArgsConstructor
public class GenTableService {

    /**
     * 列类型 → Java/TS 类型映射表（key 大写化后匹配）。
     */
    private static final Map<String, String[]> TYPE_MAPPINGS = Map.ofEntries(
            Map.entry("BIGINT", new String[]{"Long", "string"}),
            Map.entry("INT", new String[]{"Integer", "number"}),
            Map.entry("INTEGER", new String[]{"Integer", "number"}),
            Map.entry("TINYINT", new String[]{"Integer", "number"}),
            Map.entry("SMALLINT", new String[]{"Integer", "number"}),
            Map.entry("DECIMAL", new String[]{"BigDecimal", "number"}),
            Map.entry("NUMERIC", new String[]{"BigDecimal", "number"}),
            Map.entry("BOOLEAN", new String[]{"Boolean", "boolean"}),
            Map.entry("TIMESTAMP", new String[]{"LocalDateTime", "string"}),
            Map.entry("DATETIME", new String[]{"LocalDateTime", "string"}),
            Map.entry("DATE", new String[]{"LocalDateTime", "string"}),
            Map.entry("CLOB", new String[]{"String", "string"}),
            Map.entry("TEXT", new String[]{"String", "string"}));

    private final GenTableRepository tableRepository;
    private final GenTableColumnRepository columnRepository;
    private final DataSource dataSource;

    /**
     * 查询数据库中可供导入的业务表清单（排除已导入的表与代码生成器自身的元数据表）。
     *
     * <p>实现说明：走 JDBC {@link DatabaseMetaData#getTables}（跨数据库标准 API，
     * 不依赖各库 information_schema 方言差异）；类型同时匹配标准 "TABLE" 与 H2 2.x
     * 返回的 "BASE TABLE"。返回表名与表注释，供前端导入对话框选择。
     *
     * @return 表名 + 表注释列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> listImportableTables() {
        try (Connection connection = dataSource.getConnection()) {
            ResultSet rs = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE", "BASE TABLE"});
            List<Map<String, String>> tables = new ArrayList<>();
            while (rs.next()) {
                // 仅保留当前会话 schema 的用户表（排除 INFORMATION_SCHEMA 系统表）
                String tableSchema = rs.getString("TABLE_SCHEM");
                if (tableSchema == null || !"public".equalsIgnoreCase(tableSchema)) {
                    continue;
                }
                String tableName = rs.getString("TABLE_NAME");
                if (tableName.startsWith("gen_") || tableRepository.existsByTableName(tableName.toLowerCase())) {
                    continue;
                }
                tables.add(Map.of(
                        "tableName", tableName.toLowerCase(),
                        "tableComment", rs.getString("REMARKS") == null ? "" : rs.getString("REMARKS")));
            }
            tables.sort(Comparator.comparing(t -> t.get("tableName")));
            return tables;
        }
        catch (SQLException e) {
            throw new BusinessException(BusinessErrorCode.COMMON_DATA_NOT_FOUND);
        }
    }

    /**
     * 读取业务表的注释；表不存在返回 null。
     */
    private String readTableComment(Connection connection, String tableName) throws SQLException {
        ResultSet rs = connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE", "BASE TABLE"});
        if (!rs.next()) {
            return null;
        }
        String remarks = rs.getString("REMARKS");
        return remarks == null ? "" : remarks;
    }

    /**
     * 导入业务表结构：读取列元数据并按推导规则生成字段默认配置。
     *
     * <p>业务校验：表必须存在（information_schema 中可查到）且未被导入过（uk_gen_table_name 前置校验）。
     *
     * @param tableName 业务表名
     * @return 已落库的表配置（含自增 id）
     * @throws BusinessException 表不存在（600002）或已导入（600003）
     */
    @Transactional
    public GenTable importTable(String tableName) {
        if (tableRepository.existsByTableName(tableName)) {
            throw new BusinessException(BusinessErrorCode.COMMON_DATA_DUPLICATED);
        }
        GenTable table;
        List<GenTableColumn> derivedColumns;
        try (Connection connection = dataSource.getConnection()) {
            String tableComment = readTableComment(connection, tableName);
            if (tableComment == null) {
                throw new BusinessException(BusinessErrorCode.COMMON_DATA_NOT_FOUND);
            }
            table = new GenTable();
            table.setTableName(tableName);
            table.setTableComment(tableComment);
            table.setClassName(toClassName(tableName));
            table.setModuleName("sys");
            table.setBusinessName(toBusinessName(tableName));
            table.setFunctionName(tableComment);
            derivedColumns = deriveColumns(connection, tableName);
        }
        catch (SQLException e) {
            throw new BusinessException(BusinessErrorCode.COMMON_DATA_NOT_FOUND);
        }
        GenTable saved = tableRepository.save(table);
        columnRepository.saveAll(derivedColumns.stream().peek(c -> c.setTableId(saved.getId())).toList());
        return saved;
    }

    /**
     * 分页查询表配置列表（元数据量小，无过滤条件）。
     *
     * @param page 分页与排序参数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<GenTable> page(PageQuery page) {
        page.validate();
        return tableRepository.findAll(page.toPageRequest());
    }

    /**
     * 按 id 查询表配置详情（含字段配置）。
     *
     * @param id 表配置 id
     * @return 表配置
     * @throws BusinessException 表配置不存在（600002）
     */
    @Transactional(readOnly = true)
    public GenTable getTableById(Long id) {
        return tableRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.COMMON_DATA_NOT_FOUND));
    }

    /**
     * 按 id 查询全部字段配置（列序号升序）。
     *
     * @param tableId 表配置 id
     * @return 字段配置列表
     */
    @Transactional(readOnly = true)
    public List<GenTableColumn> getColumnsByTableId(Long tableId) {
        return columnRepository.findByTableIdOrderByColumnSortAsc(tableId);
    }

    /**
     * 保存表/字段配置（编辑页全量提交；tableName 不可修改，字段配置先删后插全量覆盖）。
     *
     * @param id  表配置 id
     * @param cmd 保存入参
     * @return 更新后的表配置
     * @throws BusinessException 表配置不存在（600002）或列清单为空（600001）
     */
    @Transactional
    public GenTable save(Long id, GenTableSaveCmd cmd) {
        GenTable table = getTableById(id);
        table.setClassName(cmd.className());
        table.setModuleName(cmd.moduleName());
        table.setBusinessName(cmd.businessName());
        table.setFunctionName(cmd.functionName());
        if (cmd.author() != null && !cmd.author().isBlank()) {
            table.setAuthor(cmd.author());
        }
        if (cmd.columns() == null || cmd.columns().isEmpty()) {
            throw new BusinessException(BusinessErrorCode.COMMON_PARAM_ERROR);
        }
        columnRepository.deleteByTableId(id);
        for (GenTableSaveCmd.ColumnCmd columnCmd : cmd.columns()) {
            GenTableColumn column = new GenTableColumn();
            column.setTableId(id);
            column.setColumnName(columnCmd.columnName());
            column.setColumnComment(columnCmd.columnComment());
            column.setColumnSort(columnCmd.columnSort() == null ? 0 : columnCmd.columnSort());
            column.setJavaField(columnCmd.javaField());
            column.setJavaType(columnCmd.javaType());
            column.setTsType(columnCmd.tsType());
            column.setIsPk(Boolean.TRUE.equals(columnCmd.isPk()));
            column.setIsRequired(Boolean.TRUE.equals(columnCmd.isRequired()));
            column.setIsInsert(Boolean.TRUE.equals(columnCmd.isInsert()));
            column.setIsEdit(Boolean.TRUE.equals(columnCmd.isEdit()));
            column.setIsList(Boolean.TRUE.equals(columnCmd.isList()));
            column.setIsQuery(Boolean.TRUE.equals(columnCmd.isQuery()));
            column.setQueryType(Objects.requireNonNullElse(columnCmd.queryType(), "EQ"));
            column.setHtmlType(Objects.requireNonNullElse(columnCmd.htmlType(), "input"));
            column.setOptionsJson(columnCmd.optionsJson());
            column.setDictType(columnCmd.dictType());
            column.setMinLength(columnCmd.minLength());
            column.setMaxLength(columnCmd.maxLength());
            column.setRegex(columnCmd.regex());
            columnRepository.save(column);
        }
        return table;
    }

    /**
     * 按 id 删除表配置及其全部字段配置（物理删除）。
     *
     * @param id 表配置 id
     * @throws BusinessException 表配置不存在（600002）
     */
    @Transactional
    public void delete(Long id) {
        if (!tableRepository.existsById(id)) {
            throw new BusinessException(BusinessErrorCode.COMMON_DATA_NOT_FOUND);
        }
        columnRepository.deleteByTableId(id);
        tableRepository.deleteById(id);
    }

    /**
     * 由列元数据推导字段默认配置（导入时的自动化核心）。
     *
     * <p>推导规则见类级注释"类型映射与默认值推导规则"；
     * 列注释取 JDBC REMARKS 元数据（H2 COMMENT ON COLUMN 会写入该字段）。
     * 走 JDBC {@link DatabaseMetaData#getColumns}（跨数据库标准 API），
     * 列按 ORDINAL_POSITION 升序排列。
     *
     * @param connection 数据库连接（调用方持有并关闭）
     * @param tableName  业务表名
     * @return 字段配置列表（未落库，tableId 由调用方回填）
     */
    private List<GenTableColumn> deriveColumns(Connection connection, String tableName) throws SQLException {
        ResultSet rs = connection.getMetaData().getColumns(null, null, tableName, null);
        record RawColumn(String name, String comment, String dbType, int charLength,
                         boolean nullable, boolean hasDefault, int ordinal) {
        }
        List<RawColumn> rawColumns = new ArrayList<>();
        while (rs.next()) {
            rawColumns.add(new RawColumn(
                    rs.getString("COLUMN_NAME"),
                    rs.getString("REMARKS") == null ? "" : rs.getString("REMARKS"),
                    rs.getString("TYPE_NAME").toUpperCase(),
                    rs.getInt("COLUMN_SIZE"),
                    "YES".equals(rs.getString("IS_NULLABLE")),
                    rs.getString("COLUMN_DEF") != null,
                    rs.getInt("ORDINAL_POSITION")));
        }
        rawColumns.sort(Comparator.comparingInt(RawColumn::ordinal));

        List<GenTableColumn> columns = new ArrayList<>();
        for (int i = 0; i < rawColumns.size(); i++) {
            RawColumn raw = rawColumns.get(i);
            String columnName = raw.name();
            boolean isPk = "id".equalsIgnoreCase(columnName);
            boolean isAuditTime = "create_time".equalsIgnoreCase(columnName) || "update_time".equalsIgnoreCase(columnName);
            boolean isSystemHidden = "create_by".equalsIgnoreCase(columnName) || "update_by".equalsIgnoreCase(columnName)
                    || "deleted".equalsIgnoreCase(columnName);

            String[] types = TYPE_MAPPINGS.getOrDefault(raw.dbType(), new String[]{"String", "string"});
            String javaType = types[0];
            String tsType = types[1];

            GenTableColumn column = new GenTableColumn();
            column.setColumnName(columnName);
            column.setColumnComment(raw.comment());
            column.setColumnSort(i);
            column.setJavaField(toCamelCase(columnName));
            column.setJavaType(javaType);
            column.setTsType(isPk ? "string" : tsType);
            column.setIsPk(isPk);
            // 系统列（审计人/软删/审计时间）不进表单与查询；必填推导仅针对业务列
            column.setIsRequired(!isPk && !isAuditTime && !isSystemHidden && !raw.nullable() && !raw.hasDefault());
            column.setIsInsert(!isPk && !isAuditTime && !isSystemHidden);
            column.setIsEdit(!isPk && !isAuditTime && !isSystemHidden);
            // 长文本（VARCHAR(1000)+）默认不在列表展示，避免撑爆表格；审计时间按惯例进列表
            column.setIsList(!isPk && !isSystemHidden && (isAuditTime || raw.charLength() < 1000));
            column.setIsQuery(!isPk && !isAuditTime && !isSystemHidden && !"LocalDateTime".equals(javaType));
            column.setQueryType("String".equals(javaType) ? "LIKE" : "EQ");
            column.setHtmlType(isAuditTime ? "datetime" : deriveHtmlType(javaType, raw.charLength(), raw.comment()));
            if ("number".equals(column.getHtmlType())) {
                column.setMinLength(0);
            }
            if (raw.charLength() > 0 && "String".equals(javaType)) {
                column.setMaxLength(raw.charLength());
            }
            columns.add(column);
        }
        return columns;
    }

    /**
     * 推导表单组件类型：数值→number、布尔→switch、时间→datetime、
     * 注释含"内容/描述/备注/详情"或长文本→textarea、其余→input。
     */
    private String deriveHtmlType(String javaType, int charLength, String comment) {
        return switch (javaType) {
            case "Integer", "Long", "BigDecimal" -> "number";
            case "Boolean" -> "switch";
            case "LocalDateTime" -> "datetime";
            default -> (charLength >= 1000 || containsAny(comment, "内容", "描述", "备注", "详情")) ? "textarea" : "input";
        };
    }

    /**
     * 判断注释是否包含任一关键词。
     */
    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 下划线列名 → 大驼峰类名主干（sys_notice → Notice）。
     */
    private String toClassName(String tableName) {
        String body = tableName.startsWith("sys_") ? tableName.substring(4) : tableName;
        StringBuilder result = new StringBuilder();
        for (String part : body.split("_")) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return result.toString();
    }

    /**
     * 下划线列名/表名 → 小驼峰业务名（sys_notice → notice；notice_type → noticeType）。
     */
    private String toBusinessName(String tableName) {
        String body = tableName.startsWith("sys_") ? tableName.substring(4) : tableName;
        String[] parts = body.split("_");
        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                result.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            }
        }
        return result.toString();
    }

    /**
     * 下划线列名 → 小驼峰 Java 字段名（notice_type → noticeType）。
     */
    private String toCamelCase(String columnName) {
        return toBusinessName(columnName);
    }
}
