package cn.sijay.oi.gen.service;

import cn.sijay.oi.gen.entity.GenTable;
import cn.sijay.oi.gen.entity.GenTableColumn;
import cn.sijay.oi.gen.model.GenTableSaveCmd;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 代码生成器集成测试（真实 Spring 上下文 + 内存 H2）。
 *
 * <p>验证全链路：导入表结构（类型/必填/组件/查询方式自动推导）→ 配置维护 →
 * FreeMarker 渲染产物（11 个前后端文件，关键内容断言）。渲染仅内存预览、不落盘，
 * 落盘与真实编译验证走管理页手动流程（见代码生成文档）。
 */
@DisplayName("代码生成器集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gen;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        // 内嵌库默认 create-drop 会用实体重建表、丢弃 COMMENT ON 注释（代码生成依赖注释推导），
        // 显式关闭自动 DDL，让 schema-h2.sql（含注释）成为唯一建表来源
        "spring.jpa.hibernate.ddl-auto=none"
})
class GenServiceTest {

    @Autowired
    private GenTableService tableService;

    @Autowired
    private GenRenderService renderService;

    private static Long tableId;

    @Test
    @Order(1)
    @DisplayName("导入 sys_notice：类名/业务名自动推导，字段配置按列类型/注释/长度自动生成")
    void importTable() {
        GenTable table = tableService.importTable("sys_notice");
        tableId = table.getId();

        // 表配置推导：sys_notice → Notice / notice / 通知公告
        assertThat(table.getClassName()).isEqualTo("Notice");
        assertThat(table.getBusinessName()).isEqualTo("notice");
        assertThat(table.getModuleName()).isEqualTo("sys");
        assertThat(table.getFunctionName()).isEqualTo("通知公告");

        List<GenTableColumn> columns = tableService.getColumnsByTableId(tableId);
        Map<String, GenTableColumn> byField = columns.stream()
                .collect(java.util.stream.Collectors.toMap(GenTableColumn::getJavaField, c -> c));

        // 主键：isPk、不进表单/查询、TS 类型 string
        GenTableColumn id = byField.get("id");
        assertThat(id.getIsPk()).isTrue();
        assertThat(id.getTsType()).isEqualTo("string");
        assertThat(id.getIsInsert()).isFalse();

        // 业务列：title 必填 String LIKE 查询；content 长文本 textarea 且不进列表
        GenTableColumn title = byField.get("title");
        assertThat(title.getJavaType()).isEqualTo("String");
        assertThat(title.getIsRequired()).isTrue();
        assertThat(title.getQueryType()).isEqualTo("LIKE");
        assertThat(title.getMaxLength()).isEqualTo(100);
        GenTableColumn content = byField.get("content");
        assertThat(content.getHtmlType()).isEqualTo("textarea");
        assertThat(content.getIsList()).isFalse();
        assertThat(content.getMaxLength()).isEqualTo(2000);

        // 布尔/时间：top_marked Boolean + switch 组件；publish_time datetime 且不进查询
        GenTableColumn topMarked = byField.get("topMarked");
        assertThat(topMarked.getJavaType()).isEqualTo("Boolean");
        assertThat(topMarked.getTsType()).isEqualTo("boolean");
        assertThat(topMarked.getHtmlType()).isEqualTo("switch");
        assertThat(topMarked.getMinLength()).isNull();
        GenTableColumn publishTime = byField.get("publishTime");
        assertThat(publishTime.getJavaType()).isEqualTo("LocalDateTime");
        assertThat(publishTime.getHtmlType()).isEqualTo("datetime");
        assertThat(publishTime.getIsQuery()).isFalse();

        // 系统列：审计字段/软删不进表单与查询；create_time 审计时间仅进列表
        assertThat(byField.get("createBy").getIsInsert()).isFalse();
        assertThat(byField.get("deleted").getIsList()).isFalse();
        assertThat(byField.get("createTime").getIsList()).isTrue();
        assertThat(byField.get("createTime").getIsInsert()).isFalse();
    }

