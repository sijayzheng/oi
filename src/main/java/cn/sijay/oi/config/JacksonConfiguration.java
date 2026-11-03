package cn.sijay.oi.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局序列化配置（Spring Boot 4 / Jackson 3）。
 *
 * <p>通过 {@link JsonMapperBuilderCustomizer} 定制 Boot 自动配置的 {@code JsonMapper}
 * （即全局 ObjectMapper），所有 {@code @RestController} 接口响应与请求体解析自动生效，
 * 无需在单个字段上重复添加 {@code @JsonFormat} 注解。
 *
 * <p>定制内容：
 * <ol>
 *   <li><b>日期时间</b>（java.time 包，日期时间类字段统一按以下格式序列化/反序列化）：
 *       <ul>
 *         <li>{@link LocalDateTime}：{@code yyyy-MM-dd HH:mm:ss}</li>
 *         <li>{@link LocalDate}：{@code yyyy-MM-dd}</li>
 *         <li>{@link LocalTime}：{@code HH:mm:ss}</li>
 *       </ul></li>
 *   <li><b>超大数字防精度丢失</b>：{@link Long}（含基本类型 long）与 {@link BigInteger}
 *       序列化为 JSON 字符串。JavaScript 的 Number 仅能安全表示
 *       {@code Number.MAX_SAFE_INTEGER = 2^53-1 = 9007199254740991}，
 *       超过该范围的整数（如雪花 ID、Long.MAX_VALUE）以数值传输会被前端静默舍入；
 *       以字符串传输可完整保留精度，前端按字符串处理即可。
 *       反序列化时 Jackson 自动将 JSON 字符串还原为 Long/BigInteger，Java 侧使用方式不变。</li>
 * </ol>
 *
 * <p>说明：Boot 4 默认使用 Jackson 3（{@code tools.jackson.*}），java.time 支持已内置于
 * jackson-databind（{@code tools.jackson.databind.ext.javatime}），本配置注册的
 * {@link SimpleModule} 在内置模块之后应用，其序列化规则覆盖默认实现。
 *
 * @author sijay
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfiguration {

    /**
     * 日期时间标准格式；LocalDateTime 的序列化与反序列化统一使用。
     */
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 日期标准格式；LocalDate 的序列化与反序列化统一使用。
     */
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 时间标准格式；LocalTime 的序列化与反序列化统一使用。
     */
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 定制 Boot 自动配置的全局 JsonMapper（ObjectMapper）。
     *
     * <p>使用场景：所有 Controller 接口的 JSON 序列化/反序列化；
     * Boot 收集所有 {@link JsonMapperBuilderCustomizer} Bean 并按序应用到自动配置的 Mapper 上。
     *
     * @return 注册自定义序列化规则的定制器
     */
    @Bean
    public JsonMapperBuilderCustomizer oiJacksonCustomizer() {
        SimpleModule module = new SimpleModule("oiJacksonModule");

        // 日期时间：统一 yyyy-MM-dd HH:mm:ss / yyyy-MM-dd / HH:mm:ss
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
        module.addSerializer(LocalDate.class, new LocalDateSerializer(DATE_FORMATTER));
        module.addDeserializer(LocalDate.class, new LocalDateDeserializer(DATE_FORMATTER));
        module.addSerializer(LocalTime.class, new LocalTimeSerializer(TIME_FORMATTER));
        module.addDeserializer(LocalTime.class, new LocalTimeDeserializer(TIME_FORMATTER));

        // 超大数字：Long / long / BigInteger 序列化为字符串，避免前端 JS 精度丢失
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        module.addSerializer(BigInteger.class, ToStringSerializer.instance);

        return builder -> builder.addModule(module);
    }
}
