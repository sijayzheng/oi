package cn.sijay.oi.gen.service;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.gen.entity.GenTable;
import cn.sijay.oi.gen.entity.GenTableColumn;
import cn.sijay.oi.gen.repository.GenTableRepository;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码生成渲染服务：基于 FreeMarker 模板渲染前后端全套 CRUD 代码，支持预览与落盘。
 *
 * <p><b>模板清单</b>（classpath:/templates/gen/，均为 FreeMarker 语法）：
 * 单表场景 9 个——entity/repository/service/controller/create-cmd/update-cmd/query/vo（后端 Java）
 * 与 types/api/view（前端 TS/Vue）；主子表场景额外提供 view-master-detail.ftl（前端双栏联动页）。
 *
 * <p><b>渲染数据模型</b>：以 {@link GenTable} 与 {@link GenTableColumn} 构建扁平 Map
 * （table 信息 + 全量/查询/表单/列表列视图），模板只做展示层组装、不含业务判断。
 *
 * <p><b>落盘约定</b>：项目根目录相对路径——后端 {@code src/main/java/cn/sijay/oi/<module>/...}，
 * 前端 {@code vue/src/...}；同名文件直接覆盖（重复生成幂等）。
 * 生成前端文件后自动执行两处锚点插入：{@code vue/src/router/index.ts} 的路由注册与
 * {@code vue/src/layouts/AdminLayout.vue} 的菜单注册（锚点约定见代码生成文档，重复生成幂等跳过）。
 *
 * @author sijay
 */
@Service
@RequiredArgsConstructor
public class GenRenderService {

    /**
     * 前端路由注册锚点：定位 system/config 路由块内部行；插入时自动跳至该路由块收尾行 "}," 之后，
     * 避免插入到路由对象中间破坏语法（router/index.ts）。
     */
    private static final String ROUTER_ANCHOR = "          path: 'system/config',";

    /**
     * 菜单注册锚点：新菜单项插入在该行之后（AdminLayout.vue）。
     */
    private static final String MENU_ANCHOR = "  { index: '/system/config', title: '参数配置', icon: Setting },";

    /**
     * 演示表主子表信息：主表表名 → 子表信息（由表配置关系自动发现，无需硬编码）。
     */
    private final GenTableRepository tableRepository;
    private final GenTableService tableService;

    /**
     * FreeMarker 配置：模板目录 classpath:/templates/gen，UTF-8。
     */
    private Configuration freemarkerConfig;

    /**
     * 初始化 FreeMarker 配置（模板加载路径与编码）。
     */
    @PostConstruct
    public void init() {
        freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
        freemarkerConfig.setClassForTemplateLoading(GenRenderService.class, "/templates/gen");
        freemarkerConfig.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // 数字按原样输出：默认 number 格式会加千分位逗号（2000→"2,000"），污染生成的注解与代码
        freemarkerConfig.setNumberFormat("computer");
    }

    /**
     * 预览生成产物：渲染全部文件但不落盘。
     *
     * @param tableId 表配置 id
     * @return 相对项目根路径 → 文件内容（保持生成顺序）
     */
    public Map<String, String> preview(Long tableId) {
        GenTable table = tableService.getTableById(tableId);
        List<GenTableColumn> columns = tableService.getColumnsByTableId(tableId);
        return buildFiles(table, columns, null);
    }

    /**
     * 生成并落盘：渲染全部文件写入项目目录，并执行前端路由/菜单锚点插入。
     *
     * @param tableId 表配置 id
     * @return 已写入文件的相对路径清单
     */
    public List<String> generate(Long tableId) {
        GenTable table = tableService.getTableById(tableId);
        List<GenTableColumn> columns = tableService.getColumnsByTableId(tableId);
        Path projectRoot = Path.of("").toAbsolutePath();
        Map<String, String> files = buildFiles(table, columns, projectRoot);
        files.forEach((relativePath, content) -> write(projectRoot.resolve(relativePath), content));
        return new ArrayList<>(files.keySet());
    }

