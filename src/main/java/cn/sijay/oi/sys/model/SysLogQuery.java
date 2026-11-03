package cn.sijay.oi.sys.model;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志分页条件查询入参（与通用 {@code PageQuery} 分离，Spring MVC 按 query 参数绑定）。
 *
 * <p>时间参数由 Spring MVC 按 ISO 格式（yyyy-MM-ddTHH:mm:ss）绑定 LocalDateTime；
 * 前端 datetimerange 控件使用 value-format 转换。
 *
 * @author sijay
 */
@Data
public class SysLogQuery {

    /**
     * 日志级别；取值 DEBUG/INFO/WARN/ERROR，空串或空时不过滤（语义为"全部"）。
     */
    @Pattern(regexp = "DEBUG|INFO|WARN|ERROR|^$", message = "日志级别取值非法")
    private String level;

    /**
     * 关键词；匹配日志内容/动作/模块/操作人/请求路径/请求 ID（模糊），空时不过滤。
     */
    @Size(max = 100, message = "关键词最长 100 字符")
    private String keyword;

    /**
     * 开始时间（含）；空时不过滤。
     */
    private LocalDateTime startTime;

    /**
     * 结束时间（含）；空时不过滤。
     */
    private LocalDateTime endTime;
}
