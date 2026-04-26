package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.admin.dto.req.JobLogPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysJob;
import cn.ac.fage.accessmesh.admin.entity.SysJobLog;
import cn.ac.fage.accessmesh.admin.service.JobService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/job")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/create")
    @AuditLog(module = "定时任务", action = "创建")
    public PermResult<Long> createJob(@Valid @RequestBody SysJob job) {
        return PermResult.success(jobService.createJob(job));
    }

    @PostMapping("/update")
    @AuditLog(module = "定时任务", action = "更新")
    public PermResult<Void> updateJob(@Valid @RequestBody SysJob job) {
        jobService.updateJob(job);
        return PermResult.success();
    }

    @PostMapping("/delete")
    @AuditLog(module = "定时任务", action = "删除")
    public PermResult<Void> deleteJobs(@Valid @RequestBody IdsReq req) {
        jobService.deleteJobs(req);
        return PermResult.success();
    }

    @PostMapping("/toggle")
    @AuditLog(module = "定时任务", action = "切换状态")
    public PermResult<Void> toggleJobStatus(@RequestBody ToggleJobReq req) {
        jobService.toggleJobStatus(req.id(), req.status());
        return PermResult.success();
    }

    @PostMapping("/trigger")
    @AuditLog(module = "定时任务", action = "手动触发")
    public PermResult<Void> triggerJob(@Valid @RequestBody IdReq req) {
        jobService.triggerJob(req.id());
        return PermResult.success();
    }

    @PostMapping("/detail")
    public PermResult<SysJob> getJob(@Valid @RequestBody IdReq req) {
        return PermResult.success(jobService.getJob(req.id()));
    }

    @PostMapping("/page")
    public PermResult<PaginatedResult<SysJob>> pageJobs(@Valid @RequestBody PageReq req) {
        return PermResult.success(jobService.pageJobs(req, null));
    }

    @PostMapping("/log/page")
    public PermResult<PaginatedResult<SysJobLog>> pageJobLogs(
            @Valid @RequestBody JobLogPageReq req) {
        return PermResult.success(jobService.pageJobLogs(req, req.jobId()));
    }

    public record ToggleJobReq(Long id, Integer status) {}
}
