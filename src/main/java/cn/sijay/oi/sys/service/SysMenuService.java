package cn.sijay.oi.sys.service;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.model.TreeNode;
import cn.sijay.oi.sys.entity.SysMenu;
import cn.sijay.oi.sys.enums.MenuType;
import cn.sijay.oi.sys.model.menu.SysMenuCreateCmd;
import cn.sijay.oi.sys.model.menu.SysMenuQuery;
import cn.sijay.oi.sys.model.menu.SysMenuUpdateCmd;
import cn.sijay.oi.sys.model.menu.SysMenuVO;
import cn.sijay.oi.sys.repository.SysMenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 菜单业务层，提供菜单模块的增删改查、树形装配与层级合法性校验。
 *
 * <p>分层约定：参数格式校验（非空/长度/枚举取值）由入参模型的 Bean Validation 完成；
 * 本层负责<b>业务合法性校验</b>（父菜单存在性、父子类型层级规则、删除影响检查），
 * 失败抛出 {@link BusinessException}（标准错误码），由全局异常处理器统一包装为 {@code Result}。
 *
 * <p><b>层级规则</b>（目录 → 菜单 → 按钮）：目录只能作为根节点（parentId=0）；
 * 菜单的父节点必须为目录；按钮的父节点必须为菜单。parentId/type 创建后不可修改
 * （见 SysMenuUpdateCmd 约定），从机制上杜绝更新操作引入环。
 *
 * <p>事务约定：写操作 {@code @Transactional}，读操作 {@code @Transactional(readOnly = true)}。
 *
 * @author sijay
 */
@Service
@RequiredArgsConstructor
public class SysMenuService {

    /**
     * 根节点 parentId 约定值（DDL：parent_id 默认 0，0=无父节点）。
     */
    private static final long ROOT_PARENT_ID = 0L;

    private final SysMenuRepository menuRepository;

    /**
     * 创建菜单。
     *
     * <p>业务校验：按层级规则校验挂载点——parentId 为空视为根节点（0），
     * 根节点仅允许目录；非根挂载时父菜单必须存在，且父类型满足
     * 目录→菜单→按钮的层级约束。
     *
     * @param cmd 创建入参（格式校验已由 Bean Validation 完成）
     * @return 已落库的菜单实体（含自增 id）
     * @throws BusinessException 父菜单不存在（错误码 603001）或层级关系非法（错误码 603003）
     */
    @Transactional
    public SysMenu create(SysMenuCreateCmd cmd) {
        long parentId = cmd.getParentId() == null ? ROOT_PARENT_ID : cmd.getParentId();
        validateHierarchy(cmd.getType(), parentId);
        SysMenu menu = new SysMenu();
        menu.setParentId(parentId);
        menu.setName(cmd.getName());
        menu.setType(cmd.getType());
        menu.setPath(cmd.getPath());
        menu.setComponent(cmd.getComponent());
        menu.setPerms(cmd.getPerms());
        menu.setIcon(cmd.getIcon());
        menu.setSort(cmd.getSort());
        menu.setVisible(cmd.getVisible());
        menu.setEnabled(cmd.getEnabled());
        return menuRepository.save(menu);
    }

    /**
     * 按 id 查询菜单详情。
     *
     * @param id 菜单 id
     * @return 菜单实体
     * @throws BusinessException 菜单不存在或已软删（错误码 603001）
     */
    @Transactional(readOnly = true)
    public SysMenu getById(Long id) {
        return menuRepository.findById(id)
                             .orElseThrow(() -> new BusinessException(BusinessErrorCode.MENU_NOT_FOUND));
    }

    /**
     * 按 id 更新菜单档案（parentId/type 不可修改，见 SysMenuUpdateCmd 约定）。
     *
     * <p>实现说明：依托 JPA 持久化上下文脏检查，事务提交时自动生成 UPDATE，
     * 无需显式 save；updateTime 由 Hibernate 时间戳注解自动刷新。
     *
     * @param id  菜单 id
     * @param cmd 更新入参（格式校验已由 Bean Validation 完成）
     * @return 更新后的菜单实体
     * @throws BusinessException 菜单不存在或已软删（错误码 603001）
     */
    @Transactional
    public SysMenu update(Long id, SysMenuUpdateCmd cmd) {
        SysMenu menu = getById(id);
        menu.setName(cmd.getName());
        menu.setPath(cmd.getPath());
        menu.setComponent(cmd.getComponent());
        menu.setPerms(cmd.getPerms());
        menu.setIcon(cmd.getIcon());
        menu.setSort(cmd.getSort());
        menu.setVisible(cmd.getVisible());
        menu.setEnabled(cmd.getEnabled());
        return menu;
    }

