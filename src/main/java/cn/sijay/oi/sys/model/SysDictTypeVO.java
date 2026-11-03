package cn.sijay.oi.sys.model.dict;

import cn.sijay.oi.sys.entity.SysDictType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典类型出参模型（视图对象），字段为 {@code sys_dict_type} 的对外全量子集。
 *
 * <p>输出约定：id 为 {@code Long}，由全局 Jackson 配置序列化为字符串（防前端精度丢失）；
 * 时间字段由全局 Jackson 配置序列化为 {@code yyyy-MM-dd HH:mm:ss}；
 * status 同时输出枚举名与中文描述（statusDesc）。
 *
 * @author sijay
 */
@Data
public class SysDictTypeVO {

    /**
     * 字典类型 id。
     */
    private Long id;

    /**
     * 字典编码。
     */
    private String dictType;

    /**
     * 字典名称。
     */
    private String name;

    /**
     * 启用状态；true=正常（原 NORMAL）/ false=禁用（原 DISABLED，禁用后其字典项不对外提供）。
     */
    private Boolean enabled;

    /**
     * 备注。
     */
    private String remark;

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
     * @param entity 字典类型实体
     * @return 视图对象
     */
    public static SysDictTypeVO from(SysDictType entity) {
        SysDictTypeVO vo = new SysDictTypeVO();
        vo.setId(entity.getId());
        vo.setDictType(entity.getDictType());
        vo.setName(entity.getName());
        vo.setEnabled(entity.getEnabled());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
