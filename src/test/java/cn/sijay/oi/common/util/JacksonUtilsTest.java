package cn.sijay.oi.common.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JacksonUtils} 验证用例。
 *
 * <p>通过真实 Spring 上下文（JacksonAutoConfiguration + 项目 JacksonConfiguration +
 * SpringUtils）加载 Spring 管理的 JsonMapper，验证工具类与 Spring 生态配置一致；
 * 用例按 @Order 执行：先完成初始化相关断言，最后验证"上下文未初始化"边界场景。
 */
@DisplayName("Jackson 工具类测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JacksonUtilsTest {

    /** 简单载荷（record，字段顺序即序列化顺序） */
    record Item(String name, Integer age) {
    }

    /** 带日期时间与超限 Long 的载荷，用于验证 Spring 全局配置生效 */
    record Sample(LocalDateTime time, Long id) {
    }

    /** getter 抛出运行时异常的载荷，用于触发序列化失败分支 */
    static class Broken {
        public String getName() {
            throw new IllegalStateException("getter 内部错误");
        }
    }

    private static AnnotationConfigApplicationContext springContext;

    @BeforeAll
    static void setUpContext() {
        springContext = new AnnotationConfigApplicationContext();
        springContext.register(SpringUtils.class, cn.sijay.oi.config.JacksonConfiguration.class,
                JacksonAutoConfiguration.class);
        springContext.refresh();
    }

    @AfterAll
    static void tearDownContext() {
        springContext.close();
    }

    @Test
    @Order(1)
    @DisplayName("实例一致性：使用 Spring 容器管理的 JsonMapper，全局配置（日期格式/Long 转字符串）生效")
    void mapperIsSpringManaged() {
        String json = JacksonUtils.toJson(new Sample(LocalDateTime.of(2026, 10, 29, 15, 30, 45), Long.MAX_VALUE));

        assertThat(json).contains("\"time\":\"2026-10-29 15:30:45\"");
        assertThat(json).contains("\"id\":\"9223372036854775807\"");

        // Spring 容器中的 JsonMapper Bean 确实存在且被工具类使用
        assertThat(springContext.getBean(JsonMapper.class)).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("parse(Class)：正常往返转换")
    void parseByClass() {
        Item source = new Item("张三", 20);
        String json = JacksonUtils.toJson(source);

        Item parsed = JacksonUtils.parse(json, Item.class);

        assertThat(parsed).isEqualTo(source);
    }

    @Test
    @Order(3)
    @DisplayName("parse(TypeReference)：泛型嵌套与复杂集合类型保留")
    void parseByTypeReference() {
        String json = "[{\"name\":\"张三\",\"age\":20},{\"name\":\"李四\",\"age\":30}]";

        List<Item> list = JacksonUtils.parse(json, new TypeReference<List<Item>>() {
        });
        assertThat(list).containsExactly(new Item("张三", 20), new Item("李四", 30));

        Map<String, List<Integer>> nested = JacksonUtils.parse(
                "{\"a\":[1,2],\"b\":[3]}", new TypeReference<Map<String, List<Integer>>>() {
                });
        assertThat(nested).containsEntry("a", List.of(1, 2)).containsEntry("b", List.of(3));
    }

    @Test
    @Order(4)
    @DisplayName("toJson：紧凑格式无缩进")
    void toJsonCompact() {
        String json = JacksonUtils.toJson(new Item("张三", 20));

        assertThat(json).isEqualTo("{\"name\":\"张三\",\"age\":20}");
        assertThat(json).doesNotContain("\n");
    }

    @Test
    @Order(5)
    @DisplayName("toPrettyJson：带缩进美化输出，且可完整解析回原对象")
    void toPrettyJson() {
        Item source = new Item("张三", 20);
        String pretty = JacksonUtils.toPrettyJson(source);

        assertThat(pretty).contains("\n");
        assertThat(JacksonUtils.parse(pretty, Item.class)).isEqualTo(source);
    }

    @Test
    @Order(6)
    @DisplayName("parseToMap：动态结构解析，嵌套数组转 List")
    void parseToMap() {
        Map<String, Object> map = JacksonUtils.parseToMap("{\"name\":\"张三\",\"count\":1,\"tags\":[\"a\",\"b\"]}");

        assertThat(map).containsEntry("name", "张三").containsEntry("count", 1);
        assertThat(map.get("tags")).isEqualTo(List.of("a", "b"));
    }

    @Test
    @Order(7)
    @DisplayName("isValidJson：对象/数组/标量合法，null/空白/非法格式返回 false")
    void isValidJson() {
        assertThat(JacksonUtils.isValidJson("{\"a\":1}")).isTrue();
        assertThat(JacksonUtils.isValidJson("[1,2]")).isTrue();
        assertThat(JacksonUtils.isValidJson("123")).isTrue();
        assertThat(JacksonUtils.isValidJson("\"text\"")).isTrue();
        assertThat(JacksonUtils.isValidJson("true")).isTrue();

        assertThat(JacksonUtils.isValidJson(null)).isFalse();
        assertThat(JacksonUtils.isValidJson("")).isFalse();
        assertThat(JacksonUtils.isValidJson("   ")).isFalse();
        assertThat(JacksonUtils.isValidJson("{broken")).isFalse();
        assertThat(JacksonUtils.isValidJson("abc")).isFalse();
    }

    @Test
    @Order(8)
    @DisplayName("参数校验：null/空白输入抛 IllegalArgumentException，函数参数为 null 抛 NPE")
    void parameterValidation() {
        assertThatThrownBy(() -> JacksonUtils.parse(null, Item.class))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不允许为 null");
        assertThatThrownBy(() -> JacksonUtils.parse("   ", Item.class))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("空白字符串");
        assertThatThrownBy(() -> JacksonUtils.parse("{\"a\":1}", (Class<Item>) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> JacksonUtils.parse("{\"a\":1}", (TypeReference<Item>) null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> JacksonUtils.toJson(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不允许为 null");
        assertThatThrownBy(() -> JacksonUtils.toPrettyJson(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> JacksonUtils.parseToMap(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JacksonUtils.parseToMap(""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("空白字符串");
    }

    @Test
    @Order(9)
    @DisplayName("异常包装：非法格式/类型不匹配统一包装为 IllegalArgumentException，信息含上下文")
    void exceptionWrapping() {
        assertThatThrownBy(() -> JacksonUtils.parse("{broken", Item.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("反序列化失败")
                .hasMessageContaining("Item");

        // 非 object 形式的 JSON 转 Map 失败
        assertThatThrownBy(() -> JacksonUtils.parseToMap("[1,2]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object");

        assertThatThrownBy(() -> JacksonUtils.toJson(new Broken()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("序列化失败");
    }

    @Test
    @Order(10)
    @DisplayName("边界场景：Spring 上下文未初始化时抛 IllegalStateException（由 SpringUtils 抛出）")
    void contextNotInitialized() throws Exception {
        Field mapperField = JacksonUtils.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        Field contextField = SpringUtils.class.getDeclaredField("context");
        contextField.setAccessible(true);
        Object originalMapper = mapperField.get(null);
        Object originalContext = contextField.get(null);
        try {
            mapperField.set(null, null);
            contextField.set(null, null);

            assertThatThrownBy(() -> JacksonUtils.toJson(new Item("张三", 20)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("尚未初始化");
        } finally {
            mapperField.set(null, originalMapper);
            contextField.set(null, originalContext);
        }
    }
}
