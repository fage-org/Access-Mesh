package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.JobLogPageReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.JobLogResp;
import cn.ac.fage.accessmesh.admin.dto.resp.JobResp;
import cn.ac.fage.accessmesh.admin.entity.SysJob;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

public interface JobService {

    Long createJob(SysJob job);

    void updateJob(SysJob job);

    void deleteJobs(IdsReq req);

    void toggleJobStatus(Long id, Integer status);

    void triggerJob(Long id);

    JobResp getJob(Long id);

    PaginatedResult<JobResp> pageJobs(PageReq pageReq, String jobGroup);

    PaginatedResult<JobLogResp> pageJobLogs(JobLogPageReq pageReq, Long jobId);
}
