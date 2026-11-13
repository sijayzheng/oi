package cn.sijay.oi.sys.model.config;

import cn.sijay.oi.sys.enums.ConfigValueType;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 参数配置分页条件查询入参模型，仅承载业务过滤字段。
 *
 * <p>分页与排序参数（pageNum/pageSize/sortField/desc）由独立的
 * {@link cn.sijay.oi.common.model.PageQuery} 接收，Controller 方法签名形如
 * {@code page(@Valid SysConfigQuery query, @Valid PageQuery page)}；
 * 本类字段全部为选填——未设置的字段不参与条件匹配。
 *
 * <p>模糊匹配约定：configKey/name 走 {@code ExampleMatcher.StringMatcher.CONTAINING}
 * （忽略大小写）；valueType 为枚举精确匹配。
 *
 * @author sijay
 */
@Data
public class SysConfigQuery {

    /**
     * 参数键过滤；模糊匹配（包含），选填。
     */
    @Size(max = 100, message = "参数键过滤条件最长 100 字符")
    private String configKey;

    /**
     * 参数名称过滤；模糊匹配（包含），选填。
     */
    @Size(max = 100, message = "参数名称过滤条件最长 100 字符")
    private String name;

    /**
     * 值类型过滤；精确匹配，选填。
     */
    private ConfigValueType valueType;
}
