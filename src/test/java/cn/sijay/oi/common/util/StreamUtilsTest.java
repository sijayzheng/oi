package cn.sijay.oi.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.sijay.oi.common.util.StreamUtils.countBy;
import static cn.sijay.oi.common.util.StreamUtils.distinctBy;
import static cn.sijay.oi.common.util.StreamUtils.filterNonNull;
import static cn.sijay.oi.common.util.StreamUtils.groupBy;
import static cn.sijay.oi.common.util.StreamUtils.map;
import static cn.sijay.oi.common.util.StreamUtils.page;
import static cn.sijay.oi.common.util.StreamUtils.sumBy;
import static cn.sijay.oi.common.util.StreamUtils.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link StreamUtils} 验证用例：覆盖正常场景、空输入场景（null / 空集合）、边界值场景，
 * 并验证无副作用（不修改入参集合）。
 */
@DisplayName("Stream 工具类测试")
class StreamUtilsTest {

    /** 测试载荷对象 */
    record Item(Long id, String city, int amount) {
    }

    private static final Item A = new Item(1L, "北京", 10);
    private static final Item B = new Item(2L, "上海", 20);
    private static final Item C = new Item(3L, "北京", 30);
    private static final List<Item> ITEMS = List.of(A, B, C);

    @Nested
    @DisplayName("toMap：集合转 Map")
    class ToMapTest {

        @Test
        @DisplayName("正常场景：键值提取并保持插入顺序")
        void normal() {
            Map<Long, String> result = toMap(ITEMS, Item::id, Item::city);

            assertThat(result).containsEntry(1L, "北京").containsEntry(2L, "上海").containsEntry(3L, "北京");
            assertThat(result.keySet()).containsExactly(1L, 2L, 3L);
        }

        @Test
        @DisplayName("键重复：后出现者覆盖先出现者")
        void duplicateKeyLastWins() {
            Map<String, Item> result = toMap(ITEMS, Item::city, item -> item);

            assertThat(result.get("北京")).isEqualTo(C);
        }

        @Test
        @DisplayName("空输入：null 与空集合均返回空 Map")
        void emptyInput() {
            assertThat(toMap(null, Item::id, Item::city)).isEmpty();
            assertThat(toMap(List.<Item>of(), Item::id, Item::city)).isEmpty();
        }

        @Test
        @DisplayName("函数参数为 null：抛 NullPointerException")
        void nullFunction() {
            assertThatThrownBy(() -> toMap(ITEMS, null, Item::city)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("groupBy / countBy：分组与分组聚合")
    class GroupByTest {

        @Test
        @DisplayName("groupBy 正常场景：按城市分组且组内保序")
        void groupByNormal() {
            Map<String, List<Item>> result = groupBy(ITEMS, Item::city);

            assertThat(result.get("北京")).containsExactly(A, C);
            assertThat(result.get("上海")).containsExactly(B);
        }

        @Test
        @DisplayName("countBy 正常场景：分组计数正确")
        void countByNormal() {
            Map<String, Long> result = countBy(ITEMS, Item::city);

            assertThat(result).containsEntry("北京", 2L).containsEntry("上海", 1L);
        }

        @Test
        @DisplayName("空输入：null 与空集合均返回空 Map")
        void emptyInput() {
            assertThat(groupBy(null, Item::city)).isEmpty();
            assertThat(countBy(List.<Item>of(), Item::city)).isEmpty();
        }
    }

    @Nested
    @DisplayName("filterNonNull / distinctBy / map / sumBy")
    class ElementOpsTest {

        @Test
        @DisplayName("filterNonNull：剔除 null 元素，null 入参返回空 List")
        void filterNonNullNormal() {
            List<Item> withNull = new ArrayList<>(java.util.Arrays.asList(A, null, B));

            List<Item> result = filterNonNull(withNull);

            assertThat(result).containsExactly(A, B);
            assertThat(filterNonNull(null)).isEmpty();
        }

        @Test
        @DisplayName("distinctBy：按字段去重保留首个出现者且顺序不变")
        void distinctByNormal() {
            List<Item> duplicated = List.of(A, B, new Item(1L, "广州", 99), C);

            List<Item> result = distinctBy(duplicated, Item::id);

            assertThat(result).containsExactly(A, B, C);
        }

        @Test
        @DisplayName("map：类型转换，null 入参返回空 List")
        void mapNormal() {
            List<Long> ids = map(ITEMS, Item::id);

            assertThat(ids).containsExactly(1L, 2L, 3L);
            assertThat(map(null, Item::id)).isEmpty();
        }

        @Test
        @DisplayName("sumBy：数值求和，空输入返回 0")
        void sumByNormal() {
            assertThat(sumBy(ITEMS, Item::amount)).isEqualTo(60L);
            assertThat(sumBy(null, Item::amount)).isZero();
            assertThat(sumBy(List.<Item>of(), Item::amount)).isZero();
        }
    }

    @Nested
    @DisplayName("page：分页截取")
    class PageTest {

        @Test
        @DisplayName("正常场景：首页、末页不满页、单页容量大于总数")
        void normalPages() {
            assertThat(page(ITEMS, 1, 2)).containsExactly(A, B);
            assertThat(page(ITEMS, 2, 2)).containsExactly(C);
            assertThat(page(ITEMS, 1, 5)).containsExactly(A, B, C);
        }

        @Test
        @DisplayName("边界值：页码超出范围返回空 List，且返回值是独立副本")
        void outOfRangeAndCopy() {
            assertThat(page(ITEMS, 9, 10)).isEmpty();

            List<Item> slice = page(ITEMS, 1, 2);
            slice.clear();
            assertThat(ITEMS).hasSize(3); // 原列表不受影响
        }

        @Test
        @DisplayName("非法参数：pageNum / pageSize 小于 1 抛 IllegalArgumentException")
        void invalidParams() {
            assertThatThrownBy(() -> page(ITEMS, 0, 10)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> page(ITEMS, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("空输入：null 列表在参数合法时返回空 List")
        void emptyInput() {
            assertThat(page(null, 1, 10)).isEmpty();
        }
    }

    @Test
    @DisplayName("无副作用：所有操作不修改入参集合")
    void noSideEffects() {
        List<Item> mutable = new ArrayList<>(ITEMS);
        List<Item> snapshot = new ArrayList<>(mutable);

        toMap(mutable, Item::id, Item::city);
        groupBy(mutable, Item::city);
        countBy(mutable, Item::city);
        filterNonNull(mutable);
        distinctBy(mutable, Item::id);
        map(mutable, Item::id);
        sumBy(mutable, Item::amount);
        page(mutable, 1, 2);

        assertThat(mutable).containsExactlyElementsOf(snapshot);
    }
}
