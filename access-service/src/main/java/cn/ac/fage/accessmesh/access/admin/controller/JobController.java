package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.access.admin.dto.req.JobLogPageReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.JobLogResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.JobResp;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.JobCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.JobUpdateReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.access.admin.entity.SysJob;
import cn.ac.fage.accessmesh.access.admin.service.JobService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 定时任务管理控制器
 * <p>
 * 提供定时任务的CRUD操作、状态切换、手动触发、执行日志查询等功能。
 * 定时任务用于执行周期性的后台任务，如数据清理、报表生成等。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/job")
public class JobController {

    private final JobService jobService;

    /**
     * 构造函数注入依赖
     *
     * @param jobService 定时任务服务
     */
    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * 创建定时任务
     * <p>
     * 创建新的定时任务，设置任务名称、执行类、cron表达式等属性。
     * </p>
     *
     * @param job 任务实体，包含任务基本信息
     * @return 创建成功的任务ID
     */
    @PostMapping("/create")
    public PermResult<Long> createJob(@Valid @RequestBody JobCreateReq req) {
        return PermResult.success(jobService.createJob(req));
    }

    /**
     * 更新定时任务
     * <p>
     * 更新任务的名称、cron表达式、状态等属性。
     * </p>
     *
     * @param job 任务实体，包含任务ID和新属性值
     * @return 操作成功结果
     */
    @PostMapping("/update")
    public PermResult<Void> updateJob(@Valid @RequestBody JobUpdateReq req) {
        jobService.updateJob(req);
        return PermResult.success();
    }

    /**
     * 删除定时任务
     * <p>
     * 批量删除定时任务，会同时处理任务的执行日志。
     * </p>
     *
     * @param req ID集合请求，包含待删除的任务ID列表
     * @return 操作成功结果
     */
    @PostMapping("/delete")
    public PermResult<Void> deleteJobs(@Valid @RequestBody IdsReq req) {
        jobService.deleteJobs(req);
        return PermResult.success();
    }

    /**
     * 切换任务状态
     * <p>
     * 启用或暂停定时任务。
     * 暂停后任务将不再自动执行。
     * </p>
     *
     * @param req 任务状态切换请求，包含任务ID和新状态
     * @return 操作成功结果
     */
    @PostMapping("/toggle")
    public PermResult<Void> toggleJobStatus(@RequestBody ToggleJobReq req) {
        jobService.toggleJobStatus(req.id(), req.status());
        return PermResult.success();
    }

    /**
     * 手动触发任务
     * <p>
     * 手动执行一次定时任务，不等待cron调度。
     * 用于测试任务或紧急执行。
     * </p>
     *
     * @param req ID请求，包含任务ID
     * @return 操作成功结果
     */
    @PostMapping("/trigger")
    public PermResult<Void> triggerJob(@Valid @RequestBody IdReq req) {
        jobService.triggerJob(req.id());
        return PermResult.success();
    }

    /**
     * 获取任务详情
     * <p>
     * 根据任务ID查询任务的完整信息。
     * </p>
     *
     * @param req ID请求，包含任务ID
     * @return 任务详情信息
     */
    @PostMapping("/detail")
    public PermResult<JobResp> getJob(@Valid @RequestBody IdReq req) {
        return PermResult.success(jobService.getJob(req.id()));
    }

    /**
     * 分页查询任务列表
     * <p>
     * 查询系统中的定时任务列表，支持分页。
     * </p>
     *
     * @param req 分页查询请求
     * @return 分页任务列表结果
     */
    @PostMapping("/page")
    public PermResult<PaginatedResult<JobResp>> pageJobs(@Valid @RequestBody PageReq req) {
        return PermResult.success(jobService.pageJobs(req, null));
    }

    /**
     * 分页查询任务执行日志
     * <p>
     * 查询定时任务的执行历史记录，包括执行时间、执行结果、耗时等。
     * 用于任务执行监控和故障排查。
     * </p>
     *
     * @param req 任务日志分页查询请求，包含任务ID和分页参数
     * @return 分页任务日志列表结果
     */
    @PostMapping("/log/page")
    public PermResult<PaginatedResult<JobLogResp>> pageJobLogs(
            @Valid @RequestBody JobLogPageReq req) {
        return PermResult.success(jobService.pageJobLogs(req, req.jobId()));
    }

    /**
     * 任务状态切换请求记录
     *
     * @param id     任务ID
     * @param status 新状态（启用/暂停）
     */
    public record ToggleJobReq(Long id, Integer status) {}
}