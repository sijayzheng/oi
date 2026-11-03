package cn.sijay.oi.sys.service;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.sys.entity.SysNotice;
import cn.sijay.oi.sys.model.notice.SysNoticeCreateCmd;
import cn.sijay.oi.sys.model.notice.SysNoticeQuery;
import cn.sijay.oi.sys.model.notice.SysNoticeUpdateCmd;
import cn.sijay.oi.sys.repository.SysNoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知公告业务层，提供通知公告模块的增删改查与业务校验。
 *
 * <p>分层约定：参数格式校验（非空/长度/格式）由入参模型的 Bean Validation 完成；
 * 本层负责<b>业务合法性校验</b>与落库编排，失败抛出 {@link BusinessException}
 * （标准错误码），由全局异常处理器统一包装为 {@code Result}。
 *
 * <p>事务约定：写操作 {@code @Transactional}，读操作 {@code @Transactional(readOnly = true)}。
 *
 * @author sijay
 */
@Service
@RequiredArgsConstructor
public class SysNoticeService {

    private final SysNoticeRepository noticeRepository;

    /**
     * 创建通知公告。
     *
     * @param cmd 创建入参（格式校验已由 Bean Validation 完成）
     * @return 已落库的通知公告实体（含自增 id）
     */
    @Transactional
    public SysNotice create(SysNoticeCreateCmd cmd) {
        SysNotice entity = new SysNotice();
        entity.setTitle(cmd.getTitle());
        entity.setNoticeType(cmd.getNoticeType());
        entity.setContent(cmd.getContent());
        entity.setPublishTime(cmd.getPublishTime());
        entity.setTopMarked(cmd.getTopMarked());
        return noticeRepository.save(entity);
    }

    /**
     * 按 id 查询通知公告详情。
     *
     * @param id 通知公告 id
     * @return 通知公告实体
     * @throws BusinessException 通知公告不存在或已软删（错误码 600002）
     */
    @Transactional(readOnly = true)
    public SysNotice getById(Long id) {
        return noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.COMMON_DATA_NOT_FOUND));
    }

    /**
     * 按 id 更新通知公告档案。
     *
     * <p>实现说明：依托 JPA 持久化上下文脏检查，事务提交时自动生成 UPDATE，
     * 无需显式 save；updateTime 由 Hibernate 时间戳注解自动刷新。
     *
     * @param id  通知公告 id
     * @param cmd 更新入参（格式校验已由 Bean Validation 完成）
     * @return 更新后的通知公告实体
     * @throws BusinessException 通知公告不存在或已软删（错误码 600002）
     */
    @Transactional
    public SysNotice update(Long id, SysNoticeUpdateCmd cmd) {
        SysNotice entity = getById(id);
        entity.setTitle(cmd.getTitle());
        entity.setNoticeType(cmd.getNoticeType());
        entity.setContent(cmd.getContent());
        entity.setPublishTime(cmd.getPublishTime());
        entity.setTopMarked(cmd.getTopMarked());
        return entity;
    }

    /**
     * 按 id 软删除通知公告（deleted 置为本行主键 id，物理数据保留供审计）。
     *
     *
     * @param id 通知公告 id
     * @throws BusinessException 通知公告不存在或已软删（错误码 600002）
     */
    @Transactional
    public void delete(Long id) {
        if (noticeRepository.softDelete(id) == 0) {
            throw new BusinessException(BusinessErrorCode.COMMON_DATA_NOT_FOUND);
        }
    }

    /**
     * 分页条件查询通知公告列表。
     *
     * <p>实现说明：Query-by-Example——以过滤参数构建探针实体，
     * 未设置的字段自动忽略；软删行由 {@code @SQLRestriction} 全局过滤。
     * 排序仅按显式传入的 sortField 生成（实体属性名），未指定时为数据库自然顺序。
     *
     * @param query 业务过滤条件（字段格式校验已由 Bean Validation 完成）
     * @param page  分页与排序参数（构造期已归一化，此处仅校验排序字段安全性）
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<SysNotice> page(SysNoticeQuery query, PageQuery page) {
        page.validate();
        SysNotice probe = new SysNotice();
        probe.setTitle(query.getTitle());
        probe.setNoticeType(query.getNoticeType());
        probe.setContent(query.getContent());
        probe.setTopMarked(query.getTopMarked());
        ExampleMatcher matcher = ExampleMatcher.matching()
                .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
                .withIgnoreCase()
                .withIgnorePaths("id", "deleted", "createBy", "updateBy", "createTime", "updateTime", "publishTime");
        PageRequest pageRequest = page.toPageRequest();
        return noticeRepository.findAll(Example.of(probe, matcher), pageRequest);
    }
}
