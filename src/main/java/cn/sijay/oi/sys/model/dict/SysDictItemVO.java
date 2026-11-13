package cn.sijay.oi.sys.model.dict;

import cn.sijay.oi.sys.entity.SysDictItem;
import lombok.Data;

/**
 * 字典项出参模型（视图对象），字段为 {@code sys_dict_item} 的对外全量子集。
 *
 * <p>输出约定：id 为 {@code Long}，由全局 Jackson 配置序列化为字符串（防前端精度丢失）；
 * status 同时输出枚举名与中文描述（statusDesc）。本表无审计字段，不输出时间字段。
 *
 * @author sijay
 */
@Data
public class SysDictItemVO {

    /**
     * 字典项 id。
     */
    private Long id;

    /**
     * 所属字典编码。
     */
    private String dictType;

    /**
     * 项编码（业务实际存储值）。
     */
    private String itemCode;

    /**
     * 项显示名。
     */
    private String itemLabel;

    /**
     * 排序号。
     */
    private Integer sort;

    /**
     * 启用状态；true=正常（原 NORMAL）/ false=禁用（原 DISABLED）。
     */
    private Boolean enabled;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 由实体构建视图对象（实体 → VO 的唯一映射入口，新增对外字段只改此处）。
     *
     * @param entity 字典项实体
     * @return 视图对象
     */
    public static SysDictItemVO from(SysDictItem entity) {
        SysDictItemVO vo = new SysDictItemVO();
        vo.setId(entity.getId());
        vo.setDictType(entity.getDictType());
        vo.setItemCode(entity.getItemCode());
        vo.setItemLabel(entity.getItemLabel());
        vo.setSort(entity.getSort());
        vo.setEnabled(entity.getEnabled());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
