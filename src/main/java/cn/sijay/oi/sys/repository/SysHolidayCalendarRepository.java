package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysHolidayCalendar;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 节假日日历数据访问层，映射表 {@code sys_holiday_calendar}。
 *
 * <p>数据约定：外部接口同步的参考数据，不做软删除，删除为物理删除；
 * {@code cal_date} 唯一，按日期查询与唯一性校验均以其为业务键。
 *
 * <p>分页检索使用动态 JPQL（{@link #search}），支持日期精确/区间与类型组合过滤；
 * 未设置的参数在 JPQL 中通过 {@code :param is null} 短路，不参与过滤。
 *
 * @author sijay
 */
public interface SysHolidayCalendarRepository extends JpaRepository<SysHolidayCalendar, Long> {

    /**
     * 按日期查询日历记录。
     *
     * @param calDate 自然日
     * @return 命中的记录；不存在返回 {@link Optional#empty()}
     */
    Optional<SysHolidayCalendar> findByCalDate(@NonNull LocalDate calDate);

    /**
     * 判断日期是否已存在（创建时的唯一性前置校验，配合唯一键 uk_holiday_calendar_date 双保险）。
     *
     * @param calDate 自然日
     * @return 已存在返回 true
     */
    boolean existsByCalDate(@NonNull LocalDate calDate);

    /**
     * 组合条件分页检索日历记录（未设置的条件不参与过滤）。
     *
     * <p>匹配规则：calDate 精确；beginDate/endDate 为闭区间；dayType/detailsType/makeUpDay 精确；
     * typeDes 模糊包含。
     *
     * @param calDate     精确日期；空为不限
     * @param beginDate   日期下限（含）；空为不限
     * @param endDate     日期上限（含）；空为不限
     * @param dayType     日期类型；空为不限
     * @param detailsType 细分类型；空为不限
     * @param makeUpDay   补班日标记；空为不限
     * @param typeDes     类型描述关键字；空为不限
     * @param pageable    分页与排序参数
     * @return 分页结果
     */
    @Query("""
            select c from SysHolidayCalendar c
             where (:calDate is null or c.calDate = :calDate)
               and (:beginDate is null or c.calDate >= :beginDate)
               and (:endDate is null or c.calDate <= :endDate)
               and (:dayType is null or c.dayType = :dayType)
               and (:detailsType is null or c.detailsType = :detailsType)
               and (:makeUpDay is null or c.makeUpDay = :makeUpDay)
               and (:typeDes is null or c.typeDes like concat('%', :typeDes, '%'))
            """)
    Page<SysHolidayCalendar> search(@Param("calDate") LocalDate calDate,
                                    @Param("beginDate") LocalDate beginDate,
                                    @Param("endDate") LocalDate endDate,
                                    @Param("dayType") Integer dayType,
                                    @Param("detailsType") Integer detailsType,
                                    @Param("makeUpDay") Integer makeUpDay,
                                    @Param("typeDes") String typeDes,
                                    @NonNull Pageable pageable);
}