    /**
     * 渲染全部产物文件；outputRoot 非 null 时同时处理前端锚点插入。
     *
     * @param table      表配置
     * @param columns    字段配置
     * @param outputRoot 项目根目录；null 表示仅预览不处理锚点
     * @return 相对路径 → 文件内容（LinkedHashMap 保持生成顺序）
     */
    private Map<String, String> buildFiles(GenTable table, List<GenTableColumn> columns, Path outputRoot) {
        Map<String, Object> model = buildRenderModel(table, columns);
        String module = table.getModuleName();
        String business = table.getBusinessName();
        Map<String, String> files = new LinkedHashMap<>();
        try {
            // 后端 Java
            files.put("src/main/java/cn/sijay/oi/%s/entity/Sys%s.java".formatted(module, table.getClassName()),
                    render("entity.ftl", model));
            files.put("src/main/java/cn/sijay/oi/%s/repository/Sys%sRepository.java".formatted(module, table.getClassName()),
                    render("repository.ftl", model));
            files.put("src/main/java/cn/sijay/oi/%s/service/Sys%sService.java".formatted(module, table.getClassName()),
                    render("service.ftl", model));
            files.put("src/main/java/cn/sijay/oi/%s/controller/Sys%sController.java".formatted(module, table.getClassName()),
                    render("controller.ftl", model));
            files.put("src/main/java/cn/sijay/oi/%s/model/%s/Sys%sCreateCmd.java".formatted(module, business, table.getClassName()),
                    render("create-cmd.ftl", model));
            files.put("src/main/java/cn/sijay/oi/%s/model/%s/Sys%sUpdateCmd.java".formatted(module, business, table.getClassName()),
                    render("update-cmd.ftl", model));
            files.put("src/main/java/cn/sijay/oi/%s/model/%s/Sys%sQuery.java".formatted(module, business, table.getClassName()),
                    render("query.ftl", model));
            files.put("src/main/java/cn/sijay/oi/%s/model/%s/Sys%sVO.java".formatted(module, business, table.getClassName()),
                    render("vo.ftl", model));
            // 前端 TS/Vue
            files.put("vue/src/types/%s.d.ts".formatted(business), render("types.ftl", model));
            files.put("vue/src/api/%s.ts".formatted(business), render("api.ftl", model));
            files.put("vue/src/views/%s/%sView.vue".formatted(module, table.getClassName()),
                    render(Boolean.TRUE.equals(model.get("masterDetail")) ? "view-master-detail.ftl" : "view.ftl", model));
        }
        catch (Exception e) {
            throw new BusinessException(BusinessErrorCode.GEN_RENDER_FAILED);
        }
        if (outputRoot != null) {
            insertFrontendAnchors(outputRoot, model);
        }
        return files;
    }

