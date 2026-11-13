package cn.sijay.oi.sys.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 节假日日历实体，映射表 {@code sys_holiday_calendar}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>一日一行，数据来源于第三方节假日接口，为排班、任务调度（跳过节假日/补班日）等
 * 业务提供日期类型判定依据。
 *
 * <p>关键约定（与 DDL 注释对齐）：
 * <ul>
 *   <li><b>自然键</b>：{@code calDate} 唯一，创建后禁止修改（需要改期请删除后重建）；</li>
 *   <li><b>无软删除</b>：外部同步的参考数据，删除即物理删除，按日期区间整体重刷；</li>
 *   <li><b>条件字段可空</b>：{@code yearTips/dayType/detailsType/indexWorkDayOfMonth/makeUpDay}
 *       仅在接口 {@code ignoreHoliday=false} 时返回，因此实体允许为 null；</li>
 *   <li><b>周口径</b>：{@code weekDay} 为 ISO-8601（1=周一 … 7=周日），
 *       与前端 {@code Date#getDay()}（0=周日）不同。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_holiday_calendar")
public class SysHolidayCalendar {

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 当前日期（自然日）；业务唯一键，创建后禁止修改。
     */
    @Column(name = "cal_date", nullable = false)
    private LocalDate calDate;

    /**
     * 当前周第几天；1=周一 … 7=周日（ISO-8601）。
     */
    @Column(name = "week_day", nullable = false)
    private Integer weekDay;

    /**
     * 天干地支纪年描述，如"戊戌"；ignoreHoliday=true 时接口不返回，可为 null。
     */
    @Column(name = "year_tips", length = 20)
    private String yearTips;

    /**
     * 类型：0=工作日 / 1=假日 / 2=节假日；ignoreHoliday=true 时接口不返回，可为 null。
     */
    @Column(name = "day_type")
    private Integer dayType;

    /**
     * 细分类型：0=工作日 / 1=假日 / 2=普通节假日 / 3=三倍工资节假日；
     * ignoreHoliday=true 时接口不返回，可为 null。
     */
    @Column(name = "details_type")
    private Integer detailsType;

    /**
     * 类型描述，如"国庆""休息日""工作日"。
     */
    @Column(name = "type_des", length = 50)
    private String typeDes;

    /**
     * 属相，如"狗"。
     */
    @Column(name = "chinese_zodiac", length = 20)
    private String chineseZodiac;

    /**
     * 节气描述，如"小雪"；非节气日为空串。
     */
    @Column(name = "solar_terms", length = 20)
    private String solarTerms;

    /**
     * 农历日期，如"十月廿六"。
     */
    @Column(name = "lunar_calendar", length = 30)
    private String lunarCalendar;

    /**
     * 宜事项；接口原文，超长截断由同步层处理。
     */
    @Column(name = "suit", length = 500)
    private String suit;

    /**
     * 这一年的第几天（1~366）。
     */
    @Column(name = "day_of_year", nullable = false)
    private Integer dayOfYear;

    /**
     * 这一年的第几周（以接口口径为准）。
     */
    @Column(name = "week_of_year", nullable = false)
    private Integer weekOfYear;

    /**
     * 星座，如"射手座"。
     */
    @Column(name = "constellation", length = 20)
    private String constellation;

    /**
     * 工作日时返回当月第几个工作日，非工作日为 0；ignoreHoliday=true 时接口不返回，可为 null。
     */
    @Column(name = "index_work_day_of_month")
    private Integer indexWorkDayOfMonth;

    /**
     * 是否补班日：0=否 / 1=是（因调休需上班的周末）；ignoreHoliday=true 时接口不返回，可为 null。
     */
    @Column(name = "make_up_day")
    private Integer makeUpDay;

    /**
     * 记录创建时间（首次同步）。
     */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /**
     * 最近同步/更新时间；行被 UPDATE 时自动刷新。
     */
    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
