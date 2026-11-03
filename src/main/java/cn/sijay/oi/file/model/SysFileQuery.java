package cn.sijay.oi.file.model;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文件分页条件查询入参（与通用 {@code PageQuery} 分离，Spring MVC 按 query 参数绑定）。
 *
 * @author sijay
 */
@Data
public class SysFileQuery {

    /**
     * 原始文件名；模糊匹配（CONTAINING），空/空白时忽略该条件。
     */
    @Size(max = 255, message = "文件名最长 255 字符")
    private String originalName;

    /**
     * 业务标识；精确匹配，空/空白时忽略该条件。
     */
    @Size(max = 50, message = "业务标识最长 50 字符")
    private String businessKey;

    /**
     * 文件扩展名；精确匹配（小写、不含点），空/空白时忽略该条件。
     */
    @Size(max = 20, message = "扩展名最长 20 字符")
    private String extension;
}
