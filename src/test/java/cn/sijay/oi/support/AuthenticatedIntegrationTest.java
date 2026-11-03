package cn.sijay.oi.support;

import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet;
import cn.sijay.oi.sys.entity.SysUser;
import cn.sijay.oi.sys.repository.SysUserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 需要登录态的 MockMvc 集成测试基类。
 *
 * <p>接入全局鉴权后，所有业务接口默认要求登录态；本基类为子类提供开箱即用的
 * 认证测试环境：
 * <ol>
 *   <li>种子超管账号：确保 {@code sys_user} 存在 username=admin 的用户
 *       （密码 123456，BCrypt 落库），每个测试上下文独立、幂等；</li>
 *   <li>构建带令牌的 {@link #mockMvc}：以 admin 登录获取令牌，通过
 *       {@code defaultRequest} 为子类的<b>全部</b>请求自动附加 {@code satoken} 头
 *       （子类请求显式携带同名头时以子类为准）；</li>
 *   <li>{@link #rawMockMvc}：不带默认令头的实例，供"未登录访问返回 401"类场景使用。</li>
 * </ol>
 *
 * <p>注意：admin 为每上下文首个插入用户，id 固定为 1（超管判定依据）；
 * 业务测试若再创建用户/数据，不得硬编码自增 id，应通过分页接口回读。
 *
 * @author sijay
 */
public abstract class AuthenticatedIntegrationTest {

    /**
     * 种子超管的登录名；同时是幂等判据（已存在则复用，不重复创建）。
     */
    protected static final String ADMIN_USERNAME = "admin";

    /**
     * 种子超管的登录密码（明文）；落库前经 BCrypt 加密。
     */
    protected static final String ADMIN_PASSWORD = "123456";

    /**
     * sa-token 令牌请求头名；与 application.yaml 的 sa-token.token-name 一致。
     */
    protected static final String TOKEN_HEADER = "satoken";

    @Autowired
    protected WebApplicationContext webApplicationContext;

    @Autowired
    private SysUserRepository userRepository;

    /**
     * 已登录 admin 的 MockMvc；子类全部请求自动携带 satoken 头。
     */
    protected MockMvc mockMvc;

    /**
     * 不带默认令头的 MockMvc；用于未登录访问（401）等场景。
     */
    protected MockMvc rawMockMvc;

    /**
     * 种子超管的用户 id；每上下文首个插入用户，固定为 1。
     */
    protected Long adminId;

    /**
     * 初始化认证环境：种子超管 → 登录取令牌 → 构建带默认令头的 mockMvc。
     *
     * <p>实现说明：两个 MockMvc 实例都必须显式添加
     * {@link SaTokenContextFilterForJakartaServlet}——sa-token 1.46 的上下文由该
     * Servlet Filter 激活（生产环境由 SaTokenContextRegister 自动注册），
     * 而 MockMvc webAppContextSetup 不会应用 Filter，缺失时登录/校验全部抛
     * SaTokenContextException。
     */
    @BeforeEach
    void setUpAuthenticatedMockMvc() throws Exception {
        adminId = ensureAdminUser();
        rawMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(new SaTokenContextFilterForJakartaServlet())
                .build();
        String token = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(new SaTokenContextFilterForJakartaServlet())
                .defaultRequest(get("/").header(TOKEN_HEADER, token))
                .build();
    }

    /**
     * 通过登录接口为指定账号获取令牌（走完整 HTTP 链路）。
     *
     * @param username 登录名
     * @param password 登录密码（明文）
     * @return sa-token 令牌值
     */
    protected String login(String username, String password) throws Exception {
        MvcResult result = rawMockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        // 先赋值 Object 再转 String：直接 String.valueOf(JsonPath.read(...)) 会命中 valueOf(char[]) 重载
        Object tokenValue = JsonPath.read(body, "$.data.tokenValue");
        return String.valueOf(tokenValue);
    }

    /**
     * 确保种子超管存在并返回其 id（幂等：已存在则直接复用）。
     */
    private Long ensureAdminUser() {
        return userRepository.findByUsername(ADMIN_USERNAME)
                .map(SysUser::getId)
                .orElseGet(() -> {
                    PasswordEncoder encoder = new BCryptPasswordEncoder();
                    SysUser admin = new SysUser();
                    admin.setUsername(ADMIN_USERNAME);
                    admin.setPassword(encoder.encode(ADMIN_PASSWORD));
                    admin.setNickname("管理员");
                    // 邮箱取排序末位值：保证子测试按 email 升序分页时 admin 稳定排最后（NULL 排序在不同库中不确定）
                    admin.setEmail("zz@test.com");
                    admin.setEnabled(true);
                    return userRepository.save(admin).getId();
                });
    }
}
