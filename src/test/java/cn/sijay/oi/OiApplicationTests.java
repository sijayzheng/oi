package cn.sijay.oi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 应用上下文加载冒烟测试。
 *
 * <p>使用自包含的内存 H2 环境验证上下文加载：JPA 实体（SysUser）需要真实的
 * EntityManagerFactory，若沿用文件库 {@code data/oi.mv.db} 会与本地库文件状态
 * （锁、密码、schema 版本）耦合导致偶发失败；内存库每上下文独立且 schema 必现。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:contextload;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class OiApplicationTests {

    @Test
    void contextLoads() {
    }

}
