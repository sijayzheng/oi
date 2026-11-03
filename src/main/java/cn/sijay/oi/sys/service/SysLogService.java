package cn.sijay.oi.sys.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.sys.entity.SysLog;
import cn.sijay.oi.sys.model.SysLogQuery;
import cn.sijay.oi.sys.model.SysLogVO;
import cn.sijay.oi.sys.repository.SysLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 操作日志业务层，提供日志组合条件检索、CSV 导出与统一写入入口。
 *
 * <p><b>检索</b>：日志级别/时间范围/关键词三族条件组合过滤，最新记录在前。
 *
 * <p><b>写入</b>：{@link #record} 为全系统操作日志统一写入入口（append-only），
 * 内部捕获持久化异常仅记录告警日志，保证日志失败不阻断业务主流程。
 *
 * <p><b>权限</b>：检索与导出仅允许超管（用户 ID=1）访问，与在线用户模块口径一致。
 *
 * @author sijay
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysLogService {

    /**
     * 超管用户 id；唯一允许检索/导出操作日志的账号。
     */
    private static final long ADMIN_USER_ID = 1L;

    /**
     * 非管理员调用受限接口的失败码；语义与 HTTP 403 对齐。
     */
    private static final int CODE_FORBIDDEN = 403;

    /**
     * 单次导出条数上限；防止全量导出拖垮服务。
     */
    private static final int EXPORT_MAX_ROWS = 10000;

    private final SysLogRepository logRepository;

    /**
     * 分页条件检索操作日志。
     *
     * @param query 业务过滤条件（级别/关键词/时间范围）
     * @param page  分页与排序参数（日志固定按时间倒序，不接受外部排序字段）
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<SysLogVO> page(SysLogQuery query, PageQuery page) {
        page.validate();
        Page<SysLog> result = logRepository.search(normalizeBlank(query.getLevel()),
                query.getStartTime(), query.getEndTime(), normalizeBlank(query.getKeyword()),
                PageRequest.of(page.pageNum() - 1, page.pageSize()));
        return result.map(SysLogVO::from);
    }

    /**
     * 按当前筛选条件导出日志 CSV 文本。
     *
     * <p>实现说明：复用列表检索条件，不分页全量查询（上限 {@value #EXPORT_MAX_ROWS} 条）；
     * 返回 CSV 字符串由前端包装为 Blob 下载（含 UTF-8 BOM 由前端追加，保证 Excel 打开不乱码）。
     *
     * @param query 业务过滤条件
     * @return CSV 文本（首行为表头，字段以逗号分隔，内容中的引号/换行按 CSV 规范转义）
     */
    @Transactional(readOnly = true)
    public String exportCsv(SysLogQuery query) {
        List<SysLog> logs = logRepository.searchAll(normalizeBlank(query.getLevel()),
                query.getStartTime(), query.getEndTime(), normalizeBlank(query.getKeyword()));
        List<SysLog> limited = logs.size() > EXPORT_MAX_ROWS ? logs.subList(0, EXPORT_MAX_ROWS) : logs;
        StringBuilder csv = new StringBuilder();
        csv.append("日志ID,日志级别,模块,动作,操作人,客户端IP,请求路径,请求方法,请求ID,执行结果,日志内容,记录时间\n");
        for (SysLog logEntity : limited) {
            SysLogVO vo = SysLogVO.from(logEntity);
            csv.append(csvCell(vo.getId())).append(',')
                    .append(csvCell(vo.getLevel())).append(',')
                    .append(csvCell(vo.getModule())).append(',')
                    .append(csvCell(vo.getAction())).append(',')
                    .append(csvCell(vo.getUsername())).append(',')
                    .append(csvCell(vo.getIp())).append(',')
                    .append(csvCell(vo.getUri())).append(',')
                    .append(csvCell(vo.getMethod())).append(',')
                    .append(csvCell(vo.getRequestId())).append(',')
                    .append(csvCell(vo.getResult())).append(',')
                    .append(csvCell(vo.getContent())).append(',')
                    .append(csvCell(vo.getCreateTime() == null ? "" : vo.getCreateTime().toString()))
                    .append('\n');
        }
        return csv.toString();
    }

    /**
     * 操作日志统一写入入口（append-only，失败不阻断业务主流程）。
     *
     * <p>使用场景：登录成败（INFO/ERROR）、强制下线（INFO）等关键动作留痕；
     * 请求 ID 由本方法生成（8 位短追踪标识），params 超长自动截断至 4000 字符。
     *
     * @param module   业务模块标识（如 auth/online）
     * @param action   操作动作（如 login/kick）
     * @param level    日志级别（DEBUG/INFO/WARN/ERROR）
     * @param userId   操作人用户 id；匿名场景可空
     * @param username 操作人登录名快照；匿名场景可空
     * @param ip       客户端 IP
     * @param uri      请求路径
     * @param method   HTTP 方法
     * @param params   日志内容（请求参数/摘要）；超长截断
     * @param result   执行结果（SUCCESS/FAIL）
     * @param errorMsg 失败原因摘要；成功为 null
     */
    public void record(String module, String action, String level, Long userId, String username,
                       String ip, String uri, String method, String params, String result, String errorMsg) {
        try {
            SysLog entity = new SysLog();
            entity.setModule(module);
            entity.setAction(action);
            entity.setLevel(level == null ? SysLog.LEVEL_INFO : level);
            entity.setUserId(userId);
            entity.setUsername(username);
            entity.setIp(ip);
            entity.setUri(uri == null ? "" : uri);
            entity.setMethod(method == null ? "" : method);
            entity.setRequestId(UUID.randomUUID().toString().substring(0, 8));
            entity.setParams(params == null || params.length() <= 4000 ? params : params.substring(0, 4000));
            entity.setResult(result);
            entity.setErrorMsg(errorMsg);
            logRepository.save(entity);
        }
        catch (RuntimeException e) {
            log.warn("[SysLogService] 操作日志写入失败（不阻断主流程）: module={}, action={}", module, action, e);
        }
    }

    /**
     * 校验当前登录人是否超管（日志管理模块统一准入约束）。
     *
     * @throws BusinessException 当前登录人非超管（403）
     */
    public void checkAdmin() {
        if (StpUtil.getLoginIdAsLong() != ADMIN_USER_ID) {
            throw new BusinessException(CODE_FORBIDDEN, "仅系统管理员可查看操作日志");
        }
    }

    /**
     * CSV 单元格转义：内容含逗号/引号/换行时以双引号包裹并将内部引号翻倍。
     */
    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    /**
     * 空字符串过滤条件归一化为 null（与各 Service 的 normalizeBlank 约定一致）。
     */
    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // 级别与关键词统一大写/保留原值：级别枚举为大写，关键词大小写不敏感（SQL lower）
        return value.trim();
    }
}
