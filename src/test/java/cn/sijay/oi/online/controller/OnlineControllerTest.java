package cn.sijay.oi.online.controller;

import cn.sijay.oi.support.AuthenticatedIntegrationTest;
import cn.sijay.oi.sys.entity.SysLog;
import cn.sijay.oi.sys.entity.SysRole;
import cn.sijay.oi.sys.entity.SysUser;
import cn.sijay.oi.sys.entity.SysUserRole;
import cn.sijay.oi.sys.repository.SysLogRepository;
import cn.sijay.oi.sys.repository.SysRoleRepository;
import cn.sijay.oi.sys.repository.SysUserRepository;
import cn.sijay.oi.sys.repository.SysUserRoleRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 在线用户模块集成测试，覆盖权限隔离与强制下线全场景。
 *
 * <p>覆盖场景（与在线用户管理需求一一对应）：
 * <ol>
 *   <li>在线列表：超管可查询，包含登录人（用户名/昵称/IP/登录时间/角色）；</li>
 *   <li>实时性：新用户登录后出现在列表；被踢/登出后从列表消失；</li>
 *   <li>强制下线：目标令牌立即失效（401）、幂等（重复踢不报错）、操作写入 sys_log 留痕；</li>
 *   <li>权限隔离：普通用户访问列表/踢人接口均返回 403（防越权），未登录返回 401。</li>
 * </ol>
 *
 * <p>登录态：继承 {@link AuthenticatedIntegrationTest}；默认请求携带超管令牌，
 * 需要"他人令牌"或"无令牌"的场景显式携带/使用 {@link #rawMockMvc}。
 */
@DisplayName("在线用户模块集成测试（实时列表 + 强制下线 + 权限隔离）")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:online;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class OnlineControllerTest extends AuthenticatedIntegrationTest {

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysUserRoleRepository userRoleRepository;

    @Autowired
    private SysLogRepository logRepository;

    /**
     * 普通业务用户的登录名/密码。
     */
    private static final String NORMAL_USERNAME = "onlineuser";

    private static final String NORMAL_PASSWORD = "123456";

    /**
     * 测试专用角色编码（跨测试方法幂等复用）。
     */
    private static final String VIEWER_ROLE_CODE = "online_viewer";

    /**
     * 确保普通业务用户存在（幂等：跨 @Order 测试方法复用，不重复创建——
     * sys_user 按 (username, deleted) 唯一，重复 save 会导致唯一键冲突或脏数据），
     * 并保证其绑定测试角色（用于列表的角色名回显）。
     *
     * @return 用户 id
     */
    private long createNormalUser() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        long userId = userRepository.findByUsername(NORMAL_USERNAME)
                .map(SysUser::getId)
                .orElseGet(() -> {
                    SysUser user = new SysUser();
                    user.setUsername(NORMAL_USERNAME);
                    user.setPassword(encoder.encode(NORMAL_PASSWORD));
                    user.setNickname("在线用户");
                    user.setEnabled(true);
                    return userRepository.save(user).getId();
                });
        // 绑定一个角色（验证列表的角色名回显）；已绑定则跳过
        if (userRoleRepository.findByUserId(userId).isEmpty()) {
            SysRole role = roleRepository.findAll().stream()
                    .filter(r -> VIEWER_ROLE_CODE.equals(r.getCode()))
                    .findFirst()
                    .orElseGet(() -> {
                        SysRole created = new SysRole();
                        created.setCode(VIEWER_ROLE_CODE);
                        created.setName("访客角色");
                        created.setSort(1);
                        created.setEnabled(true);
                        return roleRepository.save(created);
                    });
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(role.getId());
            userRoleRepository.save(userRole);
        }
        return userId;
    }

    /**
     * 以指定账号登录并返回令牌（走完整 HTTP 链路，登录 IP 写入 Token-Session）。
     */
    private String loginAs(String username, String password) throws Exception {
        MvcResult result = rawMockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Object tokenValue = JsonPath.read(result.getResponse().getContentAsString(), "$.data.tokenValue");
        return String.valueOf(tokenValue);
    }

    @Test
    @Order(1)
    @DisplayName("在线列表：超管可查询，包含登录人（昵称/角色/IP/登录时间齐全）")
    void listContainsLoggedInUsers() throws Exception {
        long userId = createNormalUser();
        loginAs(NORMAL_USERNAME, NORMAL_PASSWORD);

        MvcResult result = mockMvc.perform(get("/online/list"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        // JsonPath.read 直接按目标泛型接住返回值，避免通配符捕获问题
        List<String> names = JsonPath.read(body, "$.data[*].username");
        assertThat(names).contains("admin", NORMAL_USERNAME);

        // 普通用户的字段回显：角色名/IP/登录时间
        List<String> rolesList = JsonPath.read(body, "$.data[?(@.username == '" + NORMAL_USERNAME + "')].roles");
        assertThat(rolesList).hasSize(1);
        assertThat(rolesList.getFirst()).contains("访客角色");
        List<String> ipList = JsonPath.read(body, "$.data[?(@.username == '" + NORMAL_USERNAME + "')].loginIp");
        assertThat(ipList.getFirst()).isNotBlank();
        List<String> timeList = JsonPath.read(body, "$.data[?(@.username == '" + NORMAL_USERNAME + "')].loginTime");
        assertThat(timeList.getFirst()).isNotBlank();
        // onlineMinutes 为非负数（Long 经全局 Jackson 配置序列化为字符串）
        List<String> minutesList = JsonPath.read(body, "$.data[?(@.username == '" + NORMAL_USERNAME + "')].onlineMinutes");
        assertThat(Integer.parseInt(minutesList.getFirst())).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Order(2)
    @DisplayName("权限隔离：普通用户访问列表/踢人接口均 403，未登录访问 401")
    void accessIsolation() throws Exception {
        createNormalUser();
        String userToken = loginAs(NORMAL_USERNAME, NORMAL_PASSWORD);

        // 普通用户：列表 403（仅系统管理员可见）
        rawMockMvc.perform(get("/online/list").header("satoken", userToken))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("仅系统管理员可查看在线用户"));

        // 普通用户：踢人 403（防越权操作）
        rawMockMvc.perform(post("/online/kick/1").header("satoken", userToken))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("仅系统管理员可执行强制下线操作"));

        // 未登录：401
        rawMockMvc.perform(get("/online/list"))
                .andExpect(jsonPath("$.code").value(401));
        rawMockMvc.perform(post("/online/kick/1"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @Order(3)
    @DisplayName("强制下线：目标令牌立即失效、列表实时移除、操作写入 sys_log 留痕、重复踢幂等")
    void kickFlow() throws Exception {
        long userId = createNormalUser();
        String userToken = loginAs(NORMAL_USERNAME, NORMAL_PASSWORD);

        // 被踢前：目标令牌有效（可访问受保护接口）
        rawMockMvc.perform(get("/auth/profile").header("satoken", userToken))
                .andExpect(jsonPath("$.code").value(200));

        // 超管执行强制下线 → 200
        mockMvc.perform(post("/online/kick/" + userId))
                .andExpect(jsonPath("$.code").value(200));

        // 被踢后：目标令牌立即失效（401，前端据此跳转登录页）
        rawMockMvc.perform(get("/auth/profile").header("satoken", userToken))
                .andExpect(jsonPath("$.code").value(401));

        // 列表实时同步：目标用户已从在线列表消失（仅剩 admin）
        MvcResult result = mockMvc.perform(get("/online/list"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        List<String> names = JsonPath.read(result.getResponse().getContentAsString(), "$.data[*].username");
        assertThat(names).contains("admin").doesNotContain(NORMAL_USERNAME);

        // 操作留痕：sys_log 新增一条 kick 成功记录（module=online）
        List<SysLog> logs = logRepository.findAll().stream()
                .filter(log -> "online".equals(log.getModule()) && "kick".equals(log.getAction()))
                .toList();
        assertThat(logs).hasSize(1);
        SysLog kickLog = logs.getFirst();
        assertThat(kickLog.getResult()).isEqualTo("SUCCESS");
        assertThat(kickLog.getUsername()).isEqualTo("admin");
        assertThat(kickLog.getIp()).isNotBlank();
        assertThat(kickLog.getParams()).contains(String.valueOf(userId));

        // 幂等：重复踢同一目标（已离线）不报错
        mockMvc.perform(post("/online/kick/" + userId))
                .andExpect(jsonPath("$.code").value(200));
    }
}
