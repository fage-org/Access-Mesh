package cn.ac.fage.accessmesh.example.metadata.domain.impl;

import cn.ac.fage.accessmesh.example.metadata.domain.DemoPublicationDomainService;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoPublication;
import cn.ac.fage.accessmesh.example.metadata.mapper.DemoPublicationMapper;
import cn.ac.fage.accessmesh.example.metadata.mapper.DemoDatasetMapper;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DemoPublicationDomainServiceImpl implements DemoPublicationDomainService {
    private final DemoPublicationMapper events;
    private final JsonMapper json;
    private final DemoDatasetMapper datasets;
    public DemoPublicationDomainServiceImpl(DemoPublicationMapper events, JsonMapper json, DemoDatasetMapper datasets) {
        this.events = events; this.json = json; this.datasets = datasets;
    }

    @Override
    public void record(long tenant, long actor, List<Change> changes) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Publication facts must share the metadata transaction");
        }
        if (changes.isEmpty()) return;
        var occurredAt = events.eventTime(tenant);
        var batch = changes.stream().map(change -> {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("operation", change.operation());
            payload.put("resourceTypeCode", change.type()); payload.put("resourceCode", change.code());
            payload.put("codeType", change.codeType()); payload.put("name", change.name());
            payload.put("status", change.enabled() ? 1 : 0);
            payload.put("sourceService", "example-service");
            payload.put("sourceEntityType", change.entityType()); payload.put("sourceEntityId", change.entityId());
            payload.put("syncVersion", Map.of("occurredAt", occurredAt, "sequenceNo", change.version()));
            return new DemoPublication(null, tenant, change.type(), change.code(), change.codeType(),
                change.version(), occurredAt, json.writeValueAsString(payload), "PENDING", 0, null, null, actor);
        }).toList();
        if (events.insertBatch(batch) != batch.size()) throw new IllegalStateException("Incomplete publication batch");
    }

    @Override
    public DemoPublication claimNext() {
        var candidate = events.next();
        if (candidate == null) return null;
        datasets.lock(candidate.getTenantId());
        var current = events.byId(candidate.getTenantId(), candidate.getId());
        if (current == null || !"PENDING".equals(current.getStatus())) return null;
        if (current.getAttempt() >= 3) {
            finish(current, false, false, "ATTEMPTS_EXHAUSTED");
            return null;
        }
        return events.claim(current) == 1 ? events.byId(current.getTenantId(), current.getId()) : null;
    }

    @Override
    public void finish(DemoPublication event, boolean successful, boolean retryable, String reason) {
        datasets.lock(event.getTenantId());
        String status = successful ? "PUBLISHED" : retryable && event.getAttempt() < 3 ? "PENDING" : "FAILED";
        if (events.finish(event, status, reason) != 1) return;
        mark(event, status);
    }

    private void mark(DemoPublication event, String status) {
        String entity = json.readTree(event.getPayload()).path("sourceEntityType").asText();
        switch (entity) {
            case "demo_report" -> events.markReport(event, status);
            case "demo_report_field" -> events.markField(event, status);
            case "demo_scope" -> events.markScope(event, status);
            default -> throw new IllegalStateException("Unexpected stored publication entity");
        }
    }

    @Override
    public DemoPublication latest(long tenant, String type, String code, String codeType) {
        var event = events.latest(tenant, type, code, normalize(codeType));
        if (event == null) throw new BizException(ExampleErrorCode.DEMO_OBJECT_UNAVAILABLE.getCode(), "发布记录不可用");
        return event;
    }

    @Override
    public DemoPublication retry(long tenant, long actor, String type, String code, String codeType) {
        datasets.lock(tenant);
        var event = latest(tenant, type, code, codeType);
        // A pending send is left intact; manual retries cannot invalidate its in-flight attempt.
        if ("FAILED".equals(event.getStatus()) && events.retry(event, actor) == 1) mark(event, "PENDING");
        return events.byId(tenant, event.getId());
    }

    @Override
    public PageResp<DemoPublication> page(long tenant, String status, int page, int size) {
        var rows = events.page(tenant, status, size, (long)(page - 1) * size);
        long total = events.count(tenant, status);
        return new PageResp<>(rows, total, page, size, (long)page * size < total);
    }

    private static String normalize(String codeType) { return codeType == null || codeType.isBlank() ? "default" : codeType.trim(); }
}
