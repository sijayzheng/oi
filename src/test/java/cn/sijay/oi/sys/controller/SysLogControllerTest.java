package cn.sijay.oi.sys.controller;

import cn.sijay.oi.support.AuthenticatedIntegrationTest;
import cn.sijay.oi.sys.entity.SysLog;
import cn.sijay.oi.sys.entity.SysUser;
import cn.sijay.oi.sys.repository.SysLogRepository;
import cn.sijay.oi.sys.repository.SysUserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 操作日志模块集成测试，覆盖日志管理界面的后端接口全场景。
 *
 * <p>覆盖场景：
 * <ol>
 *   <li>分页检索：按级别/关键词/时间范围组合过滤，固定时间倒序；</li>
 *   <li>导出：按当前筛选条件返回 CSV 文本（表头 + 内容转义）；</li>
 *   <li>登录留痕：登录成功写入 INFO 日志（module=auth/action=login，含请求 ID）；</li>
 *   <li>权限隔离：普通用户访问检索/导出均 403（防越权），未登录返回 401。</li>
 * </ol>
 *
 * <p>登录态：继承 {@link AuthenticatedIntegrationTest}；默认请求携带超管令牌，
 * 需要"他人令牌"或"无令牌"的场景显式携带/使用 {@link #rawMockMvc}。
 */
@DisplayName("操作日志模块集成测试（分页检索 + 导出 + 权限隔离）")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:syslog;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class SysLogControllerTest extends AuthenticatedIntegrationTest {

    @Autowired
    private SysLogRepository logRepository;

    @Autowired
    private SysUserRepository userRepository;

    /**
     * 普通业务用户的登录名/密码。
     */
    private static final String NORMAL_USERNAME = "loguser";

    private static final String NORMAL_PASSWORD = "123456";

    /**
     * 保存一条操作日志（必填字段补齐默认值）。
     */
    private void saveLog(String level, String module, String action, String username,
                         String params, String result, String errorMsg) {
        SysLog entity = new SysLog();
        entity.setLevel(level);
        entity.setModule(module);
        entity.setAction(action);
        entity.setUsername(username);
        entity.setIp("127.0.0.1");
        entity.setUri("/test/" + action);
        entity.setMethod("POST");
        entity.setRequestId("req-" + System.nanoTime());
        entity.setParams(params);
        entity.setResult(result);
        entity.setErrorMsg(errorMsg);
        logRepository.save(entity);
    }

    /**
     * 确保普通业务用户存在（幂等）。
     */
    private long createNormalUser() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        return userRepository.findByUsername(NORMAL_USERNAME)
                .map(SysUser::getId)
                .orElseGet(() -> {
                    SysUser user = new SysUser();
                    user.setUsername(NORMAL_USERNAME);
                    user.setPassword(encoder.encode(NORMAL_PASSWORD));
                    user.setNickname("日志用户");
                    user.setEnabled(true);
                    return userRepository.save(user).getId();
                });
    }

    @Test
    @DisplayName("分页检索：级别/关键词/时间范围组合过滤，条件为空时不过滤")
    void pageFiltering() throws Exception {
        saveLog(SysLog.LEVEL_ERROR, "auth", "login", "intruder", null, SysLog.RESULT_FAIL, "用户名或密码错误");
        saveLog(SysLog.LEVEL_INFO, "online", "kick", "admin", "目标用户: 38(viewer)", SysLog.RESULT_SUCCESS, null);
        saveLog(SysLog.LEVEL_DEBUG, "file", "upload", "admin", "a,b,\"quoted\"", SysLog.RESULT_SUCCESS, null);

        // 级别过滤：仅 ERROR
        String body = mockMvc.perform(get("/log/page").param("level", "ERROR"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        List<String> levels = JsonPath.read(body, "$.data[*].level");
        assertThat(levels).isNotEmpty().containsOnly("ERROR");

        // 关键词过滤：命中日志内容（errorMsg）
        body = mockMvc.perform(get("/log/page").param("keyword", "密码错误"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        List<String> contents = JsonPath.read(body, "$.data[*].content");
        assertThat(contents).isNotEmpty().allSatisfy(c -> assertThat(c).contains("密码错误"));

        // 关键词过滤：命中请求 ID 前缀（req-）
        body = mockMvc.perform(get("/log/page").param("keyword", "req-"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        List<String> requestIds = JsonPath.read(body, "$.data[*].requestId");
        assertThat(requestIds).isNotEmpty().allSatisfy(id -> assertThat(id).startsWith("req-"));

        // 时间范围过滤：结束时间早于全部记录 → 0 行
        mockMvc.perform(get("/log/page")
                        .param("endTime", "2000-01-01T00:00:00"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value("0"));

        // 时间范围过滤：开始时间早于全部记录 → 全部命中（≥3 条种子 + 登录留痕；total 为 Long 序列化字符串）
        body = mockMvc.perform(get("/log/page")
                        .param("startTime", "2000-01-01T00:00:00"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        // 先赋值 Object 再转 String：直接 String.valueOf(JsonPath.read(...)) 会命中 valueOf(char[]) 重载
        Object totalObject = JsonPath.read(body, "$.total");
        assertThat(Integer.parseInt(String.valueOf(totalObject)))
                .isGreaterThanOrEqualTo(3);

        // 固定时间倒序：首条记录时间 >= 末条记录时间
        body = mockMvc.perform(get("/log/page"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        List<String> times = JsonPath.read(body, "$.data[*].createTime");
        assertThat(times).isSortedAccordingTo((a, b) -> b.compareTo(a));
    }

    @Test
    @DisplayName("导出：按筛选条件返回 CSV 文本，含表头与内容转义")
    void exportCsv() throws Exception {
        saveLog(SysLog.LEVEL_ERROR, "auth", "login", "intruder", null, SysLog.RESULT_FAIL, "用户名或密码错误");
        saveLog(SysLog.LEVEL_INFO, "file", "upload", "admin", "a,b,\"quoted\"", SysLog.RESULT_SUCCESS, null);

        // 全量导出：含表头与两条种子记录内容
        String body = mockMvc.perform(get("/log/export"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        Object csvValue = JsonPath.read(body, "$.data");
        String csv = String.valueOf(csvValue);
        assertThat(csv).startsWith("日志ID,日志级别,模块,动作,操作人,客户端IP,请求路径,请求方法,请求ID,执行结果,日志内容,记录时间");
        assertThat(csv).contains("用户名或密码错误");
        // CSV 转义：含逗号/引号的内容被双引号包裹且内部引号翻倍
        assertThat(csv).contains("\"a,b,\"\"quoted\"\"\"");

        // 级别筛选导出：仅 ERROR 行
        body = mockMvc.perform(get("/log/export").param("level", "ERROR"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        // 先赋值 Object 再转 String：直接 String.valueOf(JsonPath.read(...)) 会命中 valueOf(char[]) 重载
        Object csvObject = JsonPath.read(body, "$.data");
        csv = String.valueOf(csvObject);
        assertThat(csv).contains("用户名或密码错误");
        assertThat(csv).doesNotContain("a,b");
    }

    @Test
    @DisplayName("登录留痕：登录成功写入 INFO 日志（module=auth，含请求 ID 与 IP）")
    void loginLogging() throws Exception {
        // 基类 @BeforeEach 已完成 admin 登录（走完整 HTTP 链路）
        String body = mockMvc.perform(get("/log/page").param("level", "INFO").param("keyword", "用户登录成功"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        List<String> modules = JsonPath.read(body, "$.data[*].module");
        assertThat(modules).contains("auth");
        List<String> requestIds = JsonPath.read(body, "$.data[*].requestId");
        assertThat(requestIds).isNotEmpty().allSatisfy(id -> assertThat(id).isNotBlank());
        List<String> ips = JsonPath.read(body, "$.data[*].ip");
        assertThat(ips).isNotEmpty().allSatisfy(ip -> assertThat(ip).isNotBlank());
    }

    @Test
    @DisplayName("权限隔离：普通用户访问检索/导出均 403，未登录访问 401")
    void accessIsolation() throws Exception {
        createNormalUser();
        // 登录普通用户获取令牌
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/auth/login").contentType("application/json")
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(NORMAL_USERNAME, NORMAL_PASSWORD)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        // 先赋值 Object 再转 String：直接 String.valueOf(JsonPath.read(...)) 会命中 valueOf(char[]) 重载
        Object tokenObject = JsonPath.read(body, "$.data.tokenValue");
        String userToken = String.valueOf(tokenObject);

        // 普通用户：检索 403
        rawMockMvc.perform(get("/log/page").header("satoken", userToken))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("仅系统管理员可查看操作日志"));
        // 普通用户：导出 403
        rawMockMvc.perform(get("/log/export").header("satoken", userToken))
                .andExpect(jsonPath("$.code").value(403));
        // 未登录：401
        rawMockMvc.perform(get("/log/page"))
                .andExpect(jsonPath("$.code").value(401));
        rawMockMvc.perform(get("/log/export"))
                .andExpect(jsonPath("$.code").value(401));
    }
}
