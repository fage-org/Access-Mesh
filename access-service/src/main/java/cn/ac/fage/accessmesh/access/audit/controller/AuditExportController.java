package cn.ac.fage.accessmesh.access.audit.controller;
import cn.ac.fage.accessmesh.access.audit.dto.req.*;
import cn.ac.fage.accessmesh.access.audit.service.AuditExportAppService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
@RestController
@RequestMapping("/api/access")
public class AuditExportController {
    private final AuditExportAppService service;
    public AuditExportController(AuditExportAppService service) { this.service = service; }
    @PostMapping("/log/operation/export")
    public ResponseEntity<byte[]> operations(@Valid @RequestBody OperationLogExportReq req) { return csv(service.operations(req), "operation-log.csv"); }
    @PostMapping("/log/change/export")
    public ResponseEntity<byte[]> changes(@Valid @RequestBody ChangeLogExportReq req) { return csv(service.changes(req), "permission-change-log.csv"); }
    @PostMapping("/login-log/export")
    public ResponseEntity<byte[]> logins(@Valid @RequestBody LoginLogExportReq req) { return csv(service.logins(req), "login-log.csv"); }
    private ResponseEntity<byte[]> csv(byte[] data, String name) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store").body(data);
    }
}
