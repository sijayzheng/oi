package cn.sijay.oi.sys.model.holiday;

import cn.sijay.oi.sys.entity.SysHolidayCalendar;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 节假日日历出参模型（视图对象），字段为 {@code sys_holiday_calendar} 的对外全量子集。
 *
 * <p>输出约定：id 为 {@code Long}，由全局 Jackson 配置序列化为字符串（防前端精度丢失）；
 * {@code calDate} 序列化为 {@code yyyy-MM-dd}；时间字段序列化为 {@code yyyy-MM-dd HH:mm:ss}。
 *
 * @author sijay
 */
@Data
public class SysHolidayCalendarVO {

    /**
     * 日历记录 id。
     */
    private Long id;

    /**
     * 当前日期（自然日）。
     */
    private LocalDate calDate;

    /**
     * 当前周第几天；1=周一 … 7=周日（ISO-8601）。
     */
    private Integer weekDay;

    /**
     * 天干地支纪年描述，如"戊戌"。
     */
    private String yearTips;

    /**
     * 类型：0=工作日 / 1=假日 / 2=节假日。
     */
    private Integer dayType;

    /**
     * 细分类型：0=工作日 / 1=假日 / 2=普通节假日 / 3=三倍工资节假日。
     */
    private Integer detailsType;

    /**
     * 类型描述，如"国庆""休息日""工作日"。
     */
    private String typeDes;

    /**
     * 属相，如"狗"。
     */
    private String chineseZodiac;

    /**
     * 节气描述，如"小雪"。
     */
    private String solarTerms;

    /**
     * 农历日期，如"十月廿六"。
     */
    private String lunarCalendar;

    /**
     * 宜事项。
     */
    private String suit;

    /**
     * 这一年的第几天（1~366）。
     */
    private Integer dayOfYear;

    /**
     * 这一年的第几周。
     */
    private Integer weekOfYear;

    /**
     * 星座，如"射手座"。
     */
    private String constellation;

    /**
     * 当月第几个工作日；非工作日为 0。
     */
    private Integer indexWorkDayOfMonth;

    /**
     * 是否补班日：0=否 / 1=是。
     */
    private Integer makeUpDay;

    /**
     * 记录创建时间（首次同步）。
     */
    private LocalDateTime createTime;

    /**
     * 最近同步/更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 由实体构建视图对象（实体 → VO 的唯一映射入口，新增对外字段只改此处）。
     *
     * @param entity 节假日日历实体
     * @return 视图对象
     */
    public static SysHolidayCalendarVO from(SysHolidayCalendar entity) {
        SysHolidayCalendarVO vo = new SysHolidayCalendarVO();
        vo.setId(entity.getId());
        vo.setCalDate(entity.getCalDate());
        vo.setWeekDay(entity.getWeekDay());
        vo.setYearTips(entity.getYearTips());
        vo.setDayType(entity.getDayType());
        vo.setDetailsType(entity.getDetailsType());
        vo.setTypeDes(entity.getTypeDes());
        vo.setChineseZodiac(entity.getChineseZodiac());
        vo.setSolarTerms(entity.getSolarTerms());
        vo.setLunarCalendar(entity.getLunarCalendar());
        vo.setSuit(entity.getSuit());
        vo.setDayOfYear(entity.getDayOfYear());
        vo.setWeekOfYear(entity.getWeekOfYear());
        vo.setConstellation(entity.getConstellation());
        vo.setIndexWorkDayOfMonth(entity.getIndexWorkDayOfMonth());
        vo.setMakeUpDay(entity.getMakeUpDay());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
