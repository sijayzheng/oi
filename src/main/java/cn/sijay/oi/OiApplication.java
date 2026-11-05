package cn.sijay.oi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动入口。
 *
 * <p>{@code @EnableScheduling}：启用定时任务（站内信 SSE 连接心跳保活等）。
 *
 * @author sijay
 */
@EnableScheduling
@SpringBootApplication
public class OiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OiApplication.class, args);
    }

}
