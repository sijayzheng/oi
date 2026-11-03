package cn.sijay.oi.sys.model;

import cn.sijay.oi.sys.entity.SysLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志出参模型（视图对象），日志管理列表与详情的数据载体。
 *
 * <p>安全约定：params 为请求参数 JSON（入库前已截断），errorMsg 为失败原因摘要，
 * 均为服务端已落盘内容原样输出；id 为 {@code Long}，由全局 Jackson 配置序列化为字符串。
 *
 * @author sijay
 */
@Data
public class SysLogVO {

    /**
     * 日志 id。
     */
    private Long id;

    /**
     * 日志级别；DEBUG/INFO/WARN/ERROR。
     */
    private String level;

    /**
     * 业务模块标识。
     */
    private String module;

    /**
     * 操作动作。
     */
    private String action;

    /**
     * 操作人登录名快照。
     */
    private String username;

    /**
     * 操作人用户 id。
     */
    private Long userId;

    /**
     * 客户端 IP。
     */
    private String ip;

    /**
     * 请求路径。
     */
    private String uri;

    /**
     * HTTP 方法。
     */
    private String method;

    /**
     * 请求 ID（短追踪标识）。
     */
    private String requestId;

    /**
     * 执行结果；SUCCESS/FAIL。
     */
    private String result;

    /**
     * 日志内容；成功记录为请求参数 JSON，失败记录为失败原因摘要（errorMsg 优先）。
     */
    private String content;

    /**
     * 请求参数 JSON；详情查看用。
     */
    private String params;

    /**
     * 失败原因摘要；详情查看用。
     */
    private String errorMsg;

    /**
     * 记录时间。
     */
    private LocalDateTime createTime;

    /**
     * 由实体构建视图对象（实体 → VO 的唯一映射入口，新增对外字段只改此处）。
     *
     * @param entity 日志实体
     * @return 视图对象
     */
    public static SysLogVO from(SysLog entity) {
        SysLogVO vo = new SysLogVO();
        vo.setId(entity.getId());
        vo.setLevel(entity.getLevel());
        vo.setModule(entity.getModule());
        vo.setAction(entity.getAction());
        vo.setUsername(entity.getUsername());
        vo.setUserId(entity.getUserId());
        vo.setIp(entity.getIp());
        vo.setUri(entity.getUri());
        vo.setMethod(entity.getMethod());
        vo.setRequestId(entity.getRequestId());
        vo.setResult(entity.getResult());
        vo.setParams(entity.getParams());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setContent(entity.getErrorMsg() == null || entity.getErrorMsg().isBlank()
                ? entity.getParams()
                : entity.getErrorMsg());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
