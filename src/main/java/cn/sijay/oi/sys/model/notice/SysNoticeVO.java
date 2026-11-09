package cn.sijay.oi.sys.model.notice;

import cn.sijay.oi.sys.entity.SysNotice;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知公告出参模型（视图对象），字段为 {@code sys_notice} 的对外全量子集。
 *
 * <p>输出约定：id 为 {@code Long}，由全局 Jackson 配置序列化为字符串（防前端精度丢失）；
 * 时间字段为 {@link LocalDateTime}，由全局 Jackson 配置序列化为 {@code yyyy-MM-dd HH:mm:ss}。
 *
 * @author sijay
 */
@Data
public class SysNoticeVO {

    /**
     * 通知公告 id。
     */
    private Long id;

    /**
     * 通知标题。
     */
    private String title;

    /**
     * 通知类型；0=通知 1=公告。
     */
    private String noticeType;

    /**
     * 通知内容。
     */
    private String content;

    /**
     * 发布时间。
     */
    private LocalDateTime publishTime;

    /**
     * 是否置顶；true=置顶。
     */
    private Boolean topMarked;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 由实体构建视图对象（实体 → VO 的唯一映射入口，新增对外字段只改此处）。
     *
     * @param entity 通知公告实体
     * @return 视图对象
     */
    public static SysNoticeVO from(SysNotice entity) {
        SysNoticeVO vo = new SysNoticeVO();
        vo.setId(entity.getId());
        vo.setTitle(entity.getTitle());
        vo.setNoticeType(entity.getNoticeType());
        vo.setContent(entity.getContent());
        vo.setPublishTime(entity.getPublishTime());
        vo.setTopMarked(entity.getTopMarked());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
