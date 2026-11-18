package cn.sijay.oi.sys.model.holiday;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 创建节假日日历入参模型，字段与表 {@code sys_holiday_calendar} DDL 定义严格对应。
 *
 * <p>校验规则（失败信息直接面向调用方展示）：
 * <ul>
 *   <li>calDate：必填，与已有记录重复时由 Service 返回 610002；</li>
 *   <li>weekDay：必填，1~7（1=周一 … 7=周日）；</li>
 *   <li>dayOfYear：必填，1~366；weekOfYear：必填，1~53；</li>
 *   <li>dayType：0~2；detailsType：0~3；makeUpDay：0~1；indexWorkDayOfMonth：0~31；</li>
 *   <li>文本字段长度上限与 DDL VARCHAR 定义一致。</li>
 * </ul>
 *
 * <p>条件字段约定：{@code yearTips/dayType/detailsType/indexWorkDayOfMonth/makeUpDay}
 * 在接口 {@code ignoreHoliday=true} 时不返回，故均为选填。
 *
 * @author sijay
 */
@Data
public class SysHolidayCalendarCreateCmd {

    /**
     * 当前日期（自然日）；必填，业务唯一键。
     */
    @NotNull(message = "日期不能为空")
    private LocalDate calDate;

    /**
     * 当前周第几天；必填，1=周一 … 7=周日。
     */
    @NotNull(message = "星期不能为空")
    @Min(value = 1, message = "星期取值范围为 1~7（1=周一 … 7=周日）")
    @Max(value = 7, message = "星期取值范围为 1~7（1=周一 … 7=周日）")
    private Integer weekDay;

    /**
     * 天干地支纪年描述，如"戊戌"；选填，最长 20 字符。
     */
    @Size(max = 20, message = "天干地支纪年最长 20 字符")
    private String yearTips;

    /**
     * 类型：0=工作日 / 1=假日 / 2=节假日；选填。
     */
    @Min(value = 0, message = "日期类型取值范围为 0~2（0=工作日 1=假日 2=节假日）")
    @Max(value = 2, message = "日期类型取值范围为 0~2（0=工作日 1=假日 2=节假日）")
    private Integer dayType;

    /**
     * 细分类型：0=工作日 / 1=假日 / 2=普通节假日 / 3=三倍工资节假日；选填。
     */
    @Min(value = 0, message = "细分类型取值范围为 0~3")
    @Max(value = 3, message = "细分类型取值范围为 0~3")
    private Integer detailsType;

    /**
     * 类型描述，如"国庆""休息日""工作日"；选填，最长 50 字符。
     */
    @Size(max = 50, message = "类型描述最长 50 字符")
    private String typeDes;

    /**
     * 属相，如"狗"；选填，最长 20 字符。
     */
    @Size(max = 20, message = "属相最长 20 字符")
    private String chineseZodiac;

    /**
     * 节气描述，如"小雪"；选填，最长 20 字符。
     */
    @Size(max = 20, message = "节气描述最长 20 字符")
    private String solarTerms;

    /**
     * 农历日期，如"十月廿六"；选填，最长 30 字符。
     */
    @Size(max = 30, message = "农历日期最长 30 字符")
    private String lunarCalendar;

    /**
     * 宜事项；选填，最长 500 字符。
     */
    @Size(max = 500, message = "宜事项最长 500 字符")
    private String suit;

    /**
     * 这一年的第几天；必填，1~366。
     */
    @NotNull(message = "一年中的第几天不能为空")
    @Min(value = 1, message = "一年中的第几天取值范围为 1~366")
    @Max(value = 366, message = "一年中的第几天取值范围为 1~366")
    private Integer dayOfYear;

    /**
     * 这一年的第几周；必填，1~53。
     */
    @NotNull(message = "一年中的第几周不能为空")
    @Min(value = 1, message = "一年中的第几周取值范围为 1~53")
    @Max(value = 53, message = "一年中的第几周取值范围为 1~53")
    private Integer weekOfYear;

    /**
     * 星座，如"射手座"；选填，最长 20 字符。
     */
    @Size(max = 20, message = "星座最长 20 字符")
    private String constellation;

    /**
     * 当月第几个工作日；非工作日为 0，选填。
     */
    @Min(value = 0, message = "当月第几个工作日不能为负数")
    @Max(value = 31, message = "当月第几个工作日取值范围为 0~31")
    private Integer indexWorkDayOfMonth;

    /**
     * 是否补班日：0=否 / 1=是；选填。
     */
    @Min(value = 0, message = "补班日标记取值只能为 0 或 1")
    @Max(value = 1, message = "补班日标记取值只能为 0 或 1")
    private Integer makeUpDay;
}
