package cn.sijay.oi.sys.controller;

import cn.sijay.oi.common.Result;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.sys.entity.SysNotice;
import cn.sijay.oi.sys.model.notice.SysNoticeCreateCmd;
import cn.sijay.oi.sys.model.notice.SysNoticeQuery;
import cn.sijay.oi.sys.model.notice.SysNoticeUpdateCmd;
import cn.sijay.oi.sys.model.notice.SysNoticeVO;
import cn.sijay.oi.sys.service.SysNoticeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知公告管理控制器，提供通知公告模块的标准 CRUD 接口。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装方法构建
 * {@link Result}（禁止手动构造）；入参格式校验由 Bean Validation（@Valid）触发，
 * 校验失败与业务异常均由全局异常处理器统一包装，控制器内不写 try-catch。
 *
 * <p>接口清单：
 * <pre>
 * POST   /notice        创建通知公告（Result&lt;Void&gt;）
 * DELETE /notice/{id}   软删除通知公告（Result&lt;Void&gt;）
 * PUT    /notice/{id}   更新通知公告档案（Result&lt;Void&gt;）
 * GET    /notice/{id}   查询通知公告详情（Result&lt;SysNoticeVO&gt;）
 * GET    /notice/page   分页条件查询（Result&lt;List&lt;SysNoticeVO&gt;&gt;，含 total）
 * </pre>
 *
 * @author sijay
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/notice")
public class SysNoticeController extends BaseController {

    private final SysNoticeService noticeService;

    /**
     * 创建通知公告。
     *
     * @param cmd 创建入参；格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PostMapping
    public Result<Void> create(@RequestBody @Valid SysNoticeCreateCmd cmd) {
        noticeService.create(cmd);
        return ok();
    }

    /**
     * 删除通知公告（软删除，deleted 置为本行主键 id）。
     *
     * @param id 通知公告 id；必须为正整数
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "通知公告 id 必须为正整数") Long id) {
        noticeService.delete(id);
        return ok();
    }

    /**
     * 更新通知公告档案。
     *
     * @param id  通知公告 id；必须为正整数
     * @param cmd 更新入参；格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive(message = "通知公告 id 必须为正整数") Long id,
                               @RequestBody @Valid SysNoticeUpdateCmd cmd) {
        noticeService.update(id, cmd);
        return ok();
    }

    /**
     * 查询通知公告详情。
     *
     * @param id 通知公告 id；必须为正整数
     * @return 通知公告详情；不存在时返回 600002
     */
    @GetMapping("/{id}")
    public Result<SysNoticeVO> get(@PathVariable @Positive(message = "通知公告 id 必须为正整数") Long id) {
        return ok(SysNoticeVO.from(noticeService.getById(id)));
    }

    /**
     * 分页条件查询通知公告列表。
     *
     * <p>过滤条件全部选填；分页参数越界/未传在 {@link PageQuery} 构造期自动归一化，
     * 排序字段非法（注入风险）返回 600001。
     *
     * @param query 业务过滤条件（全部选填）
     * @param page  分页与排序参数（pageNum/pageSize/sortField/desc，全部选填）
     * @return 当前页数据与总记录数
     */
    @GetMapping("/page")
    public Result<List<SysNoticeVO>> page(@Valid SysNoticeQuery query, @Valid PageQuery page) {
        Page<SysNotice> result = noticeService.page(query, page);
        List<SysNoticeVO> records = result.getContent().stream().map(SysNoticeVO::from).toList();
        return ok(records, result.getTotalElements());
    }
}
