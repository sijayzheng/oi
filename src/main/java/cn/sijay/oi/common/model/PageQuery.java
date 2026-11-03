package cn.sijay.oi.common.model;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.regex.Pattern;

/**
 * 分页查询参数（record 不可变对象），封装所有分页查询接口的通用请求参数。
 *
 * <p>与业务过滤参数分离使用：业务查询类（如 {@code SysUserQuery}）只承载过滤字段，
 * 分页/排序参数单独用本类型接收，Controller 方法签名形如
 * {@code page(@Valid SysUserQuery query, @Valid PageQuery page)}，
 * 两者在 Spring MVC 中均按 query 参数绑定，字段不重叠互不干扰。
 *
 * <p>使用示例：
 * <pre>{@code
 * // HTTP: GET /user/page?pageNum=2&pageSize=20&sortField=createTime&desc=true&username=张
 * public Result<List<SysUserVO>> page(@Valid SysUserQuery query, @Valid PageQuery page) {
 *     return ok(service.page(query, page)...);
 * }
 * }</pre>
 *
 * <p><b>record + 紧凑构造器归一化</b>：本类型不可变，页码/条数越界与未传参数（null）
 * 在构造期即归一化为合法值（页码下限 1、条数区间 [1, 100]、desc 未传默认 true 降序），
 * 因此不依赖 Bean Validation 注解做值域约束（构造器已钳制，注解永不触发）。
 *
 * <p><b>校验策略</b>（{@link #validate()}）：
 * <ul>
 *   <li>排序字段须匹配 {@code [a-zA-Z0-9_.]{1,64}}（字母/数字/下划线/点），
 *       阻断恶意字符串拼接进 ORDER BY 导致的 SQL 注入，非法值直接抛出业务异常；</li>
 *   <li>其余参数已由紧凑构造器归一化，validate() 仅需校验排序字段，幂等可重复调用。</li>
 * </ul>
 *
 * @param pageNum   页码；从 1 开始；未传或越界时归一化为 1
 * @param pageSize  每页条数；未传时默认 {@value #DEFAULT_PAGE_SIZE}，上限 {@value #MAX_PAGE_SIZE}，越界时归一化
 * @param sortField 排序字段；实体属性名（支持"表别名.属性名"），null 表示不指定排序（由查询方决定默认排序）
 * @param desc      是否降序；true=DESC 降序，false=ASC 升序；未传默认 true（后台管理列表惯例：最新数据在前）；
 *                  使用 Boolean 包装以区分"未传"（null→true）与"显式 false"（升序）
 * @author sijay
 */
public record PageQuery(Integer pageNum, Integer pageSize, String sortField, Boolean desc) {

    /**
     * 默认每页条数；pageSize 未传或非法时归一化为该值。
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 每页条数上限；防止恶意大分页拖垮数据库，超出时归一化为该值。
     */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 排序字段合法字符模式；字母、数字、下划线、点（支持表别名.字段名），长度 1~64。
     */
    private static final Pattern SORT_FIELD_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]{1,64}$");

    /**
     * 紧凑构造器：构造期归一化，保证实例恒处合法状态（不变式）。
     *
     * <p>归一化规则：pageNum 未传/小于 1 → 1；pageSize 未传/小于 1 → {@value #DEFAULT_PAGE_SIZE}，
     * 大于 {@value #MAX_PAGE_SIZE} → {@value #MAX_PAGE_SIZE}；desc 未传（null）→ true。
     * sortField 不在此处理，由 {@link #validate()} 按需校验。
     */
    public PageQuery {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
        if (desc == null) {
            desc = true;
        }
    }

    /**
     * 获取查询偏移量（页码转换为 LIMIT/OFFSET 的起始位置）。
     *
     * <p>使用场景：手写分页 SQL（如原生查询、PageHelper 之外的方案）时直接取用。
     *
     * @return 偏移量 = (pageNum - 1) * pageSize；long 类型避免深页码 int 溢出
     */
    public long offset() {
        return (long) (pageNum - 1) * pageSize;
    }

    /**
     * 校验排序字段安全性（见类级"校验策略"）。
     *
     * <p>使用场景：Service 层执行查询前的统一入口校验；
     * 校验失败抛出 {@link BusinessException}（错误码 600001 请求参数错误）。
     */
    public void validate() {
        if (sortField != null && !SORT_FIELD_PATTERN.matcher(sortField).matches()) {
            throw new BusinessException(BusinessErrorCode.COMMON_PARAM_ERROR);
        }
    }

    /**
     * 构建标准分页请求对象（实例方法，统一页码换算，排序仅按显式指定生成）。
     *
     * <p>内部规则：页码转换为 0 基索引（pageNum - 1）供 {@link PageRequest} 使用；
     * sortField 为 null 或空白时不添加任何排序（由数据库自然顺序返回），
     * 否则按 desc 决定升/降序（字段合法性应由 {@link #validate()} 先行校验）。
     * 需要确定性顺序的场景请在请求中显式携带 sortField。
     *
     * <p>使用示例：
     * <pre>{@code
     * Page<SysUser> result = userRepository.findAll(example, page.toPageRequest());
     * }</pre>
     *
     * @return 可直接传给 Repository 查询方法的 PageRequest
     */
    public PageRequest toPageRequest() {
        if (StringUtils.isBlank(sortField)) {
            return PageRequest.of(pageNum - 1, pageSize);
        } else {
            return PageRequest.of(pageNum - 1, pageSize, Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, sortField));

        }
    }
}
