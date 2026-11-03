package cn.sijay.oi.sys.model.dict;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典项分页条件查询入参模型，仅承载业务过滤字段。
 *
 * <p>分页与排序参数（pageNum/pageSize/sortField/desc）由独立的
 * {@link cn.sijay.oi.common.model.PageQuery} 接收，Controller 方法签名形如
 * {@code page(@Valid SysDictItemQuery query, @Valid PageQuery page)}；
 * 本类字段全部为选填——未设置的字段不参与条件匹配。
 *
 * <p>匹配约定：dictType 精确匹配（按类型取项的典型场景）、itemLabel 走
 * {@code ExampleMatcher.StringMatcher.CONTAINING}（忽略大小写）；status 精确匹配。
 *
 * @author sijay
 */
@Data
public class SysDictItemQuery {

    /**
     * 所属字典编码过滤；精确匹配，选填。
     */
    @Size(max = 50, message = "所属字典编码过滤条件最长 50 字符")
    private String dictType;

    /**
     * 项显示名过滤；模糊匹配（包含），选填。
     */
    @Size(max = 100, message = "项显示名过滤条件最长 100 字符")
    private String itemLabel;

    /**
     * 项状态过滤；精确匹配，选填。
     */
    private Boolean enabled;
}
