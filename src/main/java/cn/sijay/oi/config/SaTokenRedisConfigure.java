package cn.sijay.oi.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoForRedisson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * sa-token 缓存存储配置：将框架默认的内存 DAO（{@code SaTokenDaoDefaultImpl}）
 * 替换为基于 Redisson 的 {@link SaTokenDaoForRedisson}。
 *
 * <p><b>作用范围</b>：登录令牌、会话数据（Session）、Token-Session、权限缓存等
 * 全部 sa-token 数据统一存放于 Redis，从而支持多实例部署时的会话共享，
 * 并保证服务重启后登录态不丢失（令牌有效期由 sa-token.timeout 控制）。
 *
 * <p><b>装配方式</b>：sa-token 的 DAO 通过静态入口 {@link SaManager#setSaTokenDao}
 * 注入（框架启动时一次性读取），而非依赖容器按类型注入；因此在 {@link Bean}
 * 方法内除返回 Bean 外，还需显式调用该静态方法完成替换。Bean 本身保留在容器中，
 * 便于其他组件按类型引用与测试断言。
 *
 * <p><b>前置条件</b>：容器中存在 {@link RedissonClient}（由
 * {@code redisson-spring-boot-starter} 依据 {@code spring.data.redis} 配置自动装配）。
 *
 * @author sijay
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SaTokenRedisConfigure {

    /**
     * Redisson 客户端；由 starter 依据 spring.data.redis 配置创建。
     */
    private final RedissonClient redissonClient;

    /**
     * 注册基于 Redisson 的 sa-token 数据访问对象，并替换框架全局 DAO。
     *
     * @return sa-token DAO 实例（同时已注入 {@link SaManager}）
     */
    @Bean
    public SaTokenDao saTokenDao() {
        SaTokenDao dao = new SaTokenDaoForRedisson(redissonClient);
        SaManager.setSaTokenDao(dao);
        log.info("[SaTokenRedisConfigure] sa-token 缓存存储已切换为 Redisson（Redis）实现");
        return dao;
    }
}
