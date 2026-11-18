package cn.sijay.oi.sys.model.holiday;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 节假日日历分页条件查询入参模型，仅承载业务过滤字段。
 *
 * <p>分页与排序参数（pageNum/pageSize/sortField/desc）由独立的
 * {@link cn.sijay.oi.common.model.PageQuery} 接收，Controller 方法签名形如
 * {@code page(@Valid SysHolidayCalendarQuery query, @Valid PageQuery page)}；
 * 本类字段全部为选填——未设置的字段不参与条件匹配。
 *
 * <p>匹配约定：{@code calDate} 精确匹配；{@code beginDate/endDate} 为日期区间（闭区间）；
 * {@code typeDes} 模糊匹配（包含）；其余数值字段精确匹配。
 *
 * @author sijay
 */
@Data
public class SysHolidayCalendarQuery {

    /**
     * 日期精确过滤；选填，与 beginDate/endDate 互不冲突（同时命中时取交集）。
     */
    private LocalDate calDate;

    /**
     * 日期区间下限（含）；选填。
     */
    private LocalDate beginDate;

    /**
     * 日期区间上限（含）；选填。
     */
    private LocalDate endDate;

    /**
     * 类型过滤：0=工作日 / 1=假日 / 2=节假日；选填。
     */
    @Min(value = 0, message = "日期类型取值范围为 0~2")
    @Max(value = 2, message = "日期类型取值范围为 0~2")
    private Integer dayType;

    /**
     * 细分类型过滤：0=工作日 / 1=假日 / 2=普通节假日 / 3=三倍工资节假日；选填。
     */
    @Min(value = 0, message = "细分类型取值范围为 0~3")
    @Max(value = 3, message = "细分类型取值范围为 0~3")
    private Integer detailsType;

    /**
     * 补班日过滤：0=否 / 1=是；选填。
     */
    @Min(value = 0, message = "补班日标记取值只能为 0 或 1")
    @Max(value = 1, message = "补班日标记取值只能为 0 或 1")
    private Integer makeUpDay;

    /**
     * 类型描述过滤；模糊匹配（包含），选填，最长 50 字符。
     */
    @Size(max = 50, message = "类型描述过滤条件最长 50 字符")
    private String typeDes;
}
