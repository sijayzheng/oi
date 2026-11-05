package cn.sijay.oi.sys.controller;

import cn.sijay.oi.support.AuthenticatedIntegrationTest;
import cn.sijay.oi.sys.entity.SysMessage;
import cn.sijay.oi.sys.entity.SysUser;
import cn.sijay.oi.sys.repository.SysMessageRepository;
import cn.sijay.oi.sys.repository.SysUserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 站内信模块集成测试（真实 Spring 上下文 + 内存 H2 + MockMvc）。
 *
 * <p>覆盖场景：三类消息收发、未读数与已读联动、批量操作、归档
 * 撤回（时限/越权/幂等）、广播惰性状态、跨用户越权防护、发布权限管控。
 *
 * <p>登录态：继承 {@link AuthenticatedIntegrationTest}（种子超管默认请求头）；
 * 普通用户经登录接口获取令牌后以显式请求头访问。
 */
@DisplayName("站内信模块集成测试（收发 + 状态 + 撤回 + 权限）")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:message;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class SysMessageControllerTest extends AuthenticatedIntegrationTest {

    private static final String NORMAL_USERNAME = "msguser";
    private static final String NORMAL_PASSWORD = "123456";

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private SysMessageRepository messageRepository;

    private String normalToken;

    private String adminTok;

    /**
     * 获取超管令牌（显式请求头场景用；缓存避免重复登录）。
     */
    private String adminToken() throws Exception {
        if (adminTok == null) {
            adminTok = login("admin", "123456");
        }
        return adminTok;
    }

    /**
     * 创建普通用户并缓存其令牌（幂等：已存在直接登录）。
     */
    private String normalUserToken() throws Exception {
        if (normalToken == null) {
            if (userRepository.findByUsername(NORMAL_USERNAME).isEmpty()) {
                SysUser user = new SysUser();
                user.setUsername(NORMAL_USERNAME);
                user.setPassword(new BCryptPasswordEncoder().encode(NORMAL_PASSWORD));
                user.setNickname("消息测试用户");
                user.setEnabled(true);
                userRepository.save(user);
            }
            normalToken = login(NORMAL_USERNAME, NORMAL_PASSWORD);
        }
        return normalToken;
    }

    private String sendLetter(String fromToken, String toUsername, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/message").header("satoken", fromToken)
                                                           .contentType(MediaType.APPLICATION_JSON)
                                                           .content("""
                                                                   {"msgType":"LETTER","receiverUsername":"%s","title":"%s","content":"内容-%s"}
                                                                   """.formatted(toUsername, title, title)))
                                  .andExpect(jsonPath("$.code").value(200))
                                  .andReturn();
        Object id = JsonPath.read(result.getResponse().getContentAsString(), "$.data");
        return String.valueOf(id);
    }

    @Test
    @Order(1)
    @DisplayName("私信全链路：发送 → 接收方未读可见 → 未读数联动 → 详情自动已读")
    void letterFullFlow() throws Exception {
        normalUserToken();
        sendLetter(adminToken(), NORMAL_USERNAME, "全链路测试");

        // 接收方未读数 = 1
        mockMvc.perform(get("/message/unread-count").header("satoken", normalUserToken()))
               .andExpect(jsonPath("$.code").value(200))
               .andExpect(jsonPath("$.data").value(1));

        // 收件箱首条为该私信，未读
        MvcResult inbox = mockMvc.perform(get("/message/inbox")
                                         .header("satoken", normalUserToken()).param("msgType", "LETTER"))
                                 .andExpect(jsonPath("$.code").value(200))
                                 .andExpect(jsonPath("$.total").value("1"))
                                 .andExpect(jsonPath("$.data[0].title").value("全链路测试"))
                                 .andExpect(jsonPath("$.data[0].readStatus").value(0))
                                 .andExpect(jsonPath("$.data[0].withdrawable").value(false))
                                 .andReturn();
        Object messageId = JsonPath.read(inbox.getResponse().getContentAsString(), "$.data[0].id");

        // 详情：接收方查看自动已读
        mockMvc.perform(get("/message/" + messageId).header("satoken", normalUserToken()))
               .andExpect(jsonPath("$.code").value(200))
               .andExpect(jsonPath("$.data.readStatus").value(1));
        mockMvc.perform(get("/message/unread-count").header("satoken", normalUserToken()))
               .andExpect(jsonPath("$.data").value(0));
    }


    @Test
    @Order(3)
    @DisplayName("发送校验：接收人不存在 607003、发给自己 607008")
    void sendValidation() throws Exception {
        mockMvc.perform(post("/message").header("satoken", adminToken())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"msgType":"LETTER","receiverUsername":"ghost_user_xx","title":"t","content":"c"}
                                                """))
               .andExpect(jsonPath("$.code").value(607003));

        mockMvc.perform(post("/message").header("satoken", adminToken())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"msgType":"LETTER","receiverUsername":"admin","title":"t","content":"c"}
                                                """))
               .andExpect(jsonPath("$.code").value(607008));
    }

    @Test
    @Order(4)
    @DisplayName("发布权限：普通用户发布系统通知/公告广播返回 607009")
    void publishPermission() throws Exception {
        mockMvc.perform(post("/message").header("satoken", normalUserToken())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"msgType":"NOTICE","receiverUsername":"admin","title":"t","content":"c"}
                                                """))
               .andExpect(jsonPath("$.code").value(607009));
        mockMvc.perform(post("/message").header("satoken", normalUserToken())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"msgType":"BROADCAST","title":"全员公告","content":"c"}
                                                """))
               .andExpect(jsonPath("$.code").value(607009));
    }

    @Test
    @Order(5)
    @DisplayName("跨用户防护：非收发双方查看详情返回 607002，未登录 401")
    void crossUserForbidden() throws Exception {
        // admin → normaluser 的私信（Order 1 已发）；第三方用户读取 → 607002
        SysUser third = new SysUser();
        third.setUsername("thirduser");
        third.setPassword(new BCryptPasswordEncoder().encode(NORMAL_PASSWORD));
        third.setNickname("第三方");
        third.setEnabled(true);
        userRepository.save(third);
        String thirdToken = login("thirduser", NORMAL_PASSWORD);

        MvcResult inbox = mockMvc.perform(get("/message/inbox")
                                         .header("satoken", normalUserToken()).param("msgType", "LETTER"))
                                 .andExpect(jsonPath("$.code").value(200))
                                 .andReturn();
        Object messageId = JsonPath.read(inbox.getResponse().getContentAsString(), "$.data[0].id");

        mockMvc.perform(get("/message/" + messageId).header("satoken", thirdToken))
               .andExpect(jsonPath("$.code").value(607002));

        // 未登录访问收件箱：401
        rawMockMvc.perform(get("/message/inbox"))
                  .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @Order(6)
    @DisplayName("批量操作：全部已读清零未读、归档过滤、删除不可见")
    void batchOperations() throws Exception {
        String id1 = sendLetter(adminToken(), NORMAL_USERNAME, "批量一");
        sendLetter(adminToken(), NORMAL_USERNAME, "批量二");

        // 批量已读（部分 id）→ 未读仅剩 1
        mockMvc.perform(put("/message/read").header("satoken", normalUserToken())
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{\"ids\":[\"%s\"],\"archived\":0}".formatted(id1)))
               .andExpect(jsonPath("$.code").value(200))
               .andExpect(jsonPath("$.data").value(1));
        mockMvc.perform(get("/message/unread-count").header("satoken", normalUserToken()))
               .andExpect(jsonPath("$.data").value(1));

        // 全部已读 → 未读清零
        mockMvc.perform(put("/message/read-all").header("satoken", normalUserToken()))
               .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/message/unread-count").header("satoken", normalUserToken()))
               .andExpect(jsonPath("$.data").value(0));

        // 归档 → 收件箱默认视图不可见，归档箱可见
        mockMvc.perform(put("/message/archive").header("satoken", normalUserToken())
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content("{\"ids\":[\"%s\"],\"archived\":1}".formatted(id1)))
               .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/message/inbox").header("satoken", normalUserToken()).param("keyword", "批量一"))
               .andExpect(jsonPath("$.total").value("0"));
        mockMvc.perform(get("/message/inbox").header("satoken", normalUserToken())
                                             .param("archived", "1").param("keyword", "批量一"))
               .andExpect(jsonPath("$.total").value("1"));

        // 删除 → 归档箱也不可见
        mockMvc.perform(put("/message/trash").header("satoken", normalUserToken())
                                             .contentType(MediaType.APPLICATION_JSON)
                                             .content("{\"ids\":[\"%s\"],\"archived\":1}".formatted(id1)))
               .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/message/inbox").header("satoken", normalUserToken())
                                             .param("archived", "1").param("keyword", "批量一"))
               .andExpect(jsonPath("$.total").value("0"));
    }

    @Test
    @Order(7)
    @DisplayName("公告广播：不预插回执但全员可见，标记已读落状态行")
    void broadcastLazyState() throws Exception {
        // 管理员发布广播
        mockMvc.perform(post("/message").header("satoken", adminToken())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"msgType":"BROADCAST","title":"系统升级公告","content":"今晚升级"}
                                                """))
               .andExpect(jsonPath("$.code").value(200));

        // 普通用户收件箱可见广播（未读）
        MvcResult inbox = mockMvc.perform(get("/message/inbox").header("satoken", normalUserToken())
                                                               .param("msgType", "BROADCAST"))
                                 .andExpect(jsonPath("$.code").value(200))
                                 .andExpect(jsonPath("$.total").value("1"))
                                 .andExpect(jsonPath("$.data[0].readStatus").value(0))
                                 .andReturn();
        Object broadcastId = JsonPath.read(inbox.getResponse().getContentAsString(), "$.data[0].id");

        // 未读数包含广播；标记已读后未读减少（状态行惰性落库）
        mockMvc.perform(get("/message/unread-count").header("satoken", normalUserToken()))
               .andExpect(jsonPath("$.data").value(1));
        mockMvc.perform(put("/message/read").header("satoken", normalUserToken())
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{\"ids\":[\"%s\"],\"archived\":0}".formatted(broadcastId)))
               .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/message/unread-count").header("satoken", normalUserToken()))
               .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    @Order(8)
    @DisplayName("消息撤回：发送人 5 分钟内撤回成功，接收方即时不可见，重复撤回幂等")
    void withdrawWithinWindow() throws Exception {
        String id = sendLetter(adminToken(), NORMAL_USERNAME, "待撤回消息");

        // 发送人撤回
        mockMvc.perform(post("/message/withdraw/" + id).header("satoken", adminToken()))
               .andExpect(jsonPath("$.code").value(200));

        // 接收方收件箱不可见、未读数不含该消息
        mockMvc.perform(get("/message/inbox").header("satoken", normalUserToken())
                                             .param("keyword", "待撤回消息"))
               .andExpect(jsonPath("$.total").value("0"));
        mockMvc.perform(get("/message/unread-count").header("satoken", normalUserToken()))
               .andExpect(jsonPath("$.data").value(0));

        // 重复撤回：幂等成功
        mockMvc.perform(post("/message/withdraw/" + id).header("satoken", adminToken()))
               .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(9)
    @DisplayName("撤回防护：非发送人撤回 607007；超过 5 分钟时限 607006")
    void withdrawProtection() throws Exception {
        String id = sendLetter(adminToken(), NORMAL_USERNAME, "防护测试消息");

        // 非发送人撤回 → 607007
        mockMvc.perform(post("/message/withdraw/" + id).header("satoken", normalUserToken()))
               .andExpect(jsonPath("$.code").value(607007));

        // 将发送时间改到 10 分钟前 → 超过时限 607006
        SysMessage message = messageRepository.findById(Long.valueOf(id)).orElseThrow();
        message.setSendTime(LocalDateTime.now().minusMinutes(10));
        messageRepository.save(message);
        mockMvc.perform(post("/message/withdraw/" + id).header("satoken", adminToken()))
               .andExpect(jsonPath("$.code").value(607006))
               .andExpect(jsonPath("$.message").value("已超过可撤回时限（5 分钟）"));
    }
}