    /**
     * 按 id 软删除菜单（deleted 置为本行主键 id，物理数据保留供审计）。
     *
     * <p>业务校验：存在直接子菜单时禁止删除（{@code countByParentId} 前置检查），
     * 必须先删除/迁移全部子节点，防止子树成为孤儿节点；
     * 软删后挂到本菜单下的存量子菜单由下次删除时的影响检查兜底拦截。
     *
     * @param id 菜单 id
     * @throws BusinessException 菜单不存在或已软删（错误码 603001）；存在子菜单（错误码 603002）
     */
    @Transactional
    public void delete(Long id) {
        if (menuRepository.countByParentId(id) > 0) {
            throw new BusinessException(BusinessErrorCode.MENU_HAS_CHILDREN);
        }
        if (menuRepository.softDelete(id) == 0) {
            throw new BusinessException(BusinessErrorCode.MENU_NOT_FOUND);
        }
    }

    /**
     * 获取完整菜单树。
     *
     * <p>实现说明：一次查询全部未软删菜单（{@code @SQLRestriction} 自动过滤），
     * 平铺转树使用通用 {@link TreeNode#buildTree(List)}（parentId=0 映射为 null 即根节点），
     * 同层节点按数据库自然顺序（通常为主键序）保持稳定；
     * 状态为 DISABLED 的节点以 disabled=true 输出，由前端决定展示样式。
     *
     * @return 根节点列表（可能多个）；无菜单时返回空列表
     */
    @Transactional(readOnly = true)
    public List<TreeNode<Long>> tree() {
        return TreeNode.buildTree(menuRepository.findAll()
                                                 .stream()
                                                 .map(SysMenuVO::toTreeNode)
                                                 .toList());
    }

    /**
     * 分页条件查询菜单列表（平铺结构，不含树形装配）。
     *
     * <p>实现说明：Query-by-Example——以过滤参数构建探针实体，
     * name 走 {@code CONTAINING} 模糊匹配（忽略大小写），type/status 枚举精确匹配，
     * 未设置的字段自动忽略；软删行由 {@code @SQLRestriction} 全局过滤。
     * 探针实体的 parentId/sort/deleted/visible 存在非空默认值，须显式加入忽略路径，
     * 防止默认值被误当过滤条件。
     *
     * @param query 业务过滤条件（字段格式校验已由 Bean Validation 完成）
     * @param page  分页与排序参数（构造期已归一化，此处仅校验排序字段安全性）
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<SysMenu> page(SysMenuQuery query, PageQuery page) {
        page.validate();
        SysMenu probe = new SysMenu();
        probe.setName(query.getName());
        probe.setType(query.getType());
        probe.setEnabled(query.getEnabled());
        ExampleMatcher matcher = ExampleMatcher.matching()
                                               .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
                                               .withIgnoreCase()
                                               .withIgnorePaths("id", "parentId", "path", "component", "perms",
                                                       "icon", "sort", "createTime", "updateTime", "deleted", "visible");
        PageRequest pageRequest = page.toPageRequest();
        return menuRepository.findAll(Example.of(probe, matcher), pageRequest);
    }

    /**
     * 校验创建菜单的父子层级规则（目录 → 菜单 → 按钮）。
     *
     * <p>规则明细：根节点（parentId=0）仅允许目录；目录必须挂根节点；
     * 菜单的父节点必须为目录；按钮的父节点必须为菜单。
     *
     * @param type     待创建的节点类型
     * @param parentId 挂载点父菜单 id；0 表示根节点
     * @throws BusinessException 父菜单不存在（603001）；层级关系非法（603003）
     */
    private void validateHierarchy(MenuType type, long parentId) {
        if (parentId == ROOT_PARENT_ID) {
            if (type != MenuType.DIRECTORY) {
                throw new BusinessException(BusinessErrorCode.MENU_PARENT_INVALID);
            }
            return;
        }
        MenuType parentType = getById(parentId).getType();
        boolean legal = switch (type) {
            case DIRECTORY -> false;
            case MENU -> parentType == MenuType.DIRECTORY;
            case BUTTON -> parentType == MenuType.MENU;
        };
        if (!legal) {
            throw new BusinessException(BusinessErrorCode.MENU_PARENT_INVALID);
        }
    }
}
