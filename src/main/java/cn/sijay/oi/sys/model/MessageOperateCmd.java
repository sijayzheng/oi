package cn.sijay.oi.sys.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 站内信批量操作入参模型（已读/归档/删除）。
 *
 * <p>所有操作均以当前登录人身份执行，服务层仅在本人回执/广播状态行上生效，
 * 从 SQL 条件层面杜绝跨用户修改。
 *
 * @author sijay
 */
@Data
public class MessageOperateCmd {

    /**
     * 消息 id 列表（sys_message.id）；1~100 条。
     */
    @NotEmpty(message = "消息 id 列表不能为空")
    @Size(max = 100, message = "单次操作最多 100 条消息")
    private List<Long> ids;

    /**
     * 归档标记；仅归档操作使用：1=归档 0=取消归档。
     */
    @NotNull(message = "归档标记不能为空")
    private Integer archived;
}
