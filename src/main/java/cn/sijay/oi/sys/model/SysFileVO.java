package cn.sijay.oi.sys.model;

import cn.sijay.oi.sys.entity.SysFile;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件出参模型（视图对象），字段为 {@code sys_file} 的对外可见子集。
 *
 * <p>安全约定：<b>不含 storageName 等存储介质信息</b>——物理存储细节不出服务层；
 * {@code accessUrl} 为带签名与有效期的临时访问地址（见 {@code FileService.issueAccessUrl}），
 * 过期后需重新获取。id 为 {@code Long}，由全局 Jackson 配置序列化为字符串（防前端精度丢失）。
 *
 * @author sijay
 */
@Data
public class SysFileVO {

    /**
     * 文件唯一标识；业务模块持久化保存该 id 即完成文件引用。
     */
    private Long id;

    /**
     * 原始文件名（含扩展名）。
     */
    private String originalName;

    /**
     * 文件扩展名；小写、不含点。
     */
    private String extension;

    /**
     * MIME 类型。
     */
    private String contentType;

    /**
     * 文件大小（字节）。
     */
    private Long size;

    /**
     * 业务标识；文件归属的业务场景（如 avatar）。
     */
    private String businessKey;

    /**
     * 临时访问地址；带签名与有效期（如 /file/access/1?expires=...&sign=...），
     * 仅在出参组装时下发，过期后经 GET /file/{id}/url 重新获取。
     */
    private String accessUrl;

    /**
     * 上传时间。
     */
    private LocalDateTime createTime;

    /**
     * 由实体构建视图对象（实体 → VO 的唯一映射入口，新增对外字段只改此处）。
     *
     * @param entity 文件实体
     * @return 视图对象（accessUrl 需另行填充）
     */
    public static SysFileVO from(SysFile entity) {
        SysFileVO vo = new SysFileVO();
        vo.setId(entity.getId());
        vo.setOriginalName(entity.getOriginalName());
        vo.setExtension(entity.getExtension());
        vo.setContentType(entity.getContentType());
        vo.setSize(entity.getSize());
        vo.setBusinessKey(entity.getBusinessKey());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
