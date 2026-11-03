package cn.sijay.oi.common.util;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Objects;

/**
 * Jackson JSON 处理工具类，提供与 Spring 生态完全一致的 JSON 序列化/反序列化静态入口。
 *
 * <p><b>实例获取与全局配置</b>：内部通过 {@link SpringUtils} 获取 Spring 容器统一管理的
 * {@link JsonMapper} Bean（双检锁 + volatile 单例持有，仅初始化一次，线程安全），
 * 因此本工具类的输入输出与 Controller 层请求/响应体的 JSON 行为<b>完全一致</b>。
 * 全局序列化规则（日期时间 {@code yyyy-MM-dd HH:mm:ss}、LocalDate/LocalTime 格式、
 * Long/BigInteger 转字符串防精度丢失、忽略未知字段等）统一收敛在
 * {@code cn.sijay.oi.config.JacksonConfiguration} 中维护，本类不做重复配置——
 * 单一配置源是保证"工具类输出 == 接口输出"的前提。
 *
 * <p>通用策略说明：
 * <ul>
 *   <li><b>空值序列化</b>：null 字段输出为 JSON null（Spring 侧默认 include 全部字段），
 *       保证前后端字段结构稳定；</li>
 *   <li><b>空集合/空字符串</b>：按 JSON 原生语义输出 {@code []} 与 {@code ""}，不做转换；</li>
 *   <li><b>格式化输出</b>：{@link #toJson(Object)} 始终紧凑无缩进（接口传输用），
 *       {@link #toPrettyJson(Object)} 按调用单独启用缩进（日志/调试用），
 *       不改变全局 Mapper 配置。</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * SysUserVO vo = JacksonUtils.parse(json, SysUserVO.class);
 * List<SysUserVO> list = JacksonUtils.parse(json, new TypeReference<List<SysUserVO>>() {});
 * String json = JacksonUtils.toJson(vo);
 * String pretty = JacksonUtils.toPrettyJson(vo);   // 日志排查用
 * Map<String, Object> map = JacksonUtils.parseToMap(json);
 * if (JacksonUtils.isValidJson(input)) { ... }
 * }</pre>
 *
 * @author sijay
 */
public final class JacksonUtils {

    /**
     * Spring 容器 JsonMapper 的单例缓存；volatile 保证多线程可见性，
     * 双检锁保证仅初始化一次且不在锁外发布半初始化实例。
     */
    private static volatile JsonMapper mapper;

    /**
     * 私有构造方法；工具类禁止实例化。
     */
    private JacksonUtils() {
        throw new UnsupportedOperationException("工具类禁止实例化");
    }

    /**
     * 获取 Spring 容器统一管理的 JsonMapper（懒加载单例）。
     *
     * @return 与 Spring 生态配置一致的 JsonMapper 实例
     * @throws IllegalStateException Spring 上下文尚未初始化（容器未启动完成即调用本工具类）
     */
    private static JsonMapper getMapper() {
        JsonMapper current = mapper;
        if (current == null) {
            synchronized (JacksonUtils.class) {
                current = mapper;
                if (current == null) {
                    current = SpringUtils.getBean(JsonMapper.class);
                    mapper = current;
                }
            }
        }
        return current;
    }

    /**
     * 将 JSON 字符串反序列化为指定类型的 Java 对象。
     *
     * <p>使用场景：简单类型/POJO 的转换（日期格式、Long 转字符串等遵循全局配置）。
     * 复杂泛型嵌套（如 List&lt;SysUserVO&gt;）请使用
     * {@link #parse(String, TypeReference)}，避免仅凭 Class 丢失泛型信息。
     *
     * @param json JSON 字符串；null 或空白字符串抛 IllegalArgumentException
     * @param type 目标类型；不允许为 null
     * @param <T>  目标类型
     * @return 反序列化后的 Java 对象
     * @throws IllegalArgumentException JSON 为 null/空白、格式非法、或与目标类型不匹配时抛出，
     *                                  异常信息携带目标类型与 JSON 摘要，便于定位
     */
    public static <T> T parse(@Nullable String json, @NonNull Class<T> type) {
        Objects.requireNonNull(type, "目标类型 type 不允许为 null");
        requireJson(json, "parse(String, Class<T>)");
        try {
            return getMapper().readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                    "JSON 反序列化失败：目标类型=" + type.getName() + "，JSON=" + abbreviate(json), e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定 TypeReference 描述的复杂类型对象。
     *
     * <p>使用场景：泛型嵌套、集合/Map 及其元素类型保留（如
     * {@code new TypeReference<List<SysUserVO>>() {}}、
     * {@code new TypeReference<Map<String, List<Long>>() {}}}）。
     *
     * @param json    JSON 字符串；null 或空白字符串抛 IllegalArgumentException
     * @param typeRef 目标类型描述（匿名子类形式）；不允许为 null
     * @param <T>     目标类型
     * @return 反序列化后的 Java 对象
     * @throws IllegalArgumentException JSON 为 null/空白、格式非法、或与目标类型不匹配时抛出，
     *                                  异常信息携带目标类型与 JSON 摘要，便于定位
     */
    public static <T> T parse(@Nullable String json, @NonNull TypeReference<T> typeRef) {
        Objects.requireNonNull(typeRef, "目标类型描述 typeRef 不允许为 null");
        requireJson(json, "parse(String, TypeReference<T>)");
        try {
            return getMapper().readValue(json, typeRef);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                    "JSON 反序列化失败：目标类型=" + typeRef.getType() + "，JSON=" + abbreviate(json), e);
        }
    }

