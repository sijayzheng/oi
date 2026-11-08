package cn.sijay.oi.sys.model.job;

import cn.sijay.oi.sys.enums.JobTriggerType;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务定义分页条件查询入参模型，仅承载业务过滤字段。
 *
 * <p>分页与排序参数由独立的 {@code PageQuery} 接收；
 * jobKey/name 走 CONTAINING 模糊匹配，triggerType/enabled 精确匹配，全部选填。
 *
 * @author sijay
 */
@Data
public class SysJobQuery {

    /**
     * 任务业务键过滤；模糊匹配，选填。
     */
    @Size(max = 100, message = "任务业务键过滤条件最长 100 字符")
    private String jobKey;

    /**
     * 任务名称过滤；模糊匹配，选填。
     */
    @Size(max = 100, message = "任务名称过滤条件最长 100 字符")
    private String name;

    /**
     * 触发类型过滤；精确匹配，选填。
     */
    private JobTriggerType triggerType;

    /**
     * 启用状态过滤；精确匹配，选填（true=启用中 / false=已暂停）。
     */
    private Boolean enabled;
}
