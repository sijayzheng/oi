package cn.sijay.oi.common.model;

import cn.sijay.oi.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 通用基础类验证用例：{@link KeyValuePair} / {@link TripleObject} / {@link SelectOption} /
 * {@link PageQuery} / {@link TreeNode}。
 */
@DisplayName("通用基础类测试")
class CommonModelTest {

    // ==================== KeyValuePair / TripleObject ====================

    @Test
    @DisplayName("KeyValuePair：静态工厂构建，泛型访问免强转，record 不可变")
    void keyValuePair_typeSafeAccess() {
        KeyValuePair<Long, String> pair = KeyValuePair.of(1L, "正常");

        assertThat(pair.key()).isEqualTo(1L);
        assertThat(pair.value()).isEqualTo("正常");
        // record 不可变：修改值需构建新实例
        KeyValuePair<Long, String> disabled = KeyValuePair.of(1L, "禁用");
        assertThat(disabled.value()).isEqualTo("禁用");
    }

    @Test
    @DisplayName("TripleObject：三位语义依次为主数据/关联数据/补充数据")
    void tripleObject_threeSlots() {
        TripleObject<Long, String, Boolean> triple = TripleObject.of(1L, "sijay", null);

        assertThat(triple.first()).isEqualTo(1L);
        assertThat(triple.second()).isEqualTo("sijay");
        assertThat(triple.third()).isNull();
    }

    // ==================== SelectOption ====================

    @Test
    @DisplayName("SelectOption：of 工厂填充 value/label 两要素")
    void selectOption_of() {
        SelectOption<Long> option = SelectOption.of(1L, "正常");

        assertThat(option.value()).isEqualTo(1L);
        assertThat(option.label()).isEqualTo("正常");
    }

    // ==================== PageQuery ====================

    @Test
    @DisplayName("PageQuery：未传参数归一化为默认值 pageNum=1 / pageSize=10 / DESC，偏移量计算正确")
    void pageQuery_defaults_andOffset() {
        PageQuery query = new PageQuery(null, null, null, null);

        assertThat(query.pageNum()).isEqualTo(1);
        assertThat(query.pageSize()).isEqualTo(10);
        assertThat(query.desc()).isTrue();
        assertThat(query.offset()).isZero();

        PageQuery custom = new PageQuery(3, 20, null, true);
        assertThat(custom.offset()).isEqualTo(40);
    }

    @Test
    @DisplayName("PageQuery：越界页码/条数在构造期静默归一化")
    void pageQuery_normalize() {
        PageQuery query = new PageQuery(0, 500, null, false);

        assertThat(query.pageNum()).isEqualTo(1);
        assertThat(query.pageSize()).isEqualTo(100);
        assertThat(query.desc()).isFalse();
    }

    @Test
    @DisplayName("PageQuery.validate：恶意排序字段抛业务异常 600001")
    void pageQuery_validate_rejectsInjection() {
        PageQuery query = new PageQuery(null, null, "id; DROP TABLE sys_user", null);

        assertThatThrownBy(query::validate)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(600001);
    }

    @Test
    @DisplayName("PageQuery.toPageRequest：页码 0 基换算，sortField 为 null 时不加排序，指定字段按 desc 方向")
    void pageQuery_toPageRequest() {
        // 未指定排序：不加任何排序（数据库自然顺序），页码 3 → 索引 2
        var unsorted = new PageQuery(3, 20, null, null).toPageRequest();
        assertThat(unsorted.getPageNumber()).isEqualTo(2);
        assertThat(unsorted.getPageSize()).isEqualTo(20);
        assertThat(unsorted.getSort().isUnsorted()).isTrue();

        // 指定 sortField + desc=false：该字段升序
        var custom = new PageQuery(1, 10, "createTime", false).toPageRequest();
        assertThat(custom.getSort().getOrderFor("createTime"))
                .extracting(org.springframework.data.domain.Sort.Order::getDirection)
                .isEqualTo(org.springframework.data.domain.Sort.Direction.ASC);
    }

    // ==================== TreeNode ====================

    @Test
    @DisplayName("TreeNode.buildTree：三层平铺数据正确装配，孤儿节点按根处理")
    void treeNode_buildTree() {
        TreeNode<Long> root = new TreeNode<>(1L, "总部");
        TreeNode<Long> child = new TreeNode<>(2L, "研发部").withParentId(1L);
        TreeNode<Long> grandChild = new TreeNode<>(3L, "后端组").withParentId(2L);
        TreeNode<Long> orphan = new TreeNode<>(9L, "悬空节点").withParentId(99L); // 父节点不存在 → 根节点

        List<TreeNode<Long>> tree = TreeNode.buildTree(List.of(root, child, grandChild, orphan));

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).children()).containsExactly(child);
        assertThat(tree.get(0).children().get(0).children()).containsExactly(grandChild);
        assertThat(tree.get(1).label()).isEqualTo("悬空节点");
    }

    @Test
    @DisplayName("TreeNode.traverse：先序遍历覆盖全部节点且顺序正确")
    void treeNode_traversePreOrder() {
        TreeNode<Long> root = new TreeNode<>(1L, "root");
        TreeNode<Long> a = new TreeNode<>(2L, "a");
        TreeNode<Long> a1 = new TreeNode<>(3L, "a1");
        TreeNode<Long> b = new TreeNode<>(4L, "b");
        root.addChild(a);
        a.addChild(a1);
        root.addChild(b);

        List<String> visited = new java.util.ArrayList<>();
        root.traverse(node -> visited.add(node.label()));

        assertThat(visited).containsExactly("root", "a", "a1", "b");
    }

    @Test
    @DisplayName("TreeNode：addChild 自动回填 parentId（树内副本），findById 深层查找，isLeaf 判断")
    void treeNode_addChild_findById_isLeaf() {
        TreeNode<Long> root = new TreeNode<>(1L, "root");
        TreeNode<Long> child = new TreeNode<>(2L, "child");
        TreeNode<Long> grandChild = new TreeNode<>(3L, "grandChild");

        root.addChild(null); // null 子节点被忽略
        root.addChild(child);
        child.addChild(grandChild);

        // record 不可变：parentId 回填发生在树内副本上，调用方持有的原实例不变
        assertThat(root.children().get(0).parentId()).isEqualTo(1L);
        assertThat(child.parentId()).isNull();
        assertThat(root.isLeaf()).isFalse();
        assertThat(grandChild.isLeaf()).isTrue();
        assertThat(root.findById(3L).label()).isEqualTo("grandChild");
        assertThat(root.findById(999L)).isNull();
    }
}
