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
 * 角色模块 CRUD 集成测试（真实 Spring 上下文 + 内存 H2 + MockMvc）。
 *
 * <p>环境说明：覆盖数据源为内存 H2（每上下文独立、无残留），schema 每次执行保证表结构。
 * 验证全链路：Bean Validation → 业务校验 → JPA 落库（含软删除与 @SQLRestriction）→ 全局响应格式。
 *
 * <p>登录态：继承 {@link AuthenticatedIntegrationTest}，全部请求自动携带种子超管令牌。
 */
@DisplayName("角色模块 CRUD 集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rolecrud;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class SysRoleControllerTest extends AuthenticatedIntegrationTest {

    private static final String CREATE_BODY = """
            {"code":"%s","name":"%s","sort":%d,"enabled":true,"remark":"测试角色"}
            """;

    @Test
    @Order(1)
    @DisplayName("全链路：创建 → 详情 → 更新 → 分页（CONTAINING 模糊匹配 + sort 排序）→ 软删除 → 不可见可重建")
    void fullCrudFlow() throws Exception {
        // 1. 创建：返回标准成功结构；sort 未传默认 0
        mockMvc.perform(post("/role").contentType("application/json")
                        .content(CREATE_BODY.formatted("admin", "管理员", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 2. 详情：id 为字符串（全局 Long 转字符串），statusDesc 输出中文描述
        mockMvc.perform(get("/role/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.code").value("admin"))
                .andExpect(jsonPath("$.data.name").value("管理员"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.createTime").isNotEmpty());

        // 3. 更新档案：code 不在更新入参中（不可修改），name/sort/status 变更生效
        mockMvc.perform(put("/role/1").contentType("application/json")
                        .content("""
                                {"name":"超级管理员","sort":10,"enabled":false,"remark":"更新后"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/role/1"))
                .andExpect(jsonPath("$.data.name").value("超级管理员"))
                .andExpect(jsonPath("$.data.sort").value(10))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.code").value("admin"));

        // 4. 分页 CONTAINING 模糊匹配 + 显式排序
        mockMvc.perform(post("/role").contentType("application/json")
                        .content(CREATE_BODY.formatted("data-viewer", "数据查看员", 2)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/role").contentType("application/json")
                        .content(CREATE_BODY.formatted("viewer", "查看员", 0)))
                .andExpect(jsonPath("$.code").value(200));

        // code 模糊匹配：view 命中 data-viewer 与 viewer
        mockMvc.perform(get("/role/page").param("code", "view"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.total").value("2"));
        // name 模糊匹配 + 忽略大小写
        mockMvc.perform(get("/role/page").param("name", "查看"))
                .andExpect(jsonPath("$.data", hasSize(2)));
        // status 枚举精确匹配：仅 admin（已更新为 DISABLED）
        mockMvc.perform(get("/role/page").param("enabled", "false"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].code").value("admin"));
        // 显式排序：sortField=sort 升序 → 首条为 sort=0 的 viewer
        mockMvc.perform(get("/role/page").param("sortField", "sort").param("desc", "false"))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].code").value("viewer"));

        // 5. 软删除：删除后详情不可见（602001），同编码角色可重建（(code, deleted) 唯一）
        mockMvc.perform(delete("/role/2"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/role/2"))
                .andExpect(jsonPath("$.code").value(602001))
                .andExpect(jsonPath("$.message").value("角色不存在"));
        mockMvc.perform(post("/role").contentType("application/json")
                        .content(CREATE_BODY.formatted("data-viewer", "数据查看员新", 2)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/role/page").param("code", "data-viewer"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("数据查看员新"));

        // 6. 删除/更新/查询不存在的 id：统一返回 602001
        mockMvc.perform(delete("/role/999"))
                .andExpect(jsonPath("$.code").value(602001));
        mockMvc.perform(put("/role/999").contentType("application/json")
                        .content("""
                                {"name":"x","sort":0,"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(602001));
    }

    @Test
    @Order(2)
    @DisplayName("业务校验：角色编码重复返回 602002")
    void createDuplicateCode() throws Exception {
        mockMvc.perform(post("/role").contentType("application/json")
                        .content(CREATE_BODY.formatted("duprole", "重复角色", 0)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/role").contentType("application/json")
                        .content(CREATE_BODY.formatted("duprole", "重复角色", 0)))
                .andExpect(jsonPath("$.code").value(602002))
                .andExpect(jsonPath("$.message").value("角色编码已存在"));
    }

    @Test
    @Order(3)
    @DisplayName("参数校验：必填缺失/格式非法/长度越界/负数排序返回 400 与明确错误信息")
    void createValidationFailures() throws Exception {
        // 必填缺失：code
        mockMvc.perform(post("/role").contentType("application/json")
                        .content("""
                                {"name":"管理员","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("角色编码不能为空"));

        // 长度越界：code 不足 2 位
        mockMvc.perform(post("/role").contentType("application/json")
                        .content("""
                                {"code":"a","name":"管理员","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("角色编码长度须在 2~50 位之间"));

        // 格式非法：code 含非法字符
        mockMvc.perform(post("/role").contentType("application/json")
                        .content("""
                                {"code":"bad role!","name":"管理员","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("角色编码仅允许字母、数字、下划线、连字符"));

        // 负数排序号
        mockMvc.perform(post("/role").contentType("application/json")
                        .content("""
                                {"code":"ok-role","name":"管理员","sort":-1,"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("排序号不能为负数"));

        // 枚举非法：status 值不在 NORMAL/DISABLED 内
        mockMvc.perform(post("/role").contentType("application/json")
                        .content("""
                                {"code":"ok-role","name":"管理员","enabled":"WHATEVER"}
                                """))
                .andExpect(jsonPath("$.code").value(400));

        // 更新接口：name 为 null 必填校验
        mockMvc.perform(put("/role/1").contentType("application/json")
                        .content("""
                                {"sort":0,"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("角色名称不能为 null"));

        // 路径参数：非法 id
        mockMvc.perform(get("/role/0"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("角色 id 必须为正整数"));
    }
}
