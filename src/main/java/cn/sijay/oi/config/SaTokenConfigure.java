package cn.sijay.oi.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * sa-token 全局接口鉴权配置：除公开路径外，所有接口默认需要登录态。
 *
 * <p><b>鉴权规则</b>：注册 {@link SaInterceptor}，对 {@code /**} 全路径执行登录校验
 * （{@code StpUtil.checkLogin()}），未携带有效令牌的请求抛出 {@code NotLoginException}，
 * 由 {@code GlobalExceptionHandler} 统一包装为 code=401 的 {@code Result} 响应。
 *
 * <p><b>公开路径清单</b>（豁免登录校验）：
 * <ul>
 *   <li>{@code /auth/login} —— 登录接口本身（获取令牌的唯一入口）；</li>
 *   <li>{@code /file/access/**} —— 统一文件服务的公开访问端点，凭带有效期与
 *       HMAC 签名的访问地址即可获取文件流（签名校验由 FileService 实现）；</li>
 *   <li>{@code /actuator/**} —— 运维监控端点（Spring Boot Admin 客户端自监控依赖）；</li>
 *   <li>{@code /applications/**}、{@code /instances/**} 及静态资源 —— 本应用内嵌的
 *       Spring Boot Admin 服务端端点与页面资源（客户端注册/心跳不可携带业务令牌）；</li>
 *   <li>{@code /error} —— Servlet 错误转发路径（避免错误页二次拦截）。</li>
 * </ul>
 *
 * <p>业务受保护接口无需逐个声明：默认全拦截、显式豁免公开路径，
 * 新增业务模块零配置即受登录态保护（安全默认值原则）。
 *
 * @author sijay
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    /**
     * 注册 sa-token 拦截器：全路径登录校验 + 公开路径豁免。
     *
     * <p>实现说明：校验逻辑写在 {@code SaInterceptor.handle} 回调中，
     * 通过 {@code SaRouter.match(...).notMatch(...).check(...)} 链式声明；
     * 未命中豁免清单的请求统一执行 {@link StpUtil#checkLogin()}。
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle ->
                        SaRouter.match("/**")
                                .notMatch("/auth/login")
                                .notMatch("/file/access/**")
                                .notMatch("/actuator/**")
                                .notMatch("/applications/**")
                                .notMatch("/instances/**")
                                .notMatch("/sba-settings.js")
                                .notMatch("/vendors/**")
                                .notMatch("/assets/**")
                                .notMatch("/favicon.ico")
                                .notMatch("/error")
                                .check(r -> StpUtil.checkLogin())))
                .addPathPatterns("/**");
    }
}
