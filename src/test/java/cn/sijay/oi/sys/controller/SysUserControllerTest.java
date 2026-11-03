package cn.sijay.oi.sys.controller;

import cn.sijay.oi.support.AuthenticatedIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户模块 CRUD 集成测试（真实 Spring 上下文 + 内存 H2 + MockMvc）。
 *
 * <p>环境说明：覆盖数据源为内存 H2（每上下文独立、无残留），schema 每次执行保证表结构。
 * 验证全链路：Bean Validation → 业务校验 → JPA 落库（含软删除与 @SQLRestriction）→ 全局响应格式。
 *
 * <p>登录态：继承 {@link AuthenticatedIntegrationTest}，全部请求自动携带种子超管
 * （admin，id=1）令牌；业务用户 id 通过分页接口回读，不硬编码自增 id。
 */
@DisplayName("用户模块 CRUD 集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:usercrud;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class SysUserControllerTest extends AuthenticatedIntegrationTest {

    private static final String CREATE_BODY = """
            {"username":"%s","password":"123456","nickname":"%s","mobile":"13800000000",
             "email":"%s@test.com","enabled":true,"remark":"测试用户"}
            """;

    /**
     * 通过分页接口回读指定用户名的用户 id（不硬编码自增 id）。
     */
    private String userIdByUsername(String username) throws Exception {
        MvcResult result = mockMvc.perform(get("/user/page").param("username", username))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        // 先赋值 Object 再转 String：直接 String.valueOf(JsonPath.read(...)) 会命中 valueOf(char[]) 重载
        Object id = JsonPath.read(result.getResponse().getContentAsString(), "$.data[0].id");
        return String.valueOf(id);
    }

    @Test
    @Order(1)
    @DisplayName("全链路：创建 → 详情 → 更新 → 分页（CONTAINING 模糊匹配）→ 软删除 → 不可见可重建")
    void fullCrudFlow() throws Exception {
        // 1. 创建：返回标准成功结构
        mockMvc.perform(post("/user").contentType("application/json")
                        .content(CREATE_BODY.formatted("zhangsan", "张三", "zs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 种子超管 admin 占用首个自增 id，业务用户 id 回读获取、不硬编码
        String zhangsanId = userIdByUsername("zhangsan");

        // 2. 详情：id 为字符串（全局 Long 转字符串），password 不外泄，时间为标准格式
        mockMvc.perform(get("/user/" + zhangsanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("zhangsan"))
                .andExpect(jsonPath("$.data.nickname").value("张三"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.id").value(zhangsanId))
                .andExpect(jsonPath("$.data.createTime").isNotEmpty());

        // 3. 更新档案：username/password 不在更新入参中，nickname/status 变更生效
        mockMvc.perform(put("/user/" + zhangsanId).contentType("application/json")
                        .content("""
                                {"nickname":"张三丰","mobile":"13900000000","email":"zsf@test.com",
                                 "enabled":false,"remark":"更新后"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/user/" + zhangsanId))
                .andExpect(jsonPath("$.data.nickname").value("张三丰"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.username").value("zhangsan"));

        // 4. 分页 CONTAINING 模糊匹配：zhang/zhangs/ha 均命中，li 不命中
        mockMvc.perform(post("/user").contentType("application/json")
                        .content(CREATE_BODY.formatted("zhangsi", "张四", "zsi")))
                .andExpect(jsonPath("$.code").value(200));
        String zhangsiId = userIdByUsername("zhangsi");
        mockMvc.perform(post("/user").contentType("application/json")
                        .content(CREATE_BODY.formatted("lisi", "李四", "ls")))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/user/page").param("username", "zhang"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.total").value("2"));
        mockMvc.perform(get("/user/page").param("username", "ha"))
                .andExpect(jsonPath("$.data", hasSize(2)));
        mockMvc.perform(get("/user/page").param("username", "lisi"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].username").value("lisi"));

        // status 枚举精确匹配
        mockMvc.perform(get("/user/page").param("enabled", "false"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].username").value("zhangsan"));

        // 默认分页：不指定排序，仅校验条数与总数（含种子 admin 共 4 人；顺序为数据库自然顺序，不做断言）
        mockMvc.perform(get("/user/page"))
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.total").value("4"));

        // 显式排序：sortField=email 升序（admin 的种子邮箱取排序末位，ls < zsf < zsi < zz），首条为 lisi
        mockMvc.perform(get("/user/page").param("sortField", "email").param("desc", "false"))
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[0].username").value("lisi"));

        // 5. 软删除：删除后详情不可见（601002），同名用户可重建（(username, deleted) 唯一）
        mockMvc.perform(delete("/user/" + zhangsiId))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/user/" + zhangsiId))
                .andExpect(jsonPath("$.code").value(601002))
                .andExpect(jsonPath("$.message").value("用户不存在"));
        mockMvc.perform(post("/user").contentType("application/json")
                        .content(CREATE_BODY.formatted("zhangsi", "张四新", "zsi2")))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/user/page").param("username", "zhangsi"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].nickname").value("张四新"));

        // 6. 删除/更新/查询不存在的 id：统一返回 601002
        mockMvc.perform(delete("/user/999"))
                .andExpect(jsonPath("$.code").value(601002));
        mockMvc.perform(put("/user/999").contentType("application/json")
                        .content("""
                                {"nickname":"x","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(601002));
    }

    @Test
    @Order(2)
    @DisplayName("业务校验：用户名重复返回 601001")
    void createDuplicateUsername() throws Exception {
        mockMvc.perform(post("/user").contentType("application/json")
                        .content(CREATE_BODY.formatted("dupuser", "重复", "dup")))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/user").contentType("application/json")
                        .content(CREATE_BODY.formatted("dupuser", "重复", "dup")))
                .andExpect(jsonPath("$.code").value(601001))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    @Order(3)
    @DisplayName("参数校验：必填缺失/格式非法/长度越界返回 400 与明确错误信息")
    void createValidationFailures() throws Exception {
        // 必填缺失：username
        mockMvc.perform(post("/user").contentType("application/json")
                        .content("""
                                {"password":"123456","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名不能为空"));

        // 必填缺失：password
        mockMvc.perform(post("/user").contentType("application/json")
                        .content("""
                                {"username":"abc","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码不能为空"));

        // 长度越界：password 不足 6 位
        mockMvc.perform(post("/user").contentType("application/json")
                        .content("""
                                {"username":"abc","password":"123","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码长度须在 6~100 位之间"));

        // 格式非法：username 含非法字符 / email 格式错误
        mockMvc.perform(post("/user").contentType("application/json")
                        .content("""
                                {"username":"bad name!","password":"123456","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名仅允许字母、数字、下划线"));

        mockMvc.perform(post("/user").contentType("application/json")
                        .content("""
                                {"username":"abc","password":"123456","email":"not-an-email","enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邮箱格式不正确"));

        // 枚举非法：status 值不在 NORMAL/DISABLED 内
        mockMvc.perform(post("/user").contentType("application/json")
                        .content("""
                                {"username":"abc","password":"123456","enabled":"WHATEVER"}
                                """))
                .andExpect(jsonPath("$.code").value(400));

        // 更新接口：nickname 为 null 必填校验
        mockMvc.perform(put("/user/1").contentType("application/json")
                        .content("""
                                {"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("昵称不能为 null"));

        // 路径参数：非法 id
        mockMvc.perform(get("/user/0"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户 id 必须为正整数"));
    }

    @Test
    @Order(4)
    @DisplayName("超管保护：删除种子超管（id=1）返回 601006，账号保持可用")
    void deleteAdminForbidden() throws Exception {
        // 超管是系统最后可用管理账号，删除会导致系统失去管理入口（回归测试 2026-11-03 发现）
        mockMvc.perform(delete("/user/1"))
                .andExpect(jsonPath("$.code").value(601006))
                .andExpect(jsonPath("$.message").value("不允许删除超级管理员账号"));

        // 保护生效：admin 仍可正常查询（未被软删）
        mockMvc.perform(get("/user/1"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }
}
