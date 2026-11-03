package cn.sijay.oi.sys.service;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.model.SelectOption;
import cn.sijay.oi.sys.entity.SysDictItem;
import cn.sijay.oi.sys.entity.SysDictType;
import cn.sijay.oi.sys.model.dict.*;
import cn.sijay.oi.sys.repository.SysDictItemRepository;
import cn.sijay.oi.sys.repository.SysDictTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 字典业务层，提供字典类型与字典项双表的增删改查与业务校验。
 *
 * <p>分层约定：参数格式校验（非空/长度/格式）由入参模型的 Bean Validation 完成；
 * 本层负责<b>业务合法性校验</b>（字典编码唯一、字典项同字典内唯一、类型存在性），
 * 失败抛出 {@link BusinessException}（标准错误码），由全局异常处理器统一包装为 {@code Result}。
 *
 * <p>一致性约定：删除字典类型时级联软删其全部字典项（同一事务内完成）；
 * 字典类型/项的编码均不可通过更新接口修改（被冗余引用或作为业务存储值）。
 *
 * <p>事务约定：写操作 {@code @Transactional}，读操作 {@code @Transactional(readOnly = true)}。
 *
 * @author sijay
 */
@Service
@RequiredArgsConstructor
public class SysDictService {

    /**
     * 系统操作人 id；0=系统初始化（DDL 约定），接入 sa-token 后替换为当前登录人 id。
     */
    private static final long SYSTEM_OPERATOR_ID = 0L;

    private final SysDictTypeRepository dictTypeRepository;
    private final SysDictItemRepository dictItemRepository;

    // ==================== 字典类型 ====================

    /**
     * 创建字典类型。
     *
     * <p>业务校验：字典编码唯一（查询层自动过滤软删行，软删后的同编码可重建）。
     *
     * @param cmd 创建入参（格式校验已由 Bean Validation 完成）
     * @return 已落库的字典类型实体（含自增 id）
     * @throws BusinessException 字典编码已存在（错误码 605001）
     */
    @Transactional
    public SysDictType createType(SysDictTypeCreateCmd cmd) {
        if (dictTypeRepository.existsByDictType(cmd.getDictType())) {
            throw new BusinessException(BusinessErrorCode.DICT_TYPE_EXISTS);
        }
        SysDictType type = new SysDictType();
        type.setDictType(cmd.getDictType());
        type.setName(cmd.getName());
        type.setEnabled(cmd.getEnabled());
        type.setRemark(cmd.getRemark());
        type.setCreateBy(SYSTEM_OPERATOR_ID);
        return dictTypeRepository.save(type);
    }

    /**
     * 按 id 查询字典类型详情。
     *
     * @param id 字典类型 id
     * @return 字典类型实体
     * @throws BusinessException 字典类型不存在或已软删（错误码 605002）
     */
    @Transactional(readOnly = true)
    public SysDictType getTypeById(Long id) {
        return dictTypeRepository.findById(id)
                                  .orElseThrow(() -> new BusinessException(BusinessErrorCode.DICT_NOT_FOUND));
    }

    /**
     * 按 id 更新字典类型（dictType 不可修改，见 SysDictTypeUpdateCmd 约定）。
     *
     * @param id  字典类型 id
     * @param cmd 更新入参（格式校验已由 Bean Validation 完成）
     * @return 更新后的字典类型实体
     * @throws BusinessException 字典类型不存在或已软删（错误码 605002）
     */
    @Transactional
    public SysDictType updateType(Long id, SysDictTypeUpdateCmd cmd) {
        SysDictType type = getTypeById(id);
        type.setName(cmd.getName());
        type.setEnabled(cmd.getEnabled());
        type.setRemark(cmd.getRemark());
        type.setUpdateBy(SYSTEM_OPERATOR_ID);
        return type;
    }

    /**
     * 按 id 软删除字典类型，并级联软删其全部字典项（同一事务内保证两表一致性）。
     *
     * @param id 字典类型 id
     * @throws BusinessException 字典类型不存在或已软删（错误码 605002）
     */
    @Transactional
    public void deleteType(Long id) {
        SysDictType type = getTypeById(id);
        if (dictTypeRepository.softDelete(id) == 0) {
            throw new BusinessException(BusinessErrorCode.DICT_NOT_FOUND);
        }
        dictItemRepository.cascadeSoftDeleteByDictType(type.getDictType());
    }

