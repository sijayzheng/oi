package cn.sijay.oi.sys.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 通知公告实体，映射表 {@code sys_notice}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>关键约定（与 DDL 注释对齐）：
 * <ul>
 *   <li><b>软删除</b>：{@code deleted} 默认 0，删除时写入本行主键 id；
 *       {@link SQLRestriction} 使本实体的所有查询自动携带 {@code deleted = 0} 条件；</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_notice")
@SQLRestriction("deleted = 0")
public class SysNotice {

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 通知标题。必填。
     */
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    /**
     * 通知类型；0=通知 1=公告。
     */
    @Column(name = "notice_type", length = 20)
    private String noticeType;

    /**
     * 通知内容。必填。
     */
    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    /**
     * 发布时间。
     */
    @Column(name = "publish_time")
    private LocalDateTime publishTime;

    /**
     * 是否置顶；true=置顶。
     */
    @Column(name = "top_marked")
    private Boolean topMarked;

    /**
     * createBy。
     */
    @Column(name = "create_by")
    private Long createBy;

    /**
     * createTime。
     */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /**
     * updateBy。
     */
    @Column(name = "update_by")
    private Long updateBy;

    /**
     * updateTime。
     */
    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * deleted。
     */
    @Column(name = "deleted")
    private Long deleted = 0L;

}