    /**
     * 将 Java 对象序列化为紧凑格式（无缩进）的 JSON 字符串。
     *
     * <p>使用场景：接口返回、日志拼接、缓存写入等常规输出；全局规则
     * （日期格式、Long 转字符串）与 Spring 接口层一致。
     *
     * @param obj 待序列化对象；null 抛 IllegalArgumentException（序列化 null 语义模糊，
     *            明确拒绝以避免调用方误判）
     * @return 紧凑 JSON 字符串
     * @throws IllegalArgumentException 对象为 null，或包含无法序列化的属性（如无 getter 的非法结构）时抛出
     */
    public static String toJson(@NonNull Object obj) {
        requireObject(obj, "toJson(Object)");
        try {
            return getMapper().writeValueAsString(obj);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON 序列化失败：对象类型=" + obj.getClass().getName(), e);
        }
    }

    /**
     * 将 Java 对象序列化为带缩进格式化的美化 JSON 字符串。
     *
     * <p>使用场景：开发调试、日志打印、生成可读配置文件；
     * 接口传输请使用 {@link #toJson(Object)}（紧凑格式，体积更小）。
     *
     * @param obj 待序列化对象；null 抛 IllegalArgumentException
     * @return 带缩进的美化 JSON 字符串
     * @throws IllegalArgumentException 对象为 null，或包含无法序列化的属性时抛出
     */
    public static String toPrettyJson(@NonNull Object obj) {
        requireObject(obj, "toPrettyJson(Object)");
        try {
            return getMapper().writer().with(SerializationFeature.INDENT_OUTPUT).writeValueAsString(obj);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON 序列化失败（美化输出）：对象类型=" + obj.getClass().getName(), e);
        }
    }

    /**
     * 待序列化对象前置校验：null 直接抛出明确参数异常（序列化 null 语义模糊，明确拒绝）。
     *
     * @param obj       待序列化对象
     * @param operation 调用方方法名，用于异常信息定位
     */
    private static void requireObject(@Nullable Object obj, String operation) {
        if (obj == null) {
            throw new IllegalArgumentException(operation + " 的待序列化对象不允许为 null");
        }
    }

    /**
     * 将 JSON 字符串反序列化为 {@code Map<String, Object>} 结构。
     *
     * <p>使用场景：动态/结构未知的 JSON 解析（如透传第三方响应）；
     * 嵌套对象转为 Map、数组转为 List、数值按 JSON 规则映射。
     * 结构已知时请优先使用 {@link #parse(String, Class)} 获得类型安全。
     *
     * @param json JSON 对象字符串（须为 JSON object 形式）；null 或空白抛 IllegalArgumentException，
     *             非 object 形式（如纯数组、标量）抛 IllegalArgumentException
     * @return 键值结构 Map；值为嵌套结构时递归转换
     * @throws IllegalArgumentException JSON 为 null/空白、格式非法或不是 JSON object 时抛出
     */
    public static Map<String, Object> parseToMap(@Nullable String json) {
        requireJson(json, "parseToMap(String)");
        try {
            return getMapper().readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                    "JSON 转 Map 失败（须为 JSON object 形式）：JSON=" + abbreviate(json), e);
        }
    }

    /**
     * 校验字符串是否为合法有效的 JSON 格式（对象、数组、标量均视为合法 JSON 值）。
     *
     * <p>使用场景：外部输入的前置校验、接口兼容性判断。
     * 本方法为校验语义：null/空白/非法格式一律返回 false，不抛异常。
     *
     * @param json 待校验字符串；允许为 null
     * @return 合法 JSON 返回 true；null、空白字符串、格式非法返回 false
     */
    public static boolean isValidJson(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            getMapper().readTree(json);
            return true;
        } catch (JacksonException e) {
            return false;
        }
    }

    /**
     * JSON 入参前置校验：null 与空白字符串直接抛出明确参数异常，避免底层 NPE。
     *
     * @param json      JSON 输入
     * @param operation 调用方方法名，用于异常信息定位
     */
    private static void requireJson(@Nullable String json, String operation) {
        if (json == null) {
            throw new IllegalArgumentException(operation + " 的 JSON 输入不允许为 null");
        }
        if (json.isBlank()) {
            throw new IllegalArgumentException(operation + " 的 JSON 输入不允许为空白字符串");
        }
    }

    /**
     * 异常信息中的 JSON 摘要：超长 JSON 截断至 200 字符，防止异常日志膨胀。
     *
     * @param json 原始 JSON
     * @return 摘要字符串
     */
    private static String abbreviate(String json) {
        return json.length() <= 200 ? json : json.substring(0, 200) + "...(已截断)";
    }
}
