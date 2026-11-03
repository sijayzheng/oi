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
 * 字典模块（类型 + 项双表）集成测试（真实 Spring 上下文 + 内存 H2 + MockMvc）。
 *
 * <p>环境说明：覆盖数据源为内存 H2（每上下文独立、无残留），schema 每次执行保证表结构。
 * 验证全链路：Bean Validation → 双表联动业务校验 → JPA 落库
 * （含级联软删除与 @SQLRestriction）→ 全局响应格式。
 *
 * <p>登录态：继承 {@link AuthenticatedIntegrationTest}，全部请求自动携带种子超管令牌。
 */
@DisplayName("字典模块（类型+项）集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dictcrud;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class SysDictControllerTest extends AuthenticatedIntegrationTest {

    @Test
    @Order(1)
    @DisplayName("全链路：建类型 → 建项 → 下拉选项（sort 升序）→ 更新 → 分页 → 删类型级联软删项")
    void fullDictFlow() throws Exception {
        // 1. 创建字典类型 user_status
        mockMvc.perform(post("/dict/type").contentType("application/json")
                        .content("""
                                {"dictType":"user_status","name":"用户状态","enabled":true,"remark":"测试字典"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 2. 详情：id 为字符串，statusDesc 中文
        mockMvc.perform(get("/dict/type/1"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.dictType").value("user_status"))
                .andExpect(jsonPath("$.data.name").value("用户状态"))
                .andExpect(jsonPath("$.data.id").value("1"));

        // 3. 创建字典项（sort 乱序创建，验证下拉按 sort 升序）
        mockMvc.perform(post("/dict/item").contentType("application/json")
                        .content("""
                                {"dictType":"user_status","itemCode":"DISABLED","itemLabel":"禁用","sort":2,"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/dict/item").contentType("application/json")
                        .content("""
                                {"dictType":"user_status","itemCode":"NORMAL","itemLabel":"正常","sort":1,"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(200));

        // 4. 下拉选项：按 sort 升序 → NORMAL 在前；SelectOption 结构 value/label
        mockMvc.perform(get("/dict/item/options/user_status"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].value").value("NORMAL"))
                .andExpect(jsonPath("$.data[0].label").value("正常"))
                .andExpect(jsonPath("$.data[1].value").value("DISABLED"));

        // 5. 更新字典项：itemCode/dictType 不可改，label/sort/status 生效
        mockMvc.perform(put("/dict/item/1").contentType("application/json")
                        .content("""
                                {"itemLabel":"已禁用","sort":3,"enabled":false}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/dict/item/1"))
                .andExpect(jsonPath("$.data.itemLabel").value("已禁用"))
                .andExpect(jsonPath("$.data.sort").value(3))
                .andExpect(jsonPath("$.data.enabled").value(false));

        // 6. 更新字典类型：dictType 不可改
        mockMvc.perform(put("/dict/type/1").contentType("application/json")
                        .content("""
                                {"name":"用户状态枚举","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/dict/type/1"))
                .andExpect(jsonPath("$.data.name").value("用户状态枚举"))
                .andExpect(jsonPath("$.data.dictType").value("user_status"));

        // 7. 分页：类型 name 模糊；项 dictType 精确 + itemLabel 模糊
        mockMvc.perform(get("/dict/type/page").param("name", "枚举"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.total").value("1"));
        mockMvc.perform(get("/dict/item/page").param("dictType", "user_status").param("itemLabel", "用"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].itemCode").value("DISABLED"))
                .andExpect(jsonPath("$.data[0].itemLabel").value("已禁用"));

        // 8. 删除字典类型 → 级联软删项：类型 605002，项 605004，options 605002
        mockMvc.perform(delete("/dict/type/1"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/dict/type/1"))
                .andExpect(jsonPath("$.code").value(605002));
        mockMvc.perform(get("/dict/item/1"))
                .andExpect(jsonPath("$.code").value(605004))
                .andExpect(jsonPath("$.message").value("字典项不存在"));
        mockMvc.perform(get("/dict/item/options/user_status"))
                .andExpect(jsonPath("$.code").value(605002));

        // 9. 类型不存在时创建字典项：605002
        mockMvc.perform(post("/dict/item").contentType("application/json")
                        .content("""
                                {"dictType":"ghost_type","itemCode":"X","itemLabel":"幽灵","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(605002))
                .andExpect(jsonPath("$.message").value("字典不存在"));
    }

    @Test
    @Order(2)
    @DisplayName("业务校验：字典类型编码重复 605001；同字典内项编码重复 605003")
    void createDuplicates() throws Exception {
        mockMvc.perform(post("/dict/type").contentType("application/json")
                        .content("""
                                {"dictType":"dup_type","name":"重复字典","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/dict/type").contentType("application/json")
                        .content("""
                                {"dictType":"dup_type","name":"重复字典","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(605001))
                .andExpect(jsonPath("$.message").value("字典类型已存在"));

        mockMvc.perform(post("/dict/item").contentType("application/json")
                        .content("""
                                {"dictType":"dup_type","itemCode":"A","itemLabel":"选项A","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/dict/item").contentType("application/json")
                        .content("""
                                {"dictType":"dup_type","itemCode":"A","itemLabel":"选项A改","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(605003))
                .andExpect(jsonPath("$.message").value("字典项已存在"));
    }

    @Test
    @Order(3)
    @DisplayName("参数校验：必填缺失/命名规范/长度越界/负数排序返回 400 与明确错误信息")
    void createValidationFailures() throws Exception {
        // 字典编码必填缺失
        mockMvc.perform(post("/dict/type").contentType("application/json")
                        .content("""
                                {"name":"用户状态","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("字典编码不能为空"));

        // 字典编码命名规范：大写开头违反小写下划线约定
        mockMvc.perform(post("/dict/type").contentType("application/json")
                        .content("""
                                {"dictType":"UserStatus","name":"用户状态","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("字典编码须为小写下划线格式（如 user_status），以小写字母开头，仅含小写字母、数字、下划线"));

        // 字典项编码格式非法：含空格
        mockMvc.perform(post("/dict/item").contentType("application/json")
                        .content("""
                                {"dictType":"dup_type","itemCode":"bad code","itemLabel":"选项","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("项编码仅允许字母、数字、下划线、连字符"));

        // 字典项排序号为负
        mockMvc.perform(post("/dict/item").contentType("application/json")
                        .content("""
                                {"dictType":"dup_type","itemCode":"OK","itemLabel":"选项","sort":-1,"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("排序号不能为负数"));

        // 更新接口：字典项 itemLabel 为 null 必填校验
        mockMvc.perform(put("/dict/item/1").contentType("application/json")
                        .content("""
                                {"sort":1,"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("项显示名不能为 null"));

        // 路径参数：非法 id
        mockMvc.perform(get("/dict/type/0"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("字典类型 id 必须为正整数"));
    }
}
