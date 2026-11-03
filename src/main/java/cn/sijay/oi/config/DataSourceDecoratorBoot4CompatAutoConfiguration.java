package cn.sijay.oi.config;

import com.github.gavlyukovskiy.boot.jdbc.decorator.DataSourceDecoratorAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * Compatibility shim for datasource-decorator 1.11.0 on Spring Boot 4.
 *
 * <p>The starter's {@code DataSourceDecoratorAutoConfiguration} declares
 * {@code @AutoConfiguration(after = DataSourceAutoConfiguration.class)} using the
 * Boot 3 package {@code org.springframework.boot.autoconfigure.jdbc}, which no longer
 * exists in Boot 4 (moved to {@code org.springframework.boot.jdbc.autoconfigure}).
 * The broken ordering hint makes its {@code @ConditionalOnBean(DataSource.class)}
 * evaluate before the DataSource bean definition exists, silently disabling decoration.
 *
 * <p>This shim re-registers the same configuration with the correct Boot 4 ordering.
 * The starter's own registration is excluded in application.yaml.
 *
 * @see DataSourceDecoratorAutoConfiguration
 */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnProperty(name = "decorator.datasource.enabled", havingValue = "true", matchIfMissing = true)
@Import(DataSourceDecoratorAutoConfiguration.class)
public class DataSourceDecoratorBoot4CompatAutoConfiguration {
}
