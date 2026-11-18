package cn.sijay.oi.sys.service;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.sys.entity.SysHolidayCalendar;
import cn.sijay.oi.sys.model.holiday.HolidayApiResult;
import cn.sijay.oi.sys.model.holiday.SysHolidayCalendarCreateCmd;
import cn.sijay.oi.sys.model.holiday.SysHolidayCalendarQuery;
import cn.sijay.oi.sys.model.holiday.SysHolidayCalendarUpdateCmd;
import cn.sijay.oi.sys.repository.SysHolidayCalendarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 节假日日历业务层，提供日历数据的增删改查与业务校验。
 *
 * <p>分层约定：参数格式校验（非空/长度/值域）由入参模型的 Bean Validation 完成；
 * 本层负责<b>业务合法性校验</b>（日期唯一、记录存在性），失败抛出
 * {@link BusinessException}（标准错误码），由全局异常处理器统一包装为 {@code Result}。
 *
 * <p>数据约定：外部同步的参考数据，无软删除——删除为物理删除；
 * {@code calDate} 为业务唯一键，创建后不可修改。
 *
 * <p>事务约定：写操作 {@code @Transactional}，读操作 {@code @Transactional(readOnly = true)}。
 *
 * @author sijay
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysHolidayCalendarService {

    private final SysHolidayCalendarRepository holidayCalendarRepository;
    private final SysConfigService sysConfigService;

    /**
     * 创建日历记录。
     *
     * <p>业务校验：日期唯一（uk_holiday_calendar_date 前置校验）。
     *
     * @param cmd 创建入参（格式校验已由 Bean Validation 完成）
     * @return 已落库的日历实体（含自增 id）
     * @throws BusinessException 该日期已存在（错误码 610002）
     */
    @Transactional
    public SysHolidayCalendar create(SysHolidayCalendarCreateCmd cmd) {
        if (holidayCalendarRepository.existsByCalDate(cmd.getCalDate())) {
            throw new BusinessException(BusinessErrorCode.HOLIDAY_CALENDAR_DATE_EXISTS);
        }
        SysHolidayCalendar entity = new SysHolidayCalendar();
        entity.setCalDate(cmd.getCalDate());
        entity.setWeekDay(cmd.getWeekDay());
        entity.setYearTips(cmd.getYearTips());
        entity.setDayType(cmd.getDayType());
        entity.setDetailsType(cmd.getDetailsType());
        entity.setTypeDes(cmd.getTypeDes());
        entity.setChineseZodiac(cmd.getChineseZodiac());
        entity.setSolarTerms(cmd.getSolarTerms());
        entity.setLunarCalendar(cmd.getLunarCalendar());
        entity.setSuit(cmd.getSuit());
        entity.setDayOfYear(cmd.getDayOfYear());
        entity.setWeekOfYear(cmd.getWeekOfYear());
        entity.setConstellation(cmd.getConstellation());
        entity.setIndexWorkDayOfMonth(cmd.getIndexWorkDayOfMonth());
        entity.setMakeUpDay(cmd.getMakeUpDay());
        return holidayCalendarRepository.save(entity);
    }

    /**
     * 按 id 查询日历记录。
     *
     * @param id 日历记录 id
     * @return 日历实体
     * @throws BusinessException 记录不存在（错误码 610001）
     */
    @Transactional(readOnly = true)
    public SysHolidayCalendar getById(Long id) {
        return holidayCalendarRepository.findById(id)
                                        .orElseThrow(() -> new BusinessException(BusinessErrorCode.HOLIDAY_CALENDAR_NOT_FOUND));
    }

    /**
     * 按日期查询日历记录。
     *
     * <p>使用场景：业务代码判断某天是工作日/节假日/补班日。
     *
     * @param calDate 自然日
     * @return 日历实体
     * @throws BusinessException 记录不存在（错误码 610001）
     */
    @Transactional(readOnly = true)
    public SysHolidayCalendar getByDate(LocalDate calDate) {
        return holidayCalendarRepository.findByCalDate(calDate)
                                        .orElseThrow(() -> new BusinessException(BusinessErrorCode.HOLIDAY_CALENDAR_NOT_FOUND));
    }

    /**
     * 按 id 更新日历记录（calDate 不可修改，需要改期请删除后重建）。
     *
     * <p>整体覆盖语义：可空字段按入参原样写入（传 null 即清空），
     * 防止接口 {@code ignoreHoliday=true} 同步后残留上一轮的节假日标记。
     * 依托 JPA 持久化上下文脏检查，事务提交时自动生成 UPDATE。
     *
     * @param id  日历记录 id
     * @param cmd 更新入参（格式校验已由 Bean Validation 完成）
     * @return 更新后的日历实体
     * @throws BusinessException 记录不存在（错误码 610001）
     */
    @Transactional
    public SysHolidayCalendar update(Long id, SysHolidayCalendarUpdateCmd cmd) {
        SysHolidayCalendar entity = getById(id);
        entity.setWeekDay(cmd.getWeekDay());
        entity.setYearTips(cmd.getYearTips());
        entity.setDayType(cmd.getDayType());
        entity.setDetailsType(cmd.getDetailsType());
        entity.setTypeDes(cmd.getTypeDes());
        entity.setChineseZodiac(cmd.getChineseZodiac());
        entity.setSolarTerms(cmd.getSolarTerms());
        entity.setLunarCalendar(cmd.getLunarCalendar());
        entity.setSuit(cmd.getSuit());
        entity.setDayOfYear(cmd.getDayOfYear());
        entity.setWeekOfYear(cmd.getWeekOfYear());
        entity.setConstellation(cmd.getConstellation());
        entity.setIndexWorkDayOfMonth(cmd.getIndexWorkDayOfMonth());
        entity.setMakeUpDay(cmd.getMakeUpDay());
        return entity;
    }

    /**
     * 按 id 物理删除日历记录（外部同步的参考数据，无软删除语义）。
     *
     * @param id 日历记录 id
     * @throws BusinessException 记录不存在（错误码 610001）
     */
    @Transactional
    public void delete(Long id) {
        holidayCalendarRepository.delete(getById(id));
    }

    /**
     * 分页条件查询日历记录。
     *
     * <p>实现说明：动态 JPQL 组合过滤，未设置的字段自动忽略；日期区间为闭区间；
     * 排序仅按显式传入的 sortField 生成（实体属性名，如 calDate/createTime），
     * 未指定时为数据库自然顺序。
     *
     * @param query 业务过滤条件（字段格式校验已由 Bean Validation 完成）
     * @param page  分页与排序参数（构造期已归一化，此处仅校验排序字段安全性）
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<SysHolidayCalendar> page(SysHolidayCalendarQuery query, PageQuery page) {
        page.validate();
        String typeDes = StringUtils.trimToNull(query.getTypeDes());
        return holidayCalendarRepository.search(
                query.getCalDate(),
                query.getBeginDate(),
                query.getEndDate(),
                query.getDayType(),
                query.getDetailsType(),
                query.getMakeUpDay(),
                typeDes,
                page.toPageRequest());
    }

    /**
     * 年度同步任务：通过 GET 请求逐日拉取并落库下一年的日历数据。
     *
     * <p>背景：日历数据来自外部接口（按日期查询），跨年前必须保证下一年数据完整，
     * （已存在则覆盖，不存在则新增），重复执行不会产生脏数据。
     *
     * <p>容错约定：
     * <ul>
     *   <li>未配置接口密钥（{@code oi.holiday.api.key}）时仅告警并跳过，不影响应用启动；</li>
     * </ul>
     *
     */
    public void syncYearCalendar() {
        String appId = sysConfigService.getByKey("mxnzp.com.app_id").getConfigValue();
        String appSecret = sysConfigService.getByKey("mxnzp.com.app_secret").getConfigValue();
        if (StringUtils.isAnyBlank(appId, appSecret)) {
            log.warn("[HolidayCalendar] 未配置 appId 或 appSecret，跳过");
            return;
        }
        int nextYear = 2026;// LocalDate.now().getYear() + 1;
        JsonNode response = RestClient.builder()
                                      .baseUrl("https://www.mxnzp.com/api/holiday/list/year/" + nextYear)
                                      .requestFactory(new SimpleClientHttpRequestFactory())
                                      .build()
                                      .get()
                                      .uri(uriBuilder -> uriBuilder
                                              .queryParam("ignoreHoliday", false)
                                              .queryParam("app_id", appId)
                                              .queryParam("app_secret", appSecret)
                                              .build())
                                      .retrieve()
                                      .body(JsonNode.class);
        if (response == null) {
            log.error("[HolidayCalendar] 接口未返回 {} 年日历数据", nextYear);
            return;
        }
        if (!Objects.equals(response.path("code").asInt(), 1)) {
            log.error("[HolidayCalendar] 接口未返回 {} 年日历数据失败，原因：{}", nextYear, response.path("msg").asString());
            return;
        }
        ObjectMapper mapper = JsonMapper.builder().build();
        List<HolidayApiResult> days = new ArrayList<>();
        for (JsonNode monthGroup : response.path("data")) {
            for (JsonNode day : monthGroup.path("days")) {
                days.add(mapper.treeToValue(day, HolidayApiResult.class));
            }
        }
        // 3. 展平后校验：此时 Resu 的字段应全部有值
        log.info("总天数={}", days.size());
        for (HolidayApiResult result : days) {
            log.info("result is :{}", result);
            LocalDate date = result.getDate();
            SysHolidayCalendar entity = holidayCalendarRepository.findByCalDate(date)
                                                                 .orElseGet(() -> {
                                                                     SysHolidayCalendar created = new SysHolidayCalendar();
                                                                     created.setCalDate(date);
                                                                     return created;
                                                                 });
            entity.setWeekDay(result.getWeekDay());
            entity.setYearTips(result.getYearTips());
            entity.setDayType(result.getType());
            entity.setDetailsType(result.getDetailsType());
            entity.setTypeDes(result.getTypeDes());
            entity.setChineseZodiac(result.getChineseZodiac());
            entity.setSolarTerms(result.getSolarTerms());
            entity.setLunarCalendar(result.getLunarCalendar());
            entity.setSuit(result.getSuit());
            entity.setDayOfYear(result.getDayOfYear());
            entity.setWeekOfYear(result.getWeekOfYear());
            entity.setConstellation(result.getConstellation());
            entity.setIndexWorkDayOfMonth(result.getIndexWorkDayOfMonth());
            entity.setMakeUpDay(result.getMakeUpDay());
            holidayCalendarRepository.save(entity);
        }
        log.info("[HolidayCalendar] {} 年日历同步完成", nextYear);
    }
}
