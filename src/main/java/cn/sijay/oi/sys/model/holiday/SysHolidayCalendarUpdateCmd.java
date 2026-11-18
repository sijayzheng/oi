package cn.sijay.oi.sys.model.holiday;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新节假日日历入参模型；{@code calDate} 为业务唯一键，不可通过本方法修改
 * （需要改期请删除后重建）。
 *
 * <p>采用整体覆盖语义：调用方须提交完整日历档案（条件字段可传 null），
 * 未提交的可空字段将被置空，避免接口 {@code ignoreHoliday=true} 同步后残留旧值。
 *
 * @author sijay
 */
@Data
public class SysHolidayCalendarUpdateCmd {

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