    /**
     * 构建渲染数据模型：table 信息 + 各视图列集合（全量/查询/新增表单/编辑表单/列表）。
     *
     * <p>主子表场景：本表存在以其为主表的子表配置时，额外注入 {@code masterDetail=true} 与
     * {@code child} 视图（子表业务名/功能名/外键 Java 字段名），供双栏联动页模板使用。
     */
    private Map<String, Object> buildRenderModel(GenTable table, List<GenTableColumn> columns) {
        Map<String, Object> model = new HashMap<>();
        model.put("tableName", table.getTableName());
        model.put("tableComment", table.getTableComment());
        model.put("className", table.getClassName());
        model.put("entityName", "Sys" + table.getClassName());
        model.put("moduleName", table.getModuleName());
        model.put("businessName", table.getBusinessName());
        model.put("functionName", table.getTableComment() != null && !table.getTableComment().isBlank()
                ? table.getTableComment() : table.getFunctionName());
        model.put("author", table.getAuthor());

        List<Map<String, Object>> columnMaps = columns.stream().map(this::toColumnMap).toList();
        model.put("columns", columnMaps);
        model.put("queryColumns", columnMaps.stream().filter(c -> Boolean.TRUE.equals(c.get("query"))).toList());
        model.put("insertColumns", columnMaps.stream().filter(c -> Boolean.TRUE.equals(c.get("insert"))).toList());
        model.put("editColumns", columnMaps.stream().filter(c -> Boolean.TRUE.equals(c.get("edit"))).toList());
        model.put("listColumns", columnMaps.stream().filter(c -> Boolean.TRUE.equals(c.get("list"))).toList());
        model.put("hasBigDecimal", columnMaps.stream().anyMatch(c -> "BigDecimal".equals(c.get("javaType"))));
        model.put("hasLocalDateTime", columnMaps.stream().anyMatch(c -> "LocalDateTime".equals(c.get("javaType"))));
        model.put("isChildTable", table.getParentTableId() != null);
        if (table.getParentTableId() != null) {
            // 子表场景：本表外键列的 Java 字段名（级联软删 JPQL 与 repository 方法名使用）
            model.put("fkJavaField", columnMaps.stream()
                    .filter(c -> c.get("columnName").toString().equalsIgnoreCase(table.getFkColumn()))
                    .map(c -> String.valueOf(c.get("javaField")))
                    .findFirst()
                    .orElse("parent"));
        }

        tableRepository.findAll().stream()
                .filter(child -> table.getId().equals(child.getParentTableId()))
                .findFirst()
                .ifPresentOrElse(child -> {
                    model.put("masterDetail", true);
                    List<Map<String, Object>> childColumnMaps = tableService.getColumnsByTableId(child.getId())
                            .stream().map(this::toColumnMap).toList();
                    String fkJavaField = childColumnMaps.stream()
                            .filter(c -> c.get("columnName").toString().equalsIgnoreCase(child.getFkColumn()))
                            .map(c -> String.valueOf(c.get("javaField")))
                            .findFirst()
                            .orElseThrow(() -> new BusinessException(BusinessErrorCode.COMMON_PARAM_ERROR));
                    Map<String, Object> childModel = new HashMap<>();
                    childModel.put("businessName", child.getBusinessName());
                    childModel.put("functionName", child.getTableComment() == null || child.getTableComment().isBlank()
                            ? child.getFunctionName() : child.getTableComment());
                    childModel.put("moduleName", child.getModuleName());
                    childModel.put("className", child.getClassName());
                    childModel.put("entityName", "Sys" + child.getClassName());
                    childModel.put("fkJavaField", fkJavaField);
                    childModel.put("columns", childColumnMaps);
                    childModel.put("listColumns", childColumnMaps.stream()
                            .filter(c -> Boolean.TRUE.equals(c.get("list")) && !Boolean.TRUE.equals(c.get("pk"))).toList());
                    childModel.put("insertColumns", childColumnMaps.stream()
                            .filter(c -> Boolean.TRUE.equals(c.get("insert"))).toList());
                    childModel.put("editColumns", childColumnMaps.stream()
                            .filter(c -> Boolean.TRUE.equals(c.get("edit"))).toList());
                    model.put("child", childModel);
                }, () -> model.put("masterDetail", false));
        return model;
    }

    /**
     * 单列配置 → 渲染视图 Map（补充派生标记：是否字符串/数值、是否有长度/正则/最小值约束等）。
     */
    private Map<String, Object> toColumnMap(GenTableColumn c) {
        Map<String, Object> map = new LinkedHashMap<>();
        boolean isString = "String".equals(c.getJavaType());
        boolean isNumber = "Integer".equals(c.getJavaType()) || "Long".equals(c.getJavaType())
                || "BigDecimal".equals(c.getJavaType());
        map.put("columnName", c.getColumnName());
        map.put("comment", c.getColumnComment());
        map.put("label", c.getColumnComment() == null || c.getColumnComment().isBlank() ? c.getJavaField() : c.getColumnComment());
        map.put("javaField", c.getJavaField());
        map.put("javaType", c.getJavaType());
        map.put("tsType", c.getTsType());
        map.put("pk", Boolean.TRUE.equals(c.getIsPk()));
        map.put("required", Boolean.TRUE.equals(c.getIsRequired()));
        map.put("insert", Boolean.TRUE.equals(c.getIsInsert()));
        map.put("edit", Boolean.TRUE.equals(c.getIsEdit()));
        map.put("list", Boolean.TRUE.equals(c.getIsList()));
        map.put("query", Boolean.TRUE.equals(c.getIsQuery()));
        map.put("auditTime", "create_time".equalsIgnoreCase(c.getColumnName()) || "update_time".equalsIgnoreCase(c.getColumnName()));
        map.put("systemHidden", "create_by".equalsIgnoreCase(c.getColumnName()) || "update_by".equalsIgnoreCase(c.getColumnName())
                || "deleted".equalsIgnoreCase(c.getColumnName()));
        map.put("likeQuery", "LIKE".equals(c.getQueryType()));
        map.put("htmlType", c.getHtmlType());
        map.put("optionsJson", c.getOptionsJson());
        map.put("dictType", c.getDictType());
        map.put("isString", isString);
        map.put("isNumber", isNumber);
        map.put("isDate", "LocalDateTime".equals(c.getJavaType()));
        map.put("hasRegex", c.getRegex() != null && !c.getRegex().isBlank());
        map.put("hasMax", c.getMaxLength() != null);
        map.put("hasMin", c.getMinLength() != null);
        map.put("maxLength", c.getMaxLength());
        map.put("minLength", c.getMinLength());
        map.put("regex", c.getRegex());
        return map;
    }

