package cn.sijay.oi.sys.controller;

import cn.sijay.oi.support.AuthenticatedIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 参数配置模块 CRUD 集成测试（真实 Spring 上下文 + 内存 H2 + MockMvc）。
 *
 * <p>环境说明：覆盖数据源为内存 H2（每上下文独立、无残留），schema 每次执行保证表结构。
 * 验证全链路：Bean Validation → 业务校验 → JPA 落库（含软删除与 @SQLRestriction）→ 全局响应格式。
 *
 * <p>登录态：继承 {@link AuthenticatedIntegrationTest}，全部请求自动携带种子超管令牌。
 */
@DisplayName("参数配置模块 CRUD 集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:configcrud;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class SysConfigControllerTest extends AuthenticatedIntegrationTest {

    @Test
    @Order(1)
    @DisplayName("全链路：创建 → 详情 → 按键读取 → 更新 → 分页（CONTAINING + valueType 精确）→ 软删除 → 不可见可重建")
    void fullCrudFlow() throws Exception {
        // 1. 创建
        mockMvc.perform(post("/config").contentType("application/json")
                        .content("""
                                {"configKey":"site.name","configValue":"Oi 管理系统","valueType":"STRING","name":"站点名称","remark":"站点显示名"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(post("/config").contentType("application/json")
                        .content("""
                                {"configKey":"site.page-size","configValue":"20","valueType":"NUMBER","name":"分页大小"}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/config").contentType("application/json")
                        .content("""
                                {"configKey":"site.maintenance","configValue":"false","valueType":"BOOLEAN","name":"维护模式"}
                                """))
                .andExpect(jsonPath("$.code").value(200));

        // 2. 详情：id 为字符串（全局 Long 转字符串），valueTypeDesc 中文描述
        mockMvc.perform(get("/config/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.configKey").value("site.name"))
                .andExpect(jsonPath("$.data.configValue").value("Oi 管理系统"))
                .andExpect(jsonPath("$.data.valueType").value("STRING"))
                .andExpect(jsonPath("$.data.valueTypeDesc").value("字符串"))
                .andExpect(jsonPath("$.data.id").value("1"));

        // 3. 按键读取（运行时取值入口）
        mockMvc.perform(get("/config/key/site.page-size"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.configValue").value("20"))
                .andExpect(jsonPath("$.data.valueType").value("NUMBER"));

        // 4. 更新档案：configKey 不在更新入参中（不可修改）
        mockMvc.perform(put("/config/1").contentType("application/json")
                        .content("""
                                {"configValue":"Oi 运营平台","valueType":"STRING","name":"站点名称","remark":"改名后"}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/config/1"))
                .andExpect(jsonPath("$.data.configValue").value("Oi 运营平台"))
                .andExpect(jsonPath("$.data.configKey").value("site.name"));

        // 5. 分页：configKey 模糊（site. 命中 3 条）、name 模糊（分页 命中 1 条）、valueType 精确（BOOLEAN → 1 条）
        mockMvc.perform(get("/config/page").param("configKey", "site."))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.total").value("3"));
        mockMvc.perform(get("/config/page").param("name", "分页"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].configKey").value("site.page-size"));
        mockMvc.perform(get("/config/page").param("valueType", "BOOLEAN"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].configKey").value("site.maintenance"));

        // 6. 软删除：按键读取不可见（604002），同键配置可重建
        mockMvc.perform(delete("/config/2"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/config/key/site.page-size"))
                .andExpect(jsonPath("$.code").value(604002))
                .andExpect(jsonPath("$.message").value("参数不存在"));
        mockMvc.perform(post("/config").contentType("application/json")
                        .content("""
                                {"configKey":"site.page-size","configValue":"50","valueType":"NUMBER","name":"分页大小"}
                                """))
                .andExpect(jsonPath("$.code").value(200));

        // 7. 不存在的 id / 键：统一 604002
        mockMvc.perform(get("/config/999"))
                .andExpect(jsonPath("$.code").value(604002));
        mockMvc.perform(delete("/config/999"))
                .andExpect(jsonPath("$.code").value(604002));
        mockMvc.perform(get("/config/key/no.such.key"))
                .andExpect(jsonPath("$.code").value(604002));
    }

    @Test
    @Order(2)
    @DisplayName("业务校验：参数键重复返回 604001")
    void createDuplicateKey() throws Exception {
        mockMvc.perform(post("/config").contentType("application/json")
                        .content("""
                                {"configKey":"dup.key","configValue":"v1","valueType":"STRING","name":"重复键"}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/config").contentType("application/json")
                        .content("""
                                {"configKey":"dup.key","configValue":"v2","valueType":"STRING","name":"重复键"}
                                """))
                .andExpect(jsonPath("$.code").value(604001))
                .andExpect(jsonPath("$.message").value("参数键已存在"));
    }

    @Test
    @Order(3)
    @DisplayName("参数校验：必填缺失/格式非法/长度越界返回 400 与明确错误信息")
    void createValidationFailures() throws Exception {
        // 必填缺失：configKey
        mockMvc.perform(post("/config").contentType("application/json")
                        .content("""
                                {"configValue":"v","valueType":"STRING","name":"站点名称"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数键不能为空"));

        // 长度越界：configKey 单字符
        mockMvc.perform(post("/config").contentType("application/json")
                        .content("""
                                {"configKey":"a","configValue":"v","valueType":"STRING","name":"站点名称"}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数键长度须在 2~100 位之间"));

        // 格式非法：configKey 不符合点分命名（含空格与中文）
        mockMvc.perform(post("/config").contentType("application/json")
                        .content("""
                                {"configKey":"bad key 站","configValue":"v","valueType":"STRING","name":"站点名称"}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数键须为点分命名格式（如 site.name），仅允许字母、数字、下划线、连字符与点号"));

        // 枚举非法：valueType 不在四个取值内
        mockMvc.perform(post("/config").contentType("application/json")
                        .content("""
                                {"configKey":"ok.key","configValue":"v","valueType":"OBJECT","name":"站点名称"}
                                """))
                .andExpect(jsonPath("$.code").value(400));

        // 更新接口：configValue 为 null 必填校验
        mockMvc.perform(put("/config/1").contentType("application/json")
                        .content("""
                                {"valueType":"STRING","name":"站点名称"}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数值不能为 null"));

        // 路径参数：非法 id
        mockMvc.perform(get("/config/0"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("配置 id 必须为正整数"));
    }
}
