package cn.sijay.oi.sys.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 操作日志实体，映射表 {@code sys_log}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>关键约定（与 DDL 注释对齐）：
 * <ul>
 *   <li><b>append-only</b>：仅插入不修改，不提供更新/删除能力（审计留痕不可变）；</li>
 *   <li><b>username 快照冗余</b>：记录操作时的登录名，防止用户改名/软删后无法溯源；</li>
 *   <li><b>params 截断</b>：入库前截断至 4000 字符，防止大报文撑爆；</li>
 *   <li><b>result</b>：SUCCESS=成功 / FAIL=失败；失败场景 errorMsg 记录原因摘要。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_log")
public class SysLog {

    /**
     * 成功结果标记（result 列取值）。
     */
    public static final String RESULT_SUCCESS = "SUCCESS";

    /**
     * 失败结果标记（result 列取值）。
     */
    public static final String RESULT_FAIL = "FAIL";

    /**
     * 日志级别：DEBUG（result 列同级常量，取值约束见 DDL 注释）。
     */
    public static final String LEVEL_DEBUG = "DEBUG";

    /**
     * 日志级别：INFO。
     */
    public static final String LEVEL_INFO = "INFO";

    /**
     * 日志级别：WARN。
     */
    public static final String LEVEL_WARN = "WARN";

    /**
     * 日志级别：ERROR。
     */
    public static final String LEVEL_ERROR = "ERROR";

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 操作人用户 id；匿名/未登录请求为 NULL。
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * 操作人登录名快照；冗余存储，不随用户资料变化。
     */
    @Column(length = 50)
    private String username;

    /**
     * 业务模块标识；如 online，与 Controller 分组对应。
     */
    @Column(length = 50)
    private String module;

    /**
     * 操作动作；如 kick。
     */
    @Column(length = 50)
    private String action;

    /**
     * 日志级别；DEBUG=调试 / INFO=信息 / WARN=警告 / ERROR=错误；默认 INFO。
     */
    @Column(nullable = false, length = 10)
    private String level = LEVEL_INFO;

    /**
     * 请求 ID；写入时生成的短追踪标识，用于跨记录关联与详情溯源。
     */
    @Column(name = "request_id", length = 64)
    private String requestId;

    /**
     * 请求路径。
     */
    @Column(length = 200)
    private String uri;

    /**
     * HTTP 方法；GET/POST/PUT/DELETE 等。
     */
    @Column(length = 10)
    private String method;

    /**
     * 客户端 IP。
     */
    @Column(length = 64)
    private String ip;

    /**
     * 请求参数 JSON；入库前截断至 4000 字符。
     */
    @Column(length = 4000)
    private String params;

    /**
     * 执行结果；SUCCESS=成功 / FAIL=失败。
     */
    @Column(nullable = false, length = 20)
    private String result = RESULT_SUCCESS;

    /**
     * 失败原因摘要；成功时为 NULL。
     */
    @Column(name = "error_msg", length = 500)
    private String errorMsg;

    /**
     * 记录时间；默认当前时间。
     */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;
}
