package cn.ac.fage.accessmesh.access.admin.service;

import cn.ac.fage.accessmesh.access.admin.dto.req.JobCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.JobUpdateReq;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.JobLogPageReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.JobLogResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.JobResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysJob;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;

/**
 * 定时任务服务接口
 * <p>
 * 提供定时任务管理相关的服务方法，包括任务的创建、更新、删除、执行等。
 * 支持任务的状态切换、手动触发和执行日志查询。
 * </p>
 */
public interface JobService {

    /**
     * 创建定时任务
     * <p>
     * 创建新的定时任务配置。
     * 设置任务名称、执行类、cron表达式等属性。
     * </p>
     *
     * @param job 任务实体
     * @return 创建的任务ID
     */
    Long createJob(JobCreateReq req);

    /**
     * 更新定时任务
     * <p>
     * 更新指定任务的基本信息。
     * 包括任务名称、执行类、cron表达式等属性。
     * </p>
     *
     * @param job 任务实体
     */
    void updateJob(JobUpdateReq req);

    /**
     * 删除定时任务
     * <p>
     * 批量删除多个定时任务。
     * 使用软删除方式，保留数据记录。
     * </p>
     *
     * @param req 待删除的任务ID列表请求
     */
    void deleteJobs(IdsReq req);

    /**
     * 切换任务状态
     * <p>
     * 切换定时任务的启用/禁用状态。
     * 禁用的任务不会被调度执行。
     * </p>
     *
     * @param id     任务ID
     * @param status 目标状态（0禁用 1启用）
     */
    void toggleJobStatus(Long id, Integer status);

    /**
     * 手动触发任务
     * <p>
     * 手动触发执行指定任务一次。
     * 用于测试任务配置或紧急执行场景。
     * </p>
     *
     * @param id 任务ID
     */
    void triggerJob(Long id);

    /**
     * 获取任务详情
     * <p>
     * 根据ID查询任务详细信息。
     * </p>
     *
     * @param id 任务ID
     * @return 任务详情响应
     */
    JobResp getJob(Long id);

    /**
     * 分页查询任务列表
     * <p>
     * 根据条件分页查询任务列表。
     * 支持按任务组、任务名称等条件筛选。
     * </p>
     *
     * @param pageReq  分页查询请求
     * @param jobGroup 任务组名称
     * @return 分页任务列表结果
     */
    PageResp<JobResp> pageJobs(PageReq pageReq, String jobGroup);

    /**
     * 执行一轮租约接管扫描（T-ACCESS-009）
     * <p>
     * 收敛超过最大尝试次数的过期执行为 FAILED，随后对可重试执行
     * （RUNNING 租约过期 / FAILED 未超限）原子接管重试。
     * 供接管扫描器（{@code TaskLeaseTakeoverScheduler}）周期调用，
     * 不经 HTTP 暴露。
     * </p>
     */
    void takeoverExpiredExecutions();

    /**
     * 多实例任务配置对账（T-ACCESS-009 用户决策：周期对账）
     * <p>
     * 从数据库重载全部启用任务并 diff 重调度，取消已停用/删除任务的调度——
     * 任务 CRUD 只操作当前实例内存，其他实例靠本对账周期性收敛配置漂移。
     * 供对账组件（{@code JobScheduleReconciler}）周期调用，不经 HTTP 暴露。
     * </p>
     */
    void reconcileScheduledJobs();

    /**
     * 分页查询任务执行日志
     * <p>
     * 根据条件分页查询任务执行日志列表。
     * 用于追踪任务的执行情况和异常记录。
     * </p>
     *
     * @param pageReq 分页查询请求
     * @param jobId   任务ID，可选
     * @return 分页任务日志列表结果
     */
    PageResp<JobLogResp> pageJobLogs(JobLogPageReq pageReq, Long jobId);
}