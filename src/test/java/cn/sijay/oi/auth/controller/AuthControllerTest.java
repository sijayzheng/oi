package cn.sijay.oi.auth.controller;

import cn.sijay.oi.support.AuthenticatedIntegrationTest;
import cn.sijay.oi.sys.entity.SysMenu;
import cn.sijay.oi.sys.entity.SysRole;
import cn.sijay.oi.sys.entity.SysUser;
import cn.sijay.oi.sys.entity.SysUserRole;
import cn.sijay.oi.sys.enums.MenuType;
import cn.sijay.oi.sys.repository.SysMenuRepository;
import cn.sijay.oi.sys.repository.SysRoleMenuRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证模块集成测试（真实 Spring 上下文 + 内存 H2 + MockMvc）。
 *
 * <p>覆盖场景（与鉴权安全需求一一对应）：
 * <ol>
 *   <li>正常登录：校验通过返回令牌对，令牌可访问受保护接口；</li>
 *   <li>登录失败：密码错误/用户不存在（601003，不区分防枚举）、账号禁用（601004）、参数校验（400）；</li>
 *   <li>未登录访问：受保护业务接口与踢人接口均返回 401；</li>
 *   <li>普通用户越权：非超管调用踢人接口返回 403；</li>
 *   <li>超管踢人：踢下线成功，被踢用户令牌立即失效（后续请求 401）。</li>
 * </ol>
 *
 * <p>登录态：继承 {@link AuthenticatedIntegrationTest}；默认请求携带超管令牌，
 * 需要"无令牌"或"他人令牌"的场景显式覆盖请求头或使用 {@link #rawMockMvc}。
 */
@DisplayName("认证模块集成测试（登录 + 鉴权 + 踢人下线）")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class AuthControllerTest extends AuthenticatedIntegrationTest {

    @Autowired
    private SysMenuRepository menuRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysUserRoleRepository userRoleRepository;

    @Autowired
    private SysRoleMenuRepository roleMenuRepository;

    @Autowired
    private SysUserRepository userRepository;

    /**
     * 普通业务用户的登录名/密码；跨测试方法复用（Order 4 创建，Order 5 被踢）。
     */
    private static final String NORMAL_USERNAME = "normaluser";

    private static final String NORMAL_PASSWORD = "123456";

    /**
     * 创建普通业务用户（走受保护接口，默认头即超管令牌）。
     *
     * @return 用户 id（字符串形态，与响应中 Long→string 约定一致）
     */
    private String createNormalUser(String username) throws Exception {
        mockMvc.perform(post("/user").contentType("application/json")
                        .content("""
                                {"username":"%s","password":"%s","nickname":"普通用户","enabled":true}
                                """.formatted(username, NORMAL_PASSWORD)))
                .andExpect(jsonPath("$.code").value(200));
        MvcResult result = mockMvc.perform(get("/user/page").param("username", username))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        // 先赋值 Object 再转 String：直接 String.valueOf(JsonPath.read(...)) 会命中 valueOf(char[]) 重载
        Object id = JsonPath.read(result.getResponse().getContentAsString(), "$.data[0].id");
        return String.valueOf(id);
    }

    @Test
    @Order(1)
    @DisplayName("正常登录：返回令牌对，令牌可访问受保护接口")
    void loginSuccessAndTokenUsable() throws Exception {
        MvcResult result = rawMockMvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.tokenName").value("satoken"))
                .andExpect(jsonPath("$.data.tokenValue").value(not(emptyString())))
                .andReturn();
        // 先赋值 Object 再转 String：直接 String.valueOf(JsonPath.read(...)) 会命中 valueOf(char[]) 重载
        Object tokenValue = JsonPath.read(result.getResponse().getContentAsString(), "$.data.tokenValue");
        String token = String.valueOf(tokenValue);

        // 登录拿到的令牌可访问受保护接口（证明令牌真实有效）
        rawMockMvc.perform(get("/user/page").header("satoken", token))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(2)
    @DisplayName("登录失败：密码错误/用户不存在返回 601003，禁用账号返回 601004，缺参返回 400")
    void loginBusinessFailures() throws Exception {
        // 密码错误：601003（与用户不存在同一文案，防账号枚举）
        rawMockMvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"username\":\"%s\",\"password\":\"wrong-password\"}".formatted(ADMIN_USERNAME)))
                .andExpect(jsonPath("$.code").value(601003))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));

        // 用户不存在：同样 601003
        rawMockMvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"username\":\"ghost_user\",\"password\":\"123456\"}"))
                .andExpect(jsonPath("$.code").value(601003))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));

        // 账号禁用：创建 DISABLED 用户后登录 → 601004
        mockMvc.perform(post("/user").contentType("application/json")
                        .content("""
                                {"username":"disableduser","password":"123456","nickname":"被禁用","enabled":false}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        rawMockMvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"username\":\"disableduser\",\"password\":\"123456\"}"))
                .andExpect(jsonPath("$.code").value(601004))
                .andExpect(jsonPath("$.message").value("账号已禁用"));

        // 参数校验：username 缺失 → 400
        rawMockMvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"password\":\"123456\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名不能为空"));

        // 参数校验：password 缺失 → 400
        rawMockMvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"username\":\"admin\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码不能为空"));
    }

    @Test
    @Order(3)
    @DisplayName("未登录访问：受保护业务接口与踢人接口统一返回 401")
    void protectedApiRequiresLogin() throws Exception {
        // 业务接口：无令牌 → 401（HTTP 200 + code=401，与项目 Result 约定一致）
        rawMockMvc.perform(get("/user/page"))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录状态已失效"));

        // 伪造令牌 → 401
        rawMockMvc.perform(get("/user/page").header("satoken", "forged-token-value"))
                .andExpect(jsonPath("$.code").value(401));

        // 踢人接口同样受登录态保护：无令牌 → 401
        rawMockMvc.perform(post("/auth/kick/1"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @Order(4)
    @DisplayName("越权防护：普通用户调用踢人接口返回 403")
    void normalUserKickForbidden() throws Exception {
        createNormalUser(NORMAL_USERNAME);
        String normalToken = login(NORMAL_USERNAME, NORMAL_PASSWORD);

        // 已登录（令牌有效）但非超管 → 403
        rawMockMvc.perform(post("/auth/kick/" + adminId).header("satoken", normalToken))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("仅管理员可执行踢人下线操作"));

        // 403 后普通用户令牌仍然有效（未被误踢）；用户管理接口经 @SaCheckRole 保护，普通用户 403（R-01）
        rawMockMvc.perform(get("/user/page").header("satoken", normalToken))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("暂无权限执行该操作"));
    }

    @Test
    @Order(5)
    @DisplayName("超管踢人：踢下线成功，被踢用户令牌立即失效（后续请求 401）")
    void adminKickInvalidatesTargetToken() throws Exception {
        // 创建并登录目标用户，持有其"被踢前"的令牌
        String targetUsername = NORMAL_USERNAME + "_second";
        String targetUserId = createNormalUser(targetUsername);
        String targetToken = login(targetUsername, NORMAL_PASSWORD);

        // 超管（默认头）踢目标用户 → 200
        mockMvc.perform(post("/auth/kick/" + targetUserId))
                .andExpect(jsonPath("$.code").value(200));

        // 被踢用户持原令牌访问受保护接口 → 401（令牌已作废）
        rawMockMvc.perform(get("/user/page").header("satoken", targetToken))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录状态已失效"));

        // 被踢用户重新登录后可再次访问（封禁的是令牌而非账号；/user/page 受 @SaCheckRole 保护返回 403）
        String reLoginToken = login(targetUsername, NORMAL_PASSWORD);
        rawMockMvc.perform(get("/user/page").header("satoken", reLoginToken))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("暂无权限执行该操作"));
    }

    @Test
    @Order(6)
    @DisplayName("菜单权限树：超管返回全量菜单，路由字段完整输出")
    void adminMenusFullTree() throws Exception {
        // 种子：目录 + 两个菜单（同 sort 时按 id 升序稳定排序）
        SysMenu directory = saveMenu(null, "系统管理", MenuType.DIRECTORY, "/system", null, "Setting", 1);
        saveMenu(directory.getId(), "用户管理", MenuType.MENU, "/system/user", "system/UserView", "User", 1);
        saveMenu(directory.getId(), "参数配置", MenuType.MENU, "/system/config", "system/ConfigView", "Setting", 2);

        MvcResult result = mockMvc.perform(get("/auth/menus"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String body = result.getResponse().getContentAsString();

        // 超管：根节点为目录，字段（path/component/icon/visible）完整输出
        org.junit.jupiter.api.Assertions.assertEquals(1, ((List<?>) JsonPath.read(body, "$.data")).size());
        org.junit.jupiter.api.Assertions.assertEquals("系统管理", JsonPath.read(body, "$.data[0].name"));
        org.junit.jupiter.api.Assertions.assertEquals("DIRECTORY", JsonPath.read(body, "$.data[0].type"));
        org.junit.jupiter.api.Assertions.assertEquals("/system", JsonPath.read(body, "$.data[0].path"));
        org.junit.jupiter.api.Assertions.assertEquals("Setting", JsonPath.read(body, "$.data[0].icon"));
        org.junit.jupiter.api.Assertions.assertEquals(2, ((List<?>) JsonPath.read(body, "$.data[0].children")).size());
        org.junit.jupiter.api.Assertions.assertEquals("用户管理", JsonPath.read(body, "$.data[0].children[0].name"));
        org.junit.jupiter.api.Assertions.assertEquals("MENU", JsonPath.read(body, "$.data[0].children[0].type"));
        org.junit.jupiter.api.Assertions.assertEquals("/system/user", JsonPath.read(body, "$.data[0].children[0].path"));
        org.junit.jupiter.api.Assertions.assertEquals("system/UserView", JsonPath.read(body, "$.data[0].children[0].component"));
    }

    @Test
    @Order(7)
    @DisplayName("菜单权限过滤：角色关联菜单为唯一数据源，DISABLED 子树剪枝")
    void roleFilteredMenus() throws Exception {
        // 种子：目录 + 正常菜单 + DISABLED 菜单（同挂目录下）
        SysMenu directory = saveMenu(null, "业务应用", MenuType.DIRECTORY, "/apps", null, "Files", 2);
        SysMenu notice = saveMenu(directory.getId(), "通知公告", MenuType.MENU, "/sys/notice", "sys/NoticeView", "Bell", 1);
        SysMenu hidden = saveMenu(directory.getId(), "停用菜单", MenuType.MENU, "/apps/hidden", "system/ConfigView", "Hide", 2);
        menuRepository.findById(hidden.getId()).ifPresent(menu -> {
            menu.setEnabled(false);
            menuRepository.save(menu);
        });

        // 角色：授权目录 + 通知公告 + 停用菜单（停用项应被剪枝）
        SysRole viewerRole = new SysRole();
        viewerRole.setCode("menu-viewer");
        viewerRole.setName("菜单验证角色");
        viewerRole.setEnabled(true);
        roleRepository.save(viewerRole);
        roleMenuRepository.save(new cn.sijay.oi.sys.entity.SysRoleMenu(viewerRole.getId(), directory.getId()));
        roleMenuRepository.save(new cn.sijay.oi.sys.entity.SysRoleMenu(viewerRole.getId(), notice.getId()));
        roleMenuRepository.save(new cn.sijay.oi.sys.entity.SysRoleMenu(viewerRole.getId(), hidden.getId()));

        // 用户：挂 viewer 角色
        String username = "menuuser";
        saveUser(username, viewerRole.getId());
        String token = login(username, NORMAL_PASSWORD);

        // 该用户菜单树：仅授权目录及其下正常菜单；未授权的"系统管理"不可见；停用菜单被剪枝
        MvcResult result = rawMockMvc.perform(get("/auth/menus").header("satoken", token))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(1, ((List<?>) JsonPath.read(body, "$.data")).size());
        org.junit.jupiter.api.Assertions.assertEquals("业务应用", JsonPath.read(body, "$.data[0].name"));
        org.junit.jupiter.api.Assertions.assertEquals(1, ((List<?>) JsonPath.read(body, "$.data[0].children")).size());
        org.junit.jupiter.api.Assertions.assertEquals("通知公告", JsonPath.read(body, "$.data[0].children[0].name"));
    }

    @Test
    @Order(50)
    @DisplayName("菜单权限过滤：visible=false 隐藏菜单整棵子树不下发（不渲染、不参与权限计算）")
    void hiddenMenusPrunedFromTree() throws Exception {
        // 种子：目录 + 可见菜单 + 隐藏菜单（visible=false，同挂目录下）
        SysMenu directory = saveMenu(null, "隐藏验证目录", MenuType.DIRECTORY, "/hidden-test", null, "View", 9);
        SysMenu shown = saveMenu(directory.getId(), "可见菜单", MenuType.MENU, "/hidden-test/shown", "system/ConfigView", "View", 1);
        SysMenu masked = saveMenu(directory.getId(), "隐藏菜单", MenuType.MENU, "/hidden-test/masked", "system/ConfigView", "Hide", 2);
        menuRepository.findById(masked.getId()).ifPresent(menu -> {
            menu.setVisible(false);
            menuRepository.save(menu);
        });

        // 角色：授权目录 + 可见菜单 + 隐藏菜单（隐藏项应被剪枝）
        SysRole role = new SysRole();
        role.setCode("hidden-viewer");
        role.setName("隐藏验证角色");
        role.setEnabled(true);
        roleRepository.save(role);
        roleMenuRepository.save(new cn.sijay.oi.sys.entity.SysRoleMenu(role.getId(), directory.getId()));
        roleMenuRepository.save(new cn.sijay.oi.sys.entity.SysRoleMenu(role.getId(), shown.getId()));
        roleMenuRepository.save(new cn.sijay.oi.sys.entity.SysRoleMenu(role.getId(), masked.getId()));

        String username = "hiddenuser";
        saveUser(username, role.getId());
        String token = login(username, NORMAL_PASSWORD);

        // 隐藏菜单整棵子树剪枝：仅授权目录及其下可见菜单下发
        MvcResult result = rawMockMvc.perform(get("/auth/menus").header("satoken", token))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(1, ((List<?>) JsonPath.read(body, "$.data")).size());
        org.junit.jupiter.api.Assertions.assertEquals("隐藏验证目录", JsonPath.read(body, "$.data[0].name"));
        org.junit.jupiter.api.Assertions.assertEquals(1, ((List<?>) JsonPath.read(body, "$.data[0].children")).size());
        org.junit.jupiter.api.Assertions.assertEquals("可见菜单", JsonPath.read(body, "$.data[0].children[0].name"));
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, JsonPath.read(body, "$.data[0].children[0].visible"));
    }

    @Test
    @Order(8)
    @DisplayName("菜单权限过滤：无角色用户返回空菜单树（仅有登录页与 404 可达）")
    void noRoleUserEmptyMenus() throws Exception {
        saveUser("noroleuser", null);
        String token = login("noroleuser", NORMAL_PASSWORD);
        rawMockMvc.perform(get("/auth/menus").header("satoken", token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @Order(9)
    @DisplayName("个人资料：回显与修改生效，昵称必填校验返回 400")
    void profileGetAndUpdate() throws Exception {
        // 回显：当前登录人（admin）资料，不含密码
        mockMvc.perform(get("/auth/profile"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        // 修改资料：昵称/手机/邮箱/头像生效（avatar 语义为统一文件服务的文件 id，只存 id 不存 URL）
        mockMvc.perform(put("/auth/profile").contentType("application/json")
                        .content("""
                                {"nickname":"管理员(改)","mobile":"13800138000","email":"admin@oi.com","avatar":"1001"}
                                """))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("管理员(改)"))
                .andExpect(jsonPath("$.data.mobile").value("13800138000"))
                .andExpect(jsonPath("$.data.avatar").value("1001"));

        // 邮箱格式非法 → 400
        mockMvc.perform(put("/auth/profile").contentType("application/json")
                        .content("""
                                {"nickname":"x","email":"not-an-email"}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邮箱格式不正确"));

        // 昵称缺失 → 400
        mockMvc.perform(put("/auth/profile").contentType("application/json")
                        .content("""
                                {"mobile":"13800138000"}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("昵称不能为空"));
    }

    @Test
    @Order(10)
    @DisplayName("修改密码：原密码错误 601005、不一致 400、强度不足 400，成功后旧会话注销需重新登录")
    void changePasswordFlow() throws Exception {
        // 独立用户执行改密，避免影响种子 admin 的登录态
        saveUser("pwduser", null);
        String token = login("pwduser", NORMAL_PASSWORD);

        // 原密码错误 → 601005
        rawMockMvc.perform(post("/auth/password").header("satoken", token).contentType("application/json")
                        .content("""
                                {"oldPassword":"wrong-old","newPassword":"newpass123","confirmPassword":"newpass123"}
                                """))
                .andExpect(jsonPath("$.code").value(601005))
                .andExpect(jsonPath("$.message").value("原密码错误"));

        // 两次新密码不一致 → 400
        rawMockMvc.perform(post("/auth/password").header("satoken", token).contentType("application/json")
                        .content("""
                                {"oldPassword":"%s","newPassword":"newpass123","confirmPassword":"newpass456"}
                                """.formatted(NORMAL_PASSWORD)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("两次输入的密码不一致"));

        // 强度不足（纯数字）→ 400
        rawMockMvc.perform(post("/auth/password").header("satoken", token).contentType("application/json")
                        .content("""
                                {"oldPassword":"%s","newPassword":"123456","confirmPassword":"123456"}
                                """.formatted(NORMAL_PASSWORD)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("新密码须同时包含字母和数字"));

        // 正确修改 → 200；旧会话已注销（旧令牌 401）；新密码可重新登录
        rawMockMvc.perform(post("/auth/password").header("satoken", token).contentType("application/json")
                        .content("""
                                {"oldPassword":"%s","newPassword":"newpass123","confirmPassword":"newpass123"}
                                """.formatted(NORMAL_PASSWORD)))
                .andExpect(jsonPath("$.code").value(200));
        rawMockMvc.perform(get("/auth/profile").header("satoken", token))
                .andExpect(jsonPath("$.code").value(401));
        String newToken = login("pwduser", "newpass123");
        rawMockMvc.perform(get("/auth/profile").header("satoken", newToken))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(11)
    @DisplayName("头像上传：委托统一文件服务返回文件 id 与签名访问地址，非法扩展名返回 606002")
    void avatarUpload() throws Exception {
        // PNG 图片字节（1x1 透明 PNG）
        byte[] pngBytes = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/auth/avatar")
                                .file(new org.springframework.mock.web.MockMultipartFile(
                                        "file", "avatar.png", "image/png", pngBytes)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.businessKey").value("avatar"))
                .andExpect(jsonPath("$.data.accessUrl").isNotEmpty())
                .andReturn();
        // 返回文件唯一 id（业务侧只持久化 id，不存 URL/路径）；先赋值 Object 再转 String：
        // 直接 String.valueOf(JsonPath.read(...)) 会命中 valueOf(char[]) 重载
        Object idValue = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        Long fileId = Long.valueOf(String.valueOf(idValue));
        org.junit.jupiter.api.Assertions.assertNotNull(fileId);
        // 访问地址为带签名的公开端点，凭地址可直接取流
        Object urlValue = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessUrl");
        String accessUrl = String.valueOf(urlValue);
        org.junit.jupiter.api.Assertions.assertTrue(accessUrl.startsWith("/file/access/" + fileId + "?expires="));
        mockMvc.perform(get(accessUrl))
                .andExpect(status().isOk());

        // 非法扩展名 → 606002（统一文件服务错误码）
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/auth/avatar")
                                .file(new org.springframework.mock.web.MockMultipartFile(
                                        "file", "avatar.txt", "text/plain", "text".getBytes())))
                .andExpect(jsonPath("$.code").value(606002))
                .andExpect(jsonPath("$.message").value("不支持的文件类型"));
    }

    @Test
    @Order(12)
    @DisplayName("登录锁定：连续失败 5 次触发锁定，锁定期间正确密码也被拒绝（601007）")
    void loginLockoutAfterConsecutiveFailures() throws Exception {
        String lockUsername = NORMAL_USERNAME + "_lock";
        createNormalUser(lockUsername);

        // 前 4 次失败仍返回 601003（未达阈值）
        for (int i = 0; i < 4; i++) {
            rawMockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"%s\",\"password\":\"bad-pass\"}".formatted(lockUsername)))
                    .andExpect(jsonPath("$.code").value(601003));
        }
        // 第 5 次失败触发锁定（本次仍按凭据错误返回，状态自本次起进入锁定期）
        rawMockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"bad-pass\"}".formatted(lockUsername)))
                .andExpect(jsonPath("$.code").value(601003));

        // 锁定期间：即使密码正确也被拒绝（601007 + 明确的锁定提示文案）
        rawMockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(lockUsername, NORMAL_PASSWORD)))
                .andExpect(jsonPath("$.code").value(601007))
                .andExpect(jsonPath("$.message").value("登录失败次数过多，账号已锁定，请约 10 分钟后重试"));
    }

    @Test
    @Order(13)
    @DisplayName("国密登录：公钥下发格式校验 + SM2 密文登录成功 + 错误 keyId/篡改密文返回 601008")
    void sm2LoginFlow() throws Exception {
        // 1. 公钥下发：公开接口（无令牌可访问），keyId/公钥/摘要/过期时间齐全
        MvcResult keyResult = rawMockMvc.perform(get("/auth/sm2/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.keyId").value(not(emptyString())))
                .andExpect(jsonPath("$.data.publicKey").value(not(emptyString())))
                .andExpect(jsonPath("$.data.digest").value(not(emptyString())))
                .andExpect(jsonPath("$.data.expireAt").value(not(emptyString())))
                .andReturn();
        String keyBody = keyResult.getResponse().getContentAsString();
        // 先赋值 Object 再转 String：直接 String.valueOf(JsonPath.read(...)) 会命中 valueOf(char[]) 重载
        Object keyIdObj = JsonPath.read(keyBody, "$.data.keyId");
        Object publicKeyObj = JsonPath.read(keyBody, "$.data.publicKey");
        Object digestObj = JsonPath.read(keyBody, "$.data.digest");
        String keyId = String.valueOf(keyIdObj);
        String publicKey = String.valueOf(publicKeyObj);
        String digest = String.valueOf(digestObj);

        // 公钥格式：04 前缀非压缩点 hex（130 字符）；摘要为 SM3(publicKey) hex（64 字符）
        org.junit.jupiter.api.Assertions.assertEquals(130, publicKey.length());
        org.junit.jupiter.api.Assertions.assertTrue(publicKey.startsWith("04"));
        org.junit.jupiter.api.Assertions.assertEquals(cn.sijay.oi.common.util.SmUtil.sm3Hex(publicKey), digest);

        // 2. 国密登录成功：用下发公钥 SM2 加密密码，携带 keyId 提交
        String cipher = cn.sijay.oi.common.util.SmUtil.bytesToHex(
                cn.sijay.oi.common.util.SmUtil.sm2Encrypt(NORMAL_PASSWORD.getBytes(java.nio.charset.StandardCharsets.UTF_8), publicKey));
        rawMockMvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"username\":\"%s\",\"password\":\"%s\",\"keyId\":\"%s\"}"
                                .formatted(NORMAL_USERNAME, cipher, keyId)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.tokenValue").value(not(emptyString())));

        // 3. 错误 keyId（未知密钥标识）→ 601008
        rawMockMvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"username\":\"%s\",\"password\":\"%s\",\"keyId\":\"not-exist-key\"}"
                                .formatted(NORMAL_USERNAME, cipher)))
                .andExpect(jsonPath("$.code").value(601008))
                .andExpect(jsonPath("$.message").value("密码密文无效或已过期，请刷新页面后重试"));

        // 4. 篡改密文（keyId 正确但密文损坏）→ 601008
        String tampered = new StringBuilder(cipher).reverse().toString();
        rawMockMvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"username\":\"%s\",\"password\":\"%s\",\"keyId\":\"%s\"}"
                                .formatted(NORMAL_USERNAME, tampered, keyId)))
                .andExpect(jsonPath("$.code").value(601008))
                .andExpect(jsonPath("$.message").value("密码密文无效或已过期，请刷新页面后重试"));
    }

    /**
     * 保存菜单实体（必填字段补齐默认值）。
     *
     * @param parentId  父菜单 id；null 表示根节点
     * @param name      菜单名称
     * @param type      节点类型
     * @param path      路由路径
     * @param component 组件路径；MENU 必填语义，目录传 null
     * @param icon      图标
     * @param sort      排序号
     * @return 已落库菜单实体（含 id）
     */
    private SysMenu saveMenu(Long parentId, String name, MenuType type, String path, String component, String icon, int sort) {
        SysMenu menu = new SysMenu();
        menu.setParentId(parentId == null ? 0L : parentId);
        menu.setName(name);
        menu.setType(type);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setIcon(icon);
        menu.setSort(sort);
        menu.setVisible(true);
        menu.setEnabled(true);
        return menuRepository.save(menu);
    }

    /**
     * 保存用户并按需关联角色（密码统一 NORMAL_PASSWORD，BCrypt 落库）。
     *
     * @param username 登录名
     * @param roleId   角色 id；null 表示不挂角色
     */
    private void saveUser(String username, Long roleId) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(new BCryptPasswordEncoder().encode(NORMAL_PASSWORD));
        user.setNickname(username);
        user.setEnabled(true);
        SysUser saved = userRepository.save(user);
        if (roleId != null) {
            userRoleRepository.save(new SysUserRole(saved.getId(), roleId));
        }
    }
}
