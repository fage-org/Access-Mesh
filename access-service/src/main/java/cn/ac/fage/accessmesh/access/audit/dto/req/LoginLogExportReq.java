package cn.ac.fage.accessmesh.access.audit.dto.req;
import java.time.LocalDateTime;
public record LoginLogExportReq(LocalDateTime since, LocalDateTime until) {}