    /**
     * 分页条件查询字典类型列表。
     *
     * <p>实现说明：Query-by-Example——dictType/name 走 {@code CONTAINING} 模糊匹配
     * （忽略大小写），status 枚举精确匹配，未设置的字段自动忽略；
     * 软删行由 {@code @SQLRestriction} 全局过滤。排序仅按显式传入的 sortField 生成，
     * 未指定时为数据库自然顺序。
     *
     * @param query 业务过滤条件（字段格式校验已由 Bean Validation 完成）
     * @param page  分页与排序参数（构造期已归一化，此处仅校验排序字段安全性）
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<SysDictType> pageType(SysDictTypeQuery query, PageQuery page) {
        page.validate();
        SysDictType probe = new SysDictType();
        probe.setDictType(query.getDictType());
        probe.setName(query.getName());
        probe.setEnabled(query.getEnabled());
        ExampleMatcher matcher = ExampleMatcher.matching()
                                               .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
                                               .withIgnoreCase()
                                               .withIgnorePaths("id", "createBy", "updateBy",
                                                       "createTime", "updateTime", "deleted");
        PageRequest pageRequest = page.toPageRequest();
        return dictTypeRepository.findAll(Example.of(probe, matcher), pageRequest);
    }

    // ==================== 字典项 ====================

    /**
     * 创建字典项。
     *
     * <p>业务校验：所属字典类型必须存在；同字典内项编码唯一。
     *
     * @param cmd 创建入参（格式校验已由 Bean Validation 完成）
     * @return 已落库的字典项实体（含自增 id）
     * @throws BusinessException 字典类型不存在（605002）或字典项已存在（605003）
     */
    @Transactional
    public SysDictItem createItem(SysDictItemCreateCmd cmd) {
        // 类型存在性校验：dictType 由 sys_dict_type 冗余引用，必须真实存在
        requireType(cmd.getDictType());
        if (dictItemRepository.existsByDictTypeAndItemCode(cmd.getDictType(), cmd.getItemCode())) {
            throw new BusinessException(BusinessErrorCode.DICT_ITEM_EXISTS);
        }
        SysDictItem item = new SysDictItem();
        item.setDictType(cmd.getDictType());
        item.setItemCode(cmd.getItemCode());
        item.setItemLabel(cmd.getItemLabel());
        item.setSort(cmd.getSort() == null ? 0 : cmd.getSort());
        item.setEnabled(cmd.getEnabled());
        item.setRemark(cmd.getRemark());
        return dictItemRepository.save(item);
    }

    /**
     * 按 id 查询字典项详情。
     *
     * @param id 字典项 id
     * @return 字典项实体
     * @throws BusinessException 字典项不存在或已软删（错误码 605004）
     */
    @Transactional(readOnly = true)
    public SysDictItem getItemById(Long id) {
        return dictItemRepository.findById(id)
                                 .orElseThrow(() -> new BusinessException(BusinessErrorCode.DICT_ITEM_NOT_FOUND));
    }

    /**
     * 按 id 更新字典项（dictType/itemCode 不可修改，见 SysDictItemUpdateCmd 约定）。
     *
     * @param id  字典项 id
     * @param cmd 更新入参（格式校验已由 Bean Validation 完成）
     * @return 更新后的字典项实体
     * @throws BusinessException 字典项不存在或已软删（错误码 605004）
     */
    @Transactional
    public SysDictItem updateItem(Long id, SysDictItemUpdateCmd cmd) {
        SysDictItem item = getItemById(id);
        item.setItemLabel(cmd.getItemLabel());
        item.setSort(cmd.getSort());
        item.setEnabled(cmd.getEnabled());
        item.setRemark(cmd.getRemark());
        return item;
    }

    /**
     * 按 id 软删除字典项（deleted 置为本行主键 id）。
     *
     * @param id 字典项 id
     * @throws BusinessException 字典项不存在或已软删（错误码 605004）
     */
    @Transactional
    public void deleteItem(Long id) {
        if (dictItemRepository.softDelete(id) == 0) {
            throw new BusinessException(BusinessErrorCode.DICT_ITEM_NOT_FOUND);
        }
    }

    /**
     * 按字典编码查询全部有效项（下拉框数据源），按 sort 升序排列。
     *
     * <p>使用场景：前端渲染下拉框——逐项翻译 value(itemCode) → label(itemLabel)；
     * 字典类型不存在时返回 605002（与"空字典返回空列表"区分开）。
     *
     * @param dictType 字典编码
     * @return 下拉选项列表（sort 升序）；映射规则 value=itemCode、label=itemLabel
     * @throws BusinessException 字典类型不存在或已软删（错误码 605002）
     */
    @Transactional(readOnly = true)
    public List<SelectOption<String>> listItemOptions(String dictType) {
        requireType(dictType);
        return dictItemRepository.findByDictType(dictType).stream()
                                 .sorted(Comparator.comparing(SysDictItem::getSort)
                                                   .thenComparing(SysDictItem::getId))
                                 .map(item -> SelectOption.of(item.getItemCode(), item.getItemLabel()))
                                 .toList();
    }

    /**
     * 分页条件查询字典项列表。
     *
     * <p>实现说明：Query-by-Example——dictType 精确匹配（按类型取项的典型场景）、
     * itemLabel 走 {@code CONTAINING} 模糊匹配（忽略大小写），status 枚举精确匹配；
     * 软删行由 {@code @SQLRestriction} 全局过滤。排序仅按显式传入的 sortField 生成，
     * 未指定时为数据库自然顺序。
     *
     * @param query 业务过滤条件（字段格式校验已由 Bean Validation 完成）
     * @param page  分页与排序参数（构造期已归一化，此处仅校验排序字段安全性）
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<SysDictItem> pageItem(SysDictItemQuery query, PageQuery page) {
        page.validate();
        SysDictItem probe = new SysDictItem();
        probe.setDictType(query.getDictType());
        probe.setItemLabel(query.getItemLabel());
        probe.setEnabled(query.getEnabled());
        ExampleMatcher matcher = ExampleMatcher.matching()
                                               .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
                                               .withIgnoreCase()
                                               .withIgnorePaths("id", "sort", "deleted");
        PageRequest pageRequest = page.toPageRequest();
        return dictItemRepository.findAll(Example.of(probe, matcher), pageRequest);
    }

    /**
     * 校验字典类型存在（字典项联动校验的统一入口）。
     *
     * @param dictType 字典编码
     * @return 命中的字典类型实体（预留后续联动逻辑使用）
     * @throws BusinessException 字典类型不存在或已软删（错误码 605002）
     */
    private SysDictType requireType(String dictType) {
        return dictTypeRepository.findByDictType(dictType)
                                 .orElseThrow(() -> new BusinessException(BusinessErrorCode.DICT_NOT_FOUND));
    }
}
