package cn.sijay.oi.sys.controller;

import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.model.Result;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.sys.entity.SysHolidayCalendar;
import cn.sijay.oi.sys.model.holiday.SysHolidayCalendarCreateCmd;
import cn.sijay.oi.sys.model.holiday.SysHolidayCalendarQuery;
import cn.sijay.oi.sys.model.holiday.SysHolidayCalendarUpdateCmd;
import cn.sijay.oi.sys.model.holiday.SysHolidayCalendarVO;
import cn.sijay.oi.sys.service.SysHolidayCalendarService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 节假日日历控制器，提供日历模块的标准 CRUD 接口与按日期查询接口。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装方法构建
 * {@link Result}（禁止手动构造）；入参格式校验由 Bean Validation（@Valid）触发，
 * 校验失败与业务异常均由全局异常处理器统一包装，控制器内不写 try-catch。
 *
 * <p>接口清单：
 * <pre>
 * POST   /holiday-calendar                创建日历记录（Result&lt;Void&gt;）
 * DELETE /holiday-calendar/{id}           物理删除日历记录（Result&lt;Void&gt;）
 * PUT    /holiday-calendar/{id}           更新日历记录（calDate 不可修改，Result&lt;Void&gt;）
 * GET    /holiday-calendar/{id}           查询日历详情（Result&lt;SysHolidayCalendarVO&gt;）
 * GET    /holiday-calendar/date/{calDate}  按日期查询（Result&lt;SysHolidayCalendarVO&gt;）
 * GET    /holiday-calendar/page           分页条件查询（Result&lt;List&lt;SysHolidayCalendarVO&gt;&gt;，含 total）
 * POST   /holiday-calendar/sync           手动触发年度日历同步（Result&lt;Void&gt;）
 * </pre>
 *
 * @author sijay
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/holiday-calendar")
public class SysHolidayCalendarController extends BaseController {

    private final SysHolidayCalendarService holidayCalendarService;

    /**
     * 创建日历记录。
     *
     * @param cmd 创建入参；calDate/weekDay/dayOfYear/weekOfYear 必填，
     *            格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PostMapping
    public Result<Void> create(@RequestBody @Valid SysHolidayCalendarCreateCmd cmd) {
        holidayCalendarService.create(cmd);
        return ok();
    }

    /**
     * 删除日历记录（物理删除，外部同步的参考数据无软删除语义）。
     *
     * @param id 日历记录 id；必须为正整数
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "日历记录 id 必须为正整数") Long id) {
        holidayCalendarService.delete(id);
        return ok();
    }

    /**
     * 更新日历记录（calDate 为业务唯一键，不可修改）。
     *
     * @param id  日历记录 id；必须为正整数
     * @param cmd 更新入参；整体覆盖语义，格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive(message = "日历记录 id 必须为正整数") Long id,
                               @RequestBody @Valid SysHolidayCalendarUpdateCmd cmd) {
        holidayCalendarService.update(id, cmd);
        return ok();
    }

    /**
     * 查询日历详情。
     *
     * @param id 日历记录 id；必须为正整数
     * @return 日历详情；记录不存在时返回 610001
     */
    @GetMapping("/{id}")
    public Result<SysHolidayCalendarVO> get(@PathVariable @Positive(message = "日历记录 id 必须为正整数") Long id) {
        return ok(SysHolidayCalendarVO.from(holidayCalendarService.getById(id)));
    }

    /**
     * 按日期查询日历详情。
     *
     * <p>使用场景：业务判定某天是工作日/节假日/补班日。
     *
     * @param calDate 自然日，格式 {@code yyyy-MM-dd}
     * @return 日历详情；记录不存在时返回 610001
     */
    @GetMapping("/date/{calDate}")
    public Result<SysHolidayCalendarVO> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate calDate) {
        return ok(SysHolidayCalendarVO.from(holidayCalendarService.getByDate(calDate)));
    }

    /**
     * 分页条件查询日历列表。
     *
     * <p>过滤条件全部选填：calDate 精确；beginDate/endDate 为闭区间；
     * dayType/detailsType/makeUpDay 精确；typeDes 模糊匹配。
     * 分页参数越界/未传在 {@link PageQuery} 构造期自动归一化，
     * 排序字段非法（注入风险）返回 600001。
     *
     * @param query 业务过滤条件（全部选填）
     * @param page  分页与排序参数（pageNum/pageSize/sortField/desc，全部选填）
     * @return 当前页数据与总记录数
     */
    @GetMapping("/page")
    public Result<List<SysHolidayCalendarVO>> page(@Valid SysHolidayCalendarQuery query, @Valid PageQuery page) {
        Page<SysHolidayCalendar> result = holidayCalendarService.page(query, page);
        List<SysHolidayCalendarVO> records = result.getContent().stream().map(SysHolidayCalendarVO::from).toList();
        return ok(records, result.getTotalElements());
    }

    /**
     * 手动触发年度日历同步。
     *
     * <p>使用场景：定时同步之外需要立即补数或修复数据时使用；同步逻辑复用
     * {@link SysHolidayCalendarService#syncYearCalendar()}，按 calDate 幂等 upsert，
     * 重复调用不会产生脏数据。
     *
     * <p>说明：接口密钥未配置或外部接口返回失败时，服务层仅记录日志并跳过，
     * 本接口仍返回成功；实际同步结果请查看服务端日志。
     *
     * @return 成功响应（无数据）
     */
    @PostMapping("/sync")
    public Result<Void> sync() {
        holidayCalendarService.syncYearCalendar();
        return ok();
    }
}
