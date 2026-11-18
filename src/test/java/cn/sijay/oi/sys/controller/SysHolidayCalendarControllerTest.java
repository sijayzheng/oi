package cn.sijay.oi.sys.controller;

import cn.sijay.oi.support.AuthenticatedIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 节假日日历模块 CRUD 集成测试（真实 Spring 上下文 + 内存 H2 + MockMvc）。
 *
 * <p>环境说明：覆盖数据源为内存 H2（每上下文独立、无残留），schema 每次执行保证表结构。
 * 验证全链路：Bean Validation → 业务校验（日期唯一）→ JPA 落库（物理删除）→ 全局响应格式。
 *
 * <p>登录态：继承 {@link AuthenticatedIntegrationTest}，全部请求自动携带种子超管令牌。
 *
 * @author sijay
 */
@DisplayName("节假日日历模块 CRUD 集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:holidaycrud;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class SysHolidayCalendarControllerTest extends AuthenticatedIntegrationTest {

    /**
     * 测试日期：元旦（普通工作日）。
     */
    private static final LocalDate NEW_YEAR = LocalDate.of(2026, 1, 1);

    /**
     * 测试日期：国庆（三倍工资节假日）。
     */
    private static final LocalDate NATIONAL_DAY = LocalDate.of(2026, 10, 1);

    /**
     * 测试日期：次日（唯一性/补班日场景）。
     */
    private static final LocalDate JAN_SECOND = LocalDate.of(2026, 1, 2);

    /**
     * 测试日期：第三天（参数校验场景）。
     */
    private static final LocalDate JAN_THIRD = LocalDate.of(2026, 1, 3);

    /**
     * 测试日期：次年元旦（删除后重建场景）。
     */
    private static final LocalDate NEXT_NEW_YEAR = LocalDate.of(2027, 1, 1);

    /**
     * 不存在的日期。
     */
    private static final LocalDate MISSING_DAY = LocalDate.of(2099, 12, 31);

    @Test
    @Order(1)
    @DisplayName("全链路：创建 → 详情 → 按日期查询 → 更新 → 分页过滤 → 删除")
    void fullCrudFlow() throws Exception {
        // 1. 创建：普通工作日
        mockMvc.perform(post("/holiday-calendar").contentType("application/json")
                        .content("""
                                {"calDate":"%s","weekDay":4,"dayType":0,"detailsType":0,"typeDes":"工作日","dayOfYear":1,"weekOfYear":1,"indexWorkDayOfMonth":1,"makeUpDay":0}
                                """.formatted(NEW_YEAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 2. 创建：国庆节假日（三倍工资）
        mockMvc.perform(post("/holiday-calendar").contentType("application/json")
                        .content("""
                                {"calDate":"%s","weekDay":4,"yearTips":"丙午","dayType":2,"detailsType":3,"typeDes":"国庆","chineseZodiac":"马","solarTerms":"","lunarCalendar":"八月廿一","suit":"祭祀","dayOfYear":274,"weekOfYear":40,"constellation":"天秤座","indexWorkDayOfMonth":0,"makeUpDay":0}
                                """.formatted(NATIONAL_DAY)))
                .andExpect(jsonPath("$.code").value(200));

        // 3. 详情：id 字符串化、日期按 ISO 输出
        mockMvc.perform(get("/holiday-calendar/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.calDate").value(NEW_YEAR.toString()))
                .andExpect(jsonPath("$.data.weekDay").value(4))
                .andExpect(jsonPath("$.data.dayType").value(0))
                .andExpect(jsonPath("$.data.typeDes").value("工作日"))
                .andExpect(jsonPath("$.data.id").value("1"));

        // 4. 按日期查询（业务键入口）
        mockMvc.perform(get("/holiday-calendar/date/" + NATIONAL_DAY))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.typeDes").value("国庆"))
                .andExpect(jsonPath("$.data.detailsType").value(3));

        // 5. 更新：整体覆盖可空字段（calDate 不在入参中，不可修改）
        mockMvc.perform(put("/holiday-calendar/2").contentType("application/json")
                        .content("""
                                {"weekDay":4,"dayType":1,"detailsType":2,"typeDes":"国庆假期","dayOfYear":274,"weekOfYear":40,"makeUpDay":1}
                                """))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/holiday-calendar/2"))
                .andExpect(jsonPath("$.data.calDate").value(NATIONAL_DAY.toString()))
                .andExpect(jsonPath("$.data.typeDes").value("国庆假期"))
                .andExpect(jsonPath("$.data.yearTips").value(nullValue()))
                .andExpect(jsonPath("$.data.makeUpDay").value(1));

        // 6. 分页：日期区间 + 类型精确 + 描述模糊 + 日期精确
        mockMvc.perform(get("/holiday-calendar/page")
                        .param("beginDate", NEW_YEAR.toString())
                        .param("endDate", NATIONAL_DAY.toString()))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.total").value("2"));
        mockMvc.perform(get("/holiday-calendar/page").param("dayType", "1"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].calDate").value(NATIONAL_DAY.toString()));
        mockMvc.perform(get("/holiday-calendar/page").param("typeDes", "假期"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].calDate").value(NATIONAL_DAY.toString()));
        mockMvc.perform(get("/holiday-calendar/page").param("calDate", NEW_YEAR.toString()))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].typeDes").value("工作日"));

        // 7. 物理删除：详情与按日期查询均不可见（610001）
        mockMvc.perform(delete("/holiday-calendar/1"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/holiday-calendar/1"))
                .andExpect(jsonPath("$.code").value(610001))
                .andExpect(jsonPath("$.message").value("节假日日历数据不存在"));
        mockMvc.perform(get("/holiday-calendar/date/" + NEW_YEAR))
                .andExpect(jsonPath("$.code").value(610001));

        // 8. 删除后同日可重建
        mockMvc.perform(post("/holiday-calendar").contentType("application/json")
                        .content("""
                                {"calDate":"%s","weekDay":5,"dayType":0,"detailsType":0,"typeDes":"工作日","dayOfYear":1,"weekOfYear":1}
                                """.formatted(NEXT_NEW_YEAR)))
                .andExpect(jsonPath("$.code").value(200));

        // 9. 不存在的 id / 日期
        mockMvc.perform(get("/holiday-calendar/999"))
                .andExpect(jsonPath("$.code").value(610001));
        mockMvc.perform(delete("/holiday-calendar/999"))
                .andExpect(jsonPath("$.code").value(610001));
        mockMvc.perform(get("/holiday-calendar/date/" + MISSING_DAY))
                .andExpect(jsonPath("$.code").value(610001));
    }

    @Test
    @Order(2)
    @DisplayName("业务校验：日期重复返回 610002")
    void createDuplicateDate() throws Exception {
        String body = """
                {"calDate":"%s","weekDay":5,"dayType":0,"detailsType":0,"typeDes":"工作日","dayOfYear":2,"weekOfYear":1}
                """.formatted(JAN_SECOND);
        mockMvc.perform(post("/holiday-calendar").contentType("application/json").content(body))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/holiday-calendar").contentType("application/json").content(body))
                .andExpect(jsonPath("$.code").value(610002))
                .andExpect(jsonPath("$.message").value("该日期的日历数据已存在"));
    }

    @Test
    @Order(3)
    @DisplayName("参数校验：必填缺失/值域越界返回 400 与明确错误信息")
    void createValidationFailures() throws Exception {
        // 必填缺失：calDate
        mockMvc.perform(post("/holiday-calendar").contentType("application/json")
                        .content("""
                                {"weekDay":1,"dayOfYear":1,"weekOfYear":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("日期不能为空"));

        // 值域越界：weekDay=8
        mockMvc.perform(post("/holiday-calendar").contentType("application/json")
                        .content("""
                                {"calDate":"%s","weekDay":8,"dayOfYear":1,"weekOfYear":1}
                                """.formatted(JAN_THIRD)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("星期取值范围为 1~7（1=周一 … 7=周日）"));

        // 值域越界：dayType=3
        mockMvc.perform(post("/holiday-calendar").contentType("application/json")
                        .content("""
                                {"calDate":"%s","weekDay":1,"dayType":3,"dayOfYear":1,"weekOfYear":1}
                                """.formatted(JAN_THIRD)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("日期类型取值范围为 0~2（0=工作日 1=假日 2=节假日）"));

        // 路径参数：非法 id
        mockMvc.perform(get("/holiday-calendar/0"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("日历记录 id 必须为正整数"));
    }
}
