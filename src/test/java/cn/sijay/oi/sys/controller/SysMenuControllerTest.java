package cn.sijay.oi.sys.controller;

import cn.sijay.oi.support.AuthenticatedIntegrationTest;
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
 * 菜单模块 CRUD 集成测试（真实 Spring 上下文 + 内存 H2 + MockMvc）。
 *
 * <p>环境说明：覆盖数据源为内存 H2（每上下文独立、无残留），schema 每次执行保证表结构。
 * 验证全链路：Bean Validation → 层级业务校验 → JPA 落库（含软删除与 @SQLRestriction）
 * → 树形装配 → 全局响应格式。
 *
 * <p>登录态：继承 {@link AuthenticatedIntegrationTest}，全部请求自动携带种子超管令牌。
 */
@DisplayName("菜单模块 CRUD 集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:menucrud;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class SysMenuControllerTest extends AuthenticatedIntegrationTest {

    /**
     * 构造创建菜单请求体（parentId 传 null 时省略字段，默认挂根节点）。
     */
    private static String createBody(String name, String type, String parentId) {
        String parentField = parentId == null ? "" : "\"parentId\":" + parentId + ",";
        return """
                {%s"name":"%s","type":"%s","path":"/%s","component":"%s/index","perms":"%s:list",
                 "icon":"icon-%s","sort":1,"visible":true,"enabled":true}
                """.formatted(parentField, name, type, name, name, name, name);
    }

    @Test
    @Order(1)
    @DisplayName("全链路：创建目录/菜单/按钮 → 详情 → 更新 → 树形装配 → 分页 → 逐级软删除")
    void fullCrudFlow() throws Exception {
        // 1. 创建：目录（根）→ 菜单（挂目录）→ 按钮（挂菜单），层级逐级合法
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content(createBody("系统管理", "DIRECTORY", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content(createBody("用户管理", "MENU", "1")))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content(createBody("新增用户", "BUTTON", "2")))
                .andExpect(jsonPath("$.code").value(200));

        // 2. 详情：id/parentId 为字符串（全局 Long 转字符串），枚举名与中文描述同时输出
        mockMvc.perform(get("/menu/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("用户管理"))
                .andExpect(jsonPath("$.data.type").value("MENU"))
                .andExpect(jsonPath("$.data.typeDesc").value("菜单"))
                .andExpect(jsonPath("$.data.visible").value(true))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.id").value("2"))
                .andExpect(jsonPath("$.data.parentId").value("1"))
                .andExpect(jsonPath("$.data.createTime").isNotEmpty());

        // 3. 更新档案：parentId/type 不在更新入参中不可变更，name/visible/perms 变更生效
        mockMvc.perform(put("/menu/2").contentType("application/json")
                        .content("""
                                {"name":"用户管理(改)","path":"/user","component":"user/index",
                                 "perms":"user:edit","icon":"icon-user","sort":2,
                                 "visible":false,"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/menu/2"))
                .andExpect(jsonPath("$.data.name").value("用户管理(改)"))
                .andExpect(jsonPath("$.data.visible").value(false))
                .andExpect(jsonPath("$.data.sort").value(2))
                .andExpect(jsonPath("$.data.parentId").value("1"))
                .andExpect(jsonPath("$.data.type").value("MENU"));

        // 4. 树形接口：目录 → 菜单 → 按钮三级结构，id 为字符串
        mockMvc.perform(get("/menu/tree"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value("1"))
                .andExpect(jsonPath("$.data[0].label").value("系统管理"))
                .andExpect(jsonPath("$.data[0].parentId").doesNotExist())
                .andExpect(jsonPath("$.data[0].children", hasSize(1)))
                .andExpect(jsonPath("$.data[0].children[0].id").value("2"))
                .andExpect(jsonPath("$.data[0].children[0].children", hasSize(1)))
                .andExpect(jsonPath("$.data[0].children[0].children[0].id").value("3"))
                .andExpect(jsonPath("$.data[0].children[0].children[0].label").value("新增用户"));

        // 5. 分页 CONTAINING 模糊匹配："系统" 仅命中目录，"用户" 命中菜单与按钮
        mockMvc.perform(get("/menu/page").param("name", "系统"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.total").value("1"))
                .andExpect(jsonPath("$.data[0].type").value("DIRECTORY"));
        mockMvc.perform(get("/menu/page").param("name", "用户"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.total").value("2"));

        // type/status 枚举精确匹配
        mockMvc.perform(get("/menu/page").param("type", "BUTTON"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("新增用户"));
        mockMvc.perform(get("/menu/page").param("enabled", "false"))
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.total").value("0"));

        // 默认分页
        mockMvc.perform(get("/menu/page"))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.total").value("3"));

        // 6. 删除：按钮（叶子）可删；删除后菜单变叶子可删；目录最后删
        mockMvc.perform(delete("/menu/3"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(delete("/menu/2"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(delete("/menu/1"))
                .andExpect(jsonPath("$.code").value(200));

        // 软删后不可见（603001），软删行不计入分页
        mockMvc.perform(get("/menu/1"))
                .andExpect(jsonPath("$.code").value(603001))
                .andExpect(jsonPath("$.message").value("菜单不存在"));
        mockMvc.perform(get("/menu/page"))
                .andExpect(jsonPath("$.data", hasSize(0)));

        // 7. 删除/更新/查询不存在的 id：统一返回 603001
        mockMvc.perform(delete("/menu/999"))
                .andExpect(jsonPath("$.code").value(603001));
        mockMvc.perform(put("/menu/999").contentType("application/json")
                        .content("""
                                {"name":"x","sort":0,"visible":true,"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(603001));
    }

    @Test
    @Order(2)
    @DisplayName("业务校验：层级规则非法返回 603003，父不存在返回 603001，存在子菜单删除返回 603002")
    void createBusinessValidationFailures() throws Exception {
        // 准备：目录（根）→ 菜单。id 不硬编码（H2 IDENTITY 序列跨测试方法连续递增），
        // 通过树形接口回读实际 id
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content(createBody("权限管理", "DIRECTORY", null)))
                .andExpect(jsonPath("$.code").value(200));
        String rootId = treeId("$.data[0].id");
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content(createBody("角色管理", "MENU", rootId)))
                .andExpect(jsonPath("$.code").value(200));
        String menuId = treeId("$.data[0].children[0].id");

        // 非目录类型挂根节点：菜单/按钮不允许作为根
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content(createBody("根菜单", "MENU", null)))
                .andExpect(jsonPath("$.code").value(603003))
                .andExpect(jsonPath("$.message").value("父菜单层级关系非法"));

        // 父菜单不存在
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content(createBody("孤儿菜单", "MENU", "999")))
                .andExpect(jsonPath("$.code").value(603001))
                .andExpect(jsonPath("$.message").value("菜单不存在"));

        // 层级非法：按钮不能挂目录（父必须为菜单）
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content(createBody("按钮挂目录", "BUTTON", rootId)))
                .andExpect(jsonPath("$.code").value(603003));

        // 层级非法：菜单不能挂菜单（父必须为目录）
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content(createBody("菜单挂菜单", "MENU", menuId)))
                .andExpect(jsonPath("$.code").value(603003));

        // 层级非法：目录必须为根节点
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content(createBody("子目录", "DIRECTORY", rootId)))
                .andExpect(jsonPath("$.code").value(603003));

        // 存在子菜单禁止删除：先删目录（有子菜单）→ 603002；删菜单（叶子）→ 成功后目录可删
        mockMvc.perform(delete("/menu/" + rootId))
                .andExpect(jsonPath("$.code").value(603002))
                .andExpect(jsonPath("$.message").value("存在子菜单，无法删除"));
        mockMvc.perform(delete("/menu/" + menuId))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(delete("/menu/" + rootId))
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 从树形接口响应中读取节点 id（id 由全局 Jackson 配置序列化为字符串，原样返回）。
     *
     * @param path JSON 路径（如 {@code $.data[0].children[0].id}）
     * @return 节点 id 字符串
     */
    private String treeId(String path) throws Exception {
        MvcResult result = mockMvc.perform(get("/menu/tree"))
                                  .andExpect(jsonPath("$.code").value(200))
                                  .andReturn();
        Object id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
        return String.valueOf(id);
    }

    @Test
    @Order(3)
    @DisplayName("参数校验：必填缺失/长度越界/负值/非法枚举返回 400 与明确错误信息")
    void createValidationFailures() throws Exception {
        // 必填缺失：name
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content("""
                                {"type":"DIRECTORY","sort":0,"visible":true,"enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("菜单名称不能为空"));

        // 必填缺失：type
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content("""
                                {"name":"测试","sort":0,"visible":true,"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("菜单类型不能为空"));

        // 必填缺失：sort
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content("""
                                {"name":"测试","type":"DIRECTORY","visible":true,"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("排序号不能为 null"));

        // 数值范围：sort 为负数
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content("""
                                {"name":"测试","type":"DIRECTORY","sort":-1,"visible":true,"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("排序号不能为负数"));

        // 数值范围：parentId 为负数
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content("""
                                {"parentId":-1,"name":"测试","type":"DIRECTORY","sort":0,
                                 "visible":true,"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("父菜单 id 必须为非负整数（0 表示根节点）"));

        // 长度越界：name 超过 50 字符
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content("""
                                {"name":"%s","type":"DIRECTORY","sort":0,"visible":true,"enabled":true}
                                """.formatted("长".repeat(51))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("菜单名称最长 50 字符"));

        // 枚举非法：type 值不在 DIRECTORY/MENU/BUTTON 内
        mockMvc.perform(post("/menu").contentType("application/json")
                        .content("""
                                {"name":"测试","type":"WIDGET","sort":0,"visible":true,"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400));

        // 更新接口：name 为 null 必填校验
        mockMvc.perform(put("/menu/1").contentType("application/json")
                        .content("""
                                {"sort":0,"visible":true,"enabled":true}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("菜单名称不能为 null"));

        // 路径参数：非法 id
        mockMvc.perform(get("/menu/0"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("菜单 id 必须为正整数"));
    }
}
