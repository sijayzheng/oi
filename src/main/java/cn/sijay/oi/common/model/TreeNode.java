package cn.sijay.oi.common.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 树形结构节点通用模型，封装前端 Tree/级联/部门树等组件的标准节点结构（record 不可变对象）。
 *
 * <p>标准字段：id、label、parentId、disabled、children（children 永不为 null，叶子为空列表）。
 *
 * <p><b>扩展自定义字段</b>：record 为隐式 final，不可继承；需要附加字段（如菜单树的 icon/perms）时，
 * 建议定义自有节点 record 并组合本类型，或装配后映射为目标节点 DTO：
 * <pre>{@code
 * public record MenuTreeNode(TreeNode<Long> base, String icon, String perms) {}
 * }</pre>
 *
 * <p>使用示例（平铺数据 → 树 → 遍历/查找）：
 * <pre>{@code
 * List<MenuTreeNode> flat = menuMapper.selectAll().stream().map(...).toList();
 * List<MenuTreeNode> tree = TreeNode.buildTree(flat);
 * tree.forEach(root -> root.traverse(node -> log.debug("{}", node.label())));
 * MenuTreeNode target = tree.get(0).findById(42L);
 * }</pre>
 *
 * <p><b>约束</b>：buildTree 假定数据无环且 id 唯一（重复 id 后者覆盖前者）；
 * parentId 指向的父节点不在列表中，或 parentId 等于自身 id 时，该节点按根节点处理。
 * children 传入后内容可变（addChild/buildTree 直接修改列表内容），请勿传入不可变列表（如 List.of）；
 * record 组件不可变，{@link #addChild} 通过 {@link #withParentId} 回填父节点 id，
 * 树内节点与调用方持有的子节点实例可能不同（值等价、parentId 已回填）。
 *
 * @param id       节点唯一标识；与数据库主键或业务编码对应，同一棵树内唯一
 * @param label    节点显示文本；面向用户展示（如菜单名、部门名）
 * @param parentId 父节点 id；根节点为 null
 * @param disabled 是否禁用；禁用节点前端展示但不可选中，未传归一化为 false
 * @param children 子节点列表；永不为 null，叶子节点即 children 为空
 * @param <ID>     节点 id 类型；常用 Long（数据库主键）、String（编码）
 * @author sijay
 */
public record TreeNode<ID>(ID id, String label, ID parentId, Boolean disabled, List<TreeNode<ID>> children) {

    /**
     * 紧凑构造器：children 为 null 时初始化为空可变列表（保证"永不为 null"约定），
     * disabled 为 null 时归一化为 false（保证"永不为 null"的默认值配置）。
     */
    public TreeNode {
        if (children == null) {
            children = new ArrayList<>();
        }
        if (disabled == null) {
            disabled = Boolean.FALSE;
        }
    }

    /**
     * 构建指定 id/label 的节点（parentId 为 null 即根节点，其余字段取默认值）。
     *
     * @param id    节点 id
     * @param label 节点显示文本
     */
    public TreeNode(ID id, String label) {
        this(id, label, null, Boolean.FALSE, new ArrayList<>());
    }

    /**
     * 返回带指定 parentId 的副本（record 不可变，回填父节点 id 用）。
     *
     * @param parentId 新的父节点 id
     * @return parentId 更新后的新节点实例；children 列表引用与原节点共享
     */
    public TreeNode<ID> withParentId(ID parentId) {
        return new TreeNode<>(id, label, parentId, disabled, children);
    }

    /**
     * 判断是否叶子节点。
     *
     * @return children 为空时返回 true
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * 添加子节点，并自动回填子节点的 parentId（仅当子节点未显式设置 parentId 且本节点 id 非 null）。
     *
     * <p>使用场景：手工逐层装配树结构。
     *
     * @param child 子节点；为 null 时忽略
     * @return 本节点，支持链式调用
     */
    public TreeNode<ID> addChild(TreeNode<ID> child) {
        if (child == null) {
            return this;
        }
        children.add(child.parentId() == null && id != null ? child.withParentId(id) : child);
        return this;
    }

    /**
     * 先序深度优先递归遍历（含自身），对每个节点执行给定操作。
     *
     * <p>使用场景：收集全部节点、按层级渲染、级联更新状态等。
     *
     * @param visitor 节点访问器；为 null 时忽略
     */
    public void traverse(Consumer<TreeNode<ID>> visitor) {
        if (visitor == null) {
            return;
        }
        visitor.accept(this);
        children.forEach(child -> child.traverse(visitor));
    }

    /**
     * 按 id 递归查找节点（含自身），先序优先。
     *
     * <p>使用场景：从树中定位目标节点后做局部操作（如禁用、勾选回显）。
     *
     * @param id 目标节点 id
     * @return 命中的节点；未找到返回 null
     */
    public TreeNode<ID> findById(ID id) {
        if (Objects.equals(this.id, id)) {
            return this;
        }
        for (TreeNode<ID> child : children) {
            TreeNode<ID> found = child.findById(id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * 由平铺节点列表构建树（静态工厂）。
     *
     * <p>规则：parentId 为 null、parentId 等于自身 id、或父节点不在列表中的节点视为根节点；
     * 同层节点保持入参列表顺序（LinkedHashMap 保序）。
     *
     * <p>使用场景：数据库平铺查询结果（SELECT 全表后内存组装）一次性转树，
     * 复杂度 O(n)，避免前端递归拼装。
     *
     * @param nodes 平铺节点列表；须无环、id 唯一（重复 id 后者覆盖前者）
     * @param <ID>  节点 id 类型
     * @return 根节点列表（可能多个）；入参为空时返回空列表
     */
    public static <ID> List<TreeNode<ID>> buildTree(List<TreeNode<ID>> nodes) {
        List<TreeNode<ID>> roots = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            return roots;
        }
        LinkedHashMap<ID, TreeNode<ID>> byId = new LinkedHashMap<>();
        nodes.forEach(node -> byId.put(node.id(), node));
        for (TreeNode<ID> node : nodes) {
            TreeNode<ID> parent = node.parentId() == null ? null : byId.get(node.parentId());
            if (parent == null || parent == node) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }

    /**
     * 重写 toString 排除 children：深层树的字符串化易栈溢出，且日志中无需完整层级。
     */
    @Override
    public String toString() {
        return "TreeNode[id=%s, label=%s, parentId=%s, disabled=%s, children=%d]"
                .formatted(id, label, parentId, disabled, children.size());
    }
}
