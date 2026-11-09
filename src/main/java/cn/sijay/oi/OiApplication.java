package cn.sijay.oi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * 应用启动入口。
 *
 * <p>{@code @EnableScheduling}：启用定时任务（站内信 SSE 连接心跳保活等）。
 *
 * <p>应用默认时区固定为东八区（Asia/Shanghai）：启动最早期设置，
 * 保证日志时间戳、H2 时间存储、{@code LocalDateTime.now()} 等全部随 JVM
 * 默认时区生效，不依赖宿主机时区。
 *
 * @author sijay
 */
@EnableScheduling
@SpringBootApplication
public class OiApplication {

    /**
     * 应用默认时区：东八区。
     */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    public static void main(String[] args) {
        // 须先于 Spring 上下文初始化：时区被日志、JDBC、日期序列化等在启动期读取
        System.setProperty("user.timezone", DEFAULT_TIME_ZONE);
        TimeZone.setDefault(TimeZone.getTimeZone(DEFAULT_TIME_ZONE));
        SpringApplication.run(OiApplication.class, args);
    }

}