    /**
     * 判断是否存在以本表为主表的子表配置（决定主表渲染单栏页还是主子表双栏页）。
     */
    private boolean hasChildTable(Long tableId) {
        return tableRepository.findAll().stream().anyMatch(t -> tableId.equals(t.getParentTableId()));
    }

    /**
     * FreeMarker 模板渲染。
     */
    private String render(String templateName, Map<String, Object> model) throws Exception {
        Template template = freemarkerConfig.getTemplate(templateName);
        StringWriter writer = new StringWriter();
        template.process(model, writer);
        return writer.toString();
    }

    /**
     * 前端锚点插入：注册新路由与菜单项（幂等——已注册则跳过）。
     *
     * <p>锚点约定（详见代码生成文档）：router/index.ts 中 {@link #ROUTER_ANCHOR} 所在路由块之后
     * 插入同结构新路由；AdminLayout.vue 中 {@link #MENU_ANCHOR} 菜单项之后插入新菜单项。
     */
    private void insertFrontendAnchors(Path projectRoot, Map<String, Object> model) {
        String routePath = "/%s/%s".formatted(model.get("moduleName"), model.get("businessName"));
        String routeName = "%s-%s".formatted(model.get("moduleName"), model.get("businessName"));
        String title = String.valueOf(model.get("functionName"));
        String viewFile = String.valueOf(model.get("className")) + "View.vue";

        String routeBlock = """
                        {
                          path: '%s',
                          name: '%s',
                          component: () => import('../views/%s/%s'),
                          meta: { title: '%s' },
                        },
                """.formatted(routePath, routeName, model.get("moduleName"), viewFile, title);
        insertAfterAnchor(projectRoot.resolve("vue/src/router/index.ts"), ROUTER_ANCHOR, routeBlock, routePath, true);

        String menuLine = "  { index: '%s', title: '%s', icon: Setting },".formatted(routePath, title);
        insertAfterAnchor(projectRoot.resolve("vue/src/layouts/AdminLayout.vue"), MENU_ANCHOR, menuLine, routePath, false);
    }

    /**
     * 在目标文件锚点行之后插入内容（幂等：文件已包含 idempotentMark 标记时跳过）。
     *
     * <p>{@code skipToBlockEnd} 为 true 时（路由块场景），锚点行位于目标路由对象内部，
     * 需向下跳过至该对象的收尾行（去除缩进后为 "}," 的行）之后再插入，
     * 避免插入到对象中间破坏语法；为 false 时直接在锚点行下一行插入。
     */
    private void insertAfterAnchor(Path file, String anchor, String content, String idempotentMark, boolean skipToBlockEnd) {
        try {
            if (!Files.exists(file)) {
                return;
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (source.contains(idempotentMark)) {
                return;
            }
            List<String> lines = new ArrayList<>(List.of(source.split("\r?\n", -1)));
            int anchorLine = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(anchor)) {
                    anchorLine = i;
                    break;
                }
            }
            if (anchorLine < 0) {
                return;
            }
            int insertLine = anchorLine + 1;
            if (skipToBlockEnd) {
                while (insertLine < lines.size() && !lines.get(insertLine).strip().equals("},")) {
                    insertLine++;
                }
                insertLine++;
            }
            for (String line : content.stripTrailing().split("\r?\n", -1)) {
                lines.add(insertLine++, line);
            }
            String separator = source.contains("\r\n") ? "\r\n" : "\n";
            Files.writeString(file, String.join(separator, lines), StandardCharsets.UTF_8);
        }
        catch (Exception ignored) {
            // 锚点插入失败不阻断生成主流程；人工按文档补充注册即可
        }
    }

    /**
     * 写盘：自动创建父目录，UTF-8 覆盖写。
     */
    private void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        }
        catch (Exception e) {
            throw new BusinessException(BusinessErrorCode.GEN_WRITE_FAILED);
        }
    }
}
