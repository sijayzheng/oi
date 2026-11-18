package cn.sijay.oi.sys.model.holiday;

import lombok.Data;

import java.time.LocalDate;

/**
 * 节假日接口单日数据（对应 {@code HolidayApiResponse#result}）。
 *
 * <p>字段与接口文档一一对应；其中 {@code yearTips/type/detailsType/indexWorkDayOfMonth/makeUpDay}
 * 仅在请求参数 {@code ignoreHoliday=false} 时返回，否则为 null。
 * {@code date} 保持字符串形态（接口原文），由调用方解析为 {@link java.time.LocalDate}，
 * 避免依赖全局 Jackson 日期格式配置。
 *
 * @author sijay
 */
@Data
public class HolidayApiResult {
    private LocalDate date;
    private Integer weekDay;
    private String yearTips;
    private Integer type;
    private Integer detailsType;
    private String typeDes;
    private String chineseZodiac;
    private String solarTerms;
    private String avoid;
    private String lunarCalendar;
    private String suit;
    private Integer dayOfYear;
    private Integer weekOfYear;
    private String constellation;
    private Integer indexWorkDayOfMonth;
    private Integer makeUpDay;
}