    @Test
    @Order(2)
    @DisplayName("导入查重：同一表重复导入返回 600003")
    void importDuplicate() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tableService.importTable("sys_notice"))
                .isInstanceOf(cn.sijay.oi.common.exception.BusinessException.class)
                .hasMessageContaining("数据已存在");
    }

    @Test
    @Order(3)
    @DisplayName("渲染预览：配置 notice_type 为字典下拉后，11 个前后端文件关键内容正确")
    void preview() {
        // 模拟编辑页配置：notice_type 设为静态下拉（真实流程为导入后在编辑页人工微调）
        List<GenTableColumn> columns = tableService.getColumnsByTableId(tableId);
        List<GenTableSaveCmd.ColumnCmd> columnCmds = columns.stream().map(c -> new GenTableSaveCmd.ColumnCmd(
                c.getId(), c.getColumnName(), c.getColumnComment(), c.getColumnSort(), c.getJavaField(),
                c.getJavaType(), c.getTsType(), c.getIsPk(), c.getIsRequired(), c.getIsInsert(), c.getIsEdit(),
                c.getIsList(), c.getIsQuery(), c.getQueryType(),
                "notice_type".equals(c.getColumnName()) ? "select" : c.getHtmlType(),
                "notice_type".equals(c.getColumnName()) ? "[{\"value\":\"0\",\"label\":\"通知\"},{\"value\":\"1\",\"label\":\"公告\"}]" : c.getOptionsJson(),
                c.getDictType(), c.getMinLength(), c.getMaxLength(), c.getRegex())).toList();
        tableService.save(tableId, new GenTableSaveCmd("sys_notice", "Notice", "sys", "notice", "通知公告", "sijay", columnCmds));

        Map<String, String> files = renderService.preview(tableId);

        // 产物清单：后端 8 个 + 前端 3 个
        assertThat(files).hasSize(11);
        assertThat(files.keySet()).contains(
                "src/main/java/cn/sijay/oi/sys/entity/SysNotice.java",
                "src/main/java/cn/sijay/oi/sys/repository/SysNoticeRepository.java",
                "src/main/java/cn/sijay/oi/sys/service/SysNoticeService.java",
                "src/main/java/cn/sijay/oi/sys/controller/SysNoticeController.java",
                "src/main/java/cn/sijay/oi/sys/model/notice/SysNoticeCreateCmd.java",
                "src/main/java/cn/sijay/oi/sys/model/notice/SysNoticeQuery.java",
                "src/main/java/cn/sijay/oi/sys/model/notice/SysNoticeVO.java",
                "vue/src/types/notice.d.ts",
                "vue/src/api/notice.ts",
                "vue/src/views/sys/NoticeView.vue");

        // 实体：类名/表名/软删注解
        String entity = files.get("src/main/java/cn/sijay/oi/sys/entity/SysNotice.java");
        assertThat(entity).contains("public class SysNotice", "@Table(name = \"sys_notice\")", "@SQLRestriction(\"deleted = 0\")");

        // CreateCmd：必填与长度校验对齐列配置
        String createCmd = files.get("src/main/java/cn/sijay/oi/sys/model/notice/SysNoticeCreateCmd.java");
        assertThat(createCmd).contains("@NotBlank(message = \"通知标题不能为空\")", "@Size(max = 100, message = \"通知标题最长 100 字符\")");

        // Service：QBE 模糊匹配 + 软删
        String service = files.get("src/main/java/cn/sijay/oi/sys/service/SysNoticeService.java");
        assertThat(service).contains("StringMatcher.CONTAINING", "softDelete(id)");

        // 前端：API 路径/类型引用/页面组件
        String api = files.get("vue/src/api/notice.ts");
        assertThat(api).contains("export const noticeApi", "request.page<Result<SysNoticeVO[]>>('/notice/page', data)");
        String view = files.get("vue/src/views/sys/NoticeView.vue");
        assertThat(view).contains("useTablePage<SysNoticeQuery, SysNoticeVO>", "noticeTypeOptions", "el-pagination");
        String types = files.get("vue/src/types/notice.d.ts");
        assertThat(types).contains("export interface SysNoticeVO", "id: string");
    }

    @Test
    @Order(4)
    @DisplayName("保存配置：列配置全量覆盖保存后渲染同步生效")
    void saveAndRender() {
        List<GenTableColumn> columns = tableService.getColumnsByTableId(tableId);
        List<GenTableSaveCmdHelper> columnCmds = columns.stream()
                .map(c -> new GenTableSaveCmdHelper(c)).toList();

        GenTableSaveCmd cmd = new GenTableSaveCmd("sys_notice", "Notice", "sys", "notice", "通知公告", "sijay",
                columnCmds.stream().map(GenTableSaveCmdHelper::toCmd).toList());
        tableService.save(tableId, cmd);

        // 全量覆盖后列数量不变，渲染仍成功
        assertThat(tableService.getColumnsByTableId(tableId)).hasSameSizeAs(columns);
        assertThat(renderService.preview(tableId)).hasSize(11);
    }

    @Test
    @Order(5)
    @DisplayName("删除配置：表配置与字段配置一并物理删除")
    void deleteTable() {
        tableService.delete(tableId);
        assertThat(tableService.getColumnsByTableId(tableId)).isEmpty();
        assertThat(renderService.preview(tableId = tableService.importTable("sys_notice").getId())).hasSize(11);
        tableService.delete(tableId);
    }

    /**
     * 测试辅助：GenTableColumn → GenTableSaveCmd.ColumnCmd 转换（避免测试内重复映射代码）。
     */
    private record GenTableSaveCmdHelper(GenTableColumn c) {
        GenTableSaveCmd.ColumnCmd toCmd() {
            return new GenTableSaveCmd.ColumnCmd(c.getId(), c.getColumnName(), c.getColumnComment(), c.getColumnSort(),
                    c.getJavaField(), c.getJavaType(), c.getTsType(), c.getIsPk(), c.getIsRequired(), c.getIsInsert(),
                    c.getIsEdit(), c.getIsList(), c.getIsQuery(), c.getQueryType(), c.getHtmlType(), c.getOptionsJson(),
                    c.getDictType(), c.getMinLength(), c.getMaxLength(), c.getRegex());
        }
    }
}
