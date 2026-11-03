package cn.sijay.oi.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 文件实体，映射表 {@code sys_file}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>关键约定（与 DDL 注释对齐）：
 * <ul>
 *   <li><b>统一文件入口</b>：系统内所有业务场景的文件上传均经由 {@code FileService} 落库本表，
 *       业务模块仅持有本表主键 id（文件唯一标识），禁止自行保存存储路径或直接操作文件介质；</li>
 *   <li><b>软删除</b>：{@code deleted} 默认 0，删除时写入本行主键 id；
 *       {@link SQLRestriction} 使本实体的所有查询自动携带 {@code deleted = 0} 条件，
 *       软删行对业务查询不可见（物理文件由服务层删除时一并清理）；</li>
 *   <li><b>存储路径不入库</b>：{@code storageName} 仅存落盘文件名（UUID + 扩展名），
 *       完整物理路径 = 存储根目录 + yyyy/MM 月份分桶 + storageName，
 *       路径映射规则收敛在 {@code FileService} 内部，对外不暴露；</li>
 *   <li><b>审计字段</b>：createTime/updateTime 由 Hibernate 时间戳注解维护，
 *       createBy 由 Service 层写入（当前登录人）。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_file")
@SQLRestriction("deleted = 0")
public class SysFile {

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     * 业务模块保存并传递该 id 即可完成文件的引用与访问。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 原始文件名（含扩展名）；上传时的客户端文件名快照，下载回显与检索用。
     */
    @Column(nullable = false, length = 255)
    private String originalName;

    /**
     * 落盘文件名；UUID + 原扩展名（如 550e8400-....png），
     * 与月份分桶目录共同构成存储路径，由 FileService 统一映射。
     */
    @Column(nullable = false, length = 100)
    private String storageName;

    /**
     * 文件扩展名；小写、不含点（如 png）；来源为原始文件名，扩展名白名单校验依据。
     */
    @Column(length = 20)
    private String extension;

    /**
     * MIME 类型；上传时的 contentType 快照，下载响应头回显。
     */
    @Column(length = 100)
    private String contentType;

    /**
     * 文件大小（字节）；上传时的字节数快照，用于大小限制校验与列表展示。
     */
    @Column(nullable = false)
    private Long size;

    /**
     * 业务标识；标记文件归属的业务场景（如 avatar），用于分类检索与治理。
     */
    @Column(name = "business_key", length = 50)
    private String businessKey;

    /**
     * 创建人用户 id；审计字段，应用层写入。
     */
    @Column(name = "create_by")
    private Long createBy;

    /**
     * 创建时间；审计字段。
     */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /**
     * 最后修改人用户 id；审计字段。
     */
    @Column(name = "update_by")
    private Long updateBy;

    /**
     * 更新时间；行被 UPDATE 时自动刷新。
     */
    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 软删标记；0=未删除，删除时=本行主键 id。
     */
    @Column(nullable = false)
    private Long deleted = 0L;
}
