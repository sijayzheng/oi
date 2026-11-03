package cn.sijay.oi.sys.model.notice;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 通知公告分页条件查询入参模型，仅承载业务过滤字段。
 *
 * <p>分页与排序参数（pageNum/pageSize/sortField/desc）由独立的
 * {@link cn.sijay.oi.common.model.PageQuery} 接收，Controller 方法签名形如
 * {@code page(@Valid SysNoticeQuery query, @Valid PageQuery page)}；
 * 本类字段全部为选填——未设置的字段不参与条件匹配。
 *
 * <p>匹配约定：字符串条件走 {@code ExampleMatcher.StringMatcher.CONTAINING}
 * （忽略大小写）；其余类型精确匹配。
 *
 * @author sijay
 */
@Data
public class SysNoticeQuery {

    /**
     * 通知标题过滤；模糊匹配（包含），选填。
     */
    @Size(max = 100, message = "通知标题过滤条件最长 100 字符")
    private String title;

    /**
     * 通知类型；0=通知 1=公告过滤；模糊匹配（包含），选填。
     */
    @Size(max = 20, message = "通知类型；0=通知 1=公告过滤条件最长 20 字符")
    private String noticeType;

    /**
     * 通知内容过滤；模糊匹配（包含），选填。
     */
    @Size(max = 2000, message = "通知内容过滤条件最长 2000 字符")
    private String content;

    /**
     * 是否置顶；1=置顶过滤；精确匹配，选填。
     */
    private Integer topMarked;

}
