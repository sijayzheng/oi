package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

/**
 * 站内信消息仓库。
 *
 * <p>收件箱（点对点回执 + 广播的统一分页视图）为动态条件 UNION 查询，
 * 由 {@code SysMessageService} 基于 EntityManager 原生 SQL 实现；
 * 本仓库承载消息主表的常规访问与发件箱分页。
 *
 * @author sijay
 */
public interface SysMessageRepository extends JpaRepository<SysMessage, Long> {

    /**
     * 发件箱分页：按发送人查询（排序与类型过滤由调用方经 Pageable/查询方法组合）。
     */
    Page<SysMessage> findBySenderId(Long senderId, Pageable pageable);

    /**
     * 发件箱分页：按发送人 + 消息类型过滤。
     */
    Page<SysMessage> findBySenderIdAndMsgType(Long senderId, String msgType, Pageable pageable);

    /**
     * 撤回消息：状态置为 WITHDRAWN（收件箱/发件箱查询均排除已撤回消息）。
     */
    @Modifying
    @Query("update SysMessage m set m.status = 'WITHDRAWN' where m.id = :id and m.status = 'SENT'")
    int withdraw(@Param("id") Long id);

    /**
     * 批量物理软删（仅测试/清理场景使用，业务删除走回执与广播状态表）。
     */
    @Modifying
    @Query("update SysMessage m set m.deleted = m.id where m.id in :ids")
    int softDeleteByIdIn(@Param("ids") Collection<Long> ids);
}
