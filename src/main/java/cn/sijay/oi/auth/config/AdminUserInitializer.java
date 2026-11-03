package cn.sijay.oi.auth.config;

import cn.sijay.oi.sys.entity.SysUser;
import cn.sijay.oi.sys.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 超管账号初始化器：应用启动时若系统中不存在任何用户，自动创建种子超管。
 *
 * <p><b>必要性</b>：全局鉴权开启后所有业务接口均需登录态，若库中无用户将无人
 * 能够登录（系统锁死）；本初始化器保证系统自举可用。
 *
 * <p><b>种子账号</b>：username=admin、password=123456（BCrypt 落库，明文不存储）、
 * status=NORMAL、createBy=0（系统初始化）。
 * <b>生产环境必须在首次登录后立即修改默认密码。</b>
 *
 * <p>幂等性：仅当无任何未删除用户时才创建；已有用户（含手动建的）不做任何变更。
 *
 * @author sijay
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

    /**
     * 种子超管登录名。
     */
    private static final String ADMIN_USERNAME = "admin";

    /**
     * 种子超管初始密码（明文）；落库前经 BCrypt 加密。
     */
    private static final String ADMIN_INITIAL_PASSWORD = "123456";

    private final SysUserRepository userRepository;

    /**
     * 启动时检查并初始化种子超管。
     *
     * @param args 启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        SysUser admin = new SysUser();
        admin.setUsername(ADMIN_USERNAME);
        admin.setPassword(encoder.encode(ADMIN_INITIAL_PASSWORD));
        admin.setNickname("管理员");
        // 占位邮箱取排序末位值：保证按 email 升序分页时 admin 稳定排最后（NULL 排序在不同库中不确定）
        admin.setEmail("zz@test.com");
        admin.setEnabled(true);
        admin.setCreateBy(0L);
        userRepository.save(admin);
        log.info("[AdminUserInitializer] 系统无用户，已创建种子超管: username={}（初始密码 {}，请登录后立即修改）",
                ADMIN_USERNAME, ADMIN_INITIAL_PASSWORD);
    }
}
