package cn.sijay.oi.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JacksonConfiguration} 验证用例。
 *
 * <p>基于 Boot 的 Jackson 自动配置切片（{@link ApplicationContextRunner} +
 * {@link JacksonAutoConfiguration}）验证：
 * <ol>
 *   <li>配置类的 {@code JsonMapperBuilderCustomizer} 被应用到 Boot 自动配置的全局 ObjectMapper；</li>
 *   <li>日期时间按 {@code yyyy-MM-dd HH:mm:ss} 等标准格式序列化与反序列化；</li>
 *   <li>超过 JavaScript 安全整数范围（{@code Number.MAX_SAFE_INTEGER = 9007199254740991}）
 *       的 Long / BigInteger 以字符串输出，精度无丢失；Integer 等小数值不受影响。</li>
 * </ol>
 */
@DisplayName("Jackson 全局配置测试")
class JacksonConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withBean(JacksonConfiguration.class);

    /**
     * 测试载荷：覆盖 Long 包装类型、long 基本类型、BigInteger、Integer（对照项）与三种 java.time 类型。
     * id 取 Long.MAX_VALUE = 9223372036854775807，远超 JS 安全整数上限 9007199254740991。
     */
    record JacksonTestDto(Long id, long count, BigInteger big, Integer small,
                          LocalDateTime time, LocalDate date, LocalTime timeOnly) {
    }

    @Test
    @DisplayName("序列化：超大数字输出为字符串，日期时间按标准格式，Integer 保持数值")
    void serialize_bigNumbersAsStrings_andDateTimeFormatted() {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            String json = mapper.writeValueAsString(new JacksonTestDto(
                    Long.MAX_VALUE,
                    1234567890123456789L,
                    new BigInteger("18446744073709551616"),
                    42,
                    LocalDateTime.of(2026, 10, 29, 15, 30, 45),
                    LocalDate.of(2026, 10, 29),
                    LocalTime.of(8, 5, 3)));

            // 超大数字 → 带引号的字符串（Long.MAX_VALUE 与 2^64，均超过 JS 安全整数范围）
            assertThat(json).contains("\"id\":\"9223372036854775807\"");
            assertThat(json).contains("\"count\":\"1234567890123456789\"");
            assertThat(json).contains("\"big\":\"18446744073709551616\"");
            // 对照项：Integer 不受影响，保持数值
            assertThat(json).contains("\"small\":42");
            // 日期时间 → 统一格式
            assertThat(json).contains("\"time\":\"2026-10-29 15:30:45\"");
            assertThat(json).contains("\"date\":\"2026-10-29\"");
            assertThat(json).contains("\"timeOnly\":\"08:05:03\"");
        });
    }

    @Test
    @DisplayName("反序列化：标准格式日期时间与字符串形式的超大数字均可正确还原")
    void deserialize_formattedDateTime_andStringBigNumbers() {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            JacksonTestDto dto = mapper.readValue("""
                    {"id":"9223372036854775807","count":"1234567890123456789","big":"18446744073709551616",
                     "small":42,"time":"2026-10-29 15:30:45","date":"2026-10-29","timeOnly":"08:05:03"}
                    """, JacksonTestDto.class);

            assertThat(dto.id()).isEqualTo(Long.MAX_VALUE);
            assertThat(dto.count()).isEqualTo(1234567890123456789L);
            assertThat(dto.big()).isEqualTo(new BigInteger("18446744073709551616"));
            assertThat(dto.small()).isEqualTo(42);
            assertThat(dto.time()).isEqualTo(LocalDateTime.of(2026, 10, 29, 15, 30, 45));
            assertThat(dto.date()).isEqualTo(LocalDate.of(2026, 10, 29));
            assertThat(dto.timeOnly()).isEqualTo(LocalTime.of(8, 5, 3));
        });
    }

    @Test
    @DisplayName("往返一致性：超大数字序列化→反序列化→再序列化，精度无丢失")
    void roundTrip_noPrecisionLoss() {
        runner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            JacksonTestDto source = new JacksonTestDto(Long.MAX_VALUE, 0L,
                    new BigInteger("18446744073709551616"), 42, null, null, null);
            JacksonTestDto restored = mapper.readValue(mapper.writeValueAsString(source), JacksonTestDto.class);
            String jsonAgain = mapper.writeValueAsString(restored);

            assertThat(restored.id()).isEqualTo(Long.MAX_VALUE);
            assertThat(restored.big()).isEqualTo(new BigInteger("18446744073709551616"));
            assertThat(jsonAgain).contains("\"id\":\"9223372036854775807\"");
            assertThat(jsonAgain).contains("\"big\":\"18446744073709551616\"");
        });
    }
}
