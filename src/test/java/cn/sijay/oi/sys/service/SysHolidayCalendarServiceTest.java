package cn.sijay.oi.sys.service;

import cn.sijay.oi.sys.repository.SysHolidayCalendarRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link SysHolidayCalendarService} 年度同步任务单元测试。
 *
 * <p>覆盖：未配置密钥跳过、逐日 GET 并 upsert 全年数据、连续失败提前终止。
 * 接口客户端与仓库均为 Mock，不发起真实 HTTP 请求、不依赖 Spring 上下文。
 *
 * @author sijay
 */
@DisplayName("节假日日历年度同步任务测试")
@SpringBootTest
class SysHolidayCalendarServiceTest {

    @Autowired
    private SysHolidayCalendarRepository holidayCalendarRepository;

    @Autowired
    private SysHolidayCalendarService holidayCalendarService;

    @Test
    public void syncYearCalendarTest() {
        holidayCalendarService.syncYearCalendar();
    }

}
