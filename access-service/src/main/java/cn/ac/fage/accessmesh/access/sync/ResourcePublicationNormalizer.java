package cn.ac.fage.accessmesh.access.sync;

import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntityFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncItem;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncReq;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionOrder;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 资源发布快照和指纹；排序不更改调用方的 item 应用顺序。 */
public final class ResourcePublicationNormalizer {
    private final ObjectMapper json;
    private final ObjectMapper storedJson;
    public ResourcePublicationNormalizer(ObjectMapper json) {
        this.json = json;
        this.storedJson = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
    }

    public ResourceEntitySyncReq snapshot(ResourceEntitySyncReq request) {
        return new ResourceEntitySyncReq(request.operation(), request.resourceTypeCode(), request.resourceCode(),
                request.codeType(), request.name(), request.parentResourceTypeCode(), request.parentResourceCode(),
                request.parentCodeType(), request.path(), request.status(), copyExtra(request.extra()), request.sourceService(),
                request.sourceEntityType(), request.sourceEntityId(), request.syncVersion(), request.publicationGeneration());
    }
    public ResourceEntityFullSyncReq snapshot(ResourceEntityFullSyncReq request) {
        var items = request.items().stream().map(item -> new ResourceEntitySyncItem(item.resourceCode(), item.codeType(),
                item.name(), item.parentResourceTypeCode(), item.parentResourceCode(), item.parentCodeType(), item.path(),
                item.status(), copyExtra(item.extra()), item.sourceEntityType(), item.sourceEntityId(), item.syncVersion())).toList();
        return new ResourceEntityFullSyncReq(request.scope(), items, request.publicationGeneration());
    }
    public String businessKey(ResourceEntitySyncReq request) {
        return SyncKeyCodecUtil.resourceEntityBusinessKey(request.resourceTypeCode(), request.resourceCode(), codeType(request.codeType()));
    }
    public String hash(ResourceEntitySyncReq request) { return CanonicalJson.hash(payload(request)); }
    public String fullHash(ResourceEntityFullSyncReq request) {
        var root = JsonNodeFactory.instance.objectNode();
        root.put("sourceService", request.scope().sourceService());
        root.put("resourceTypeCode", request.scope().resourceTypeCode());
        List<ResourceEntitySyncReq> items = request.items().stream().map(item -> asSingle(request, item))
                .sorted(Comparator.comparing(this::businessKey)).toList();
        var keys = new HashSet<String>();
        var array = root.putArray("items");
        for (var item : items) {
            if (!keys.add(businessKey(item))) throw new IllegalArgumentException("DUPLICATE_BUSINESS_KEY");
            array.add(payload(item));
        }
        return CanonicalJson.hash(root);
    }
    public ResourceEntitySyncReq asSingle(ResourceEntityFullSyncReq full, ResourceEntitySyncItem item) {
        return new ResourceEntitySyncReq("UPSERT", full.scope().resourceTypeCode(), item.resourceCode(), item.codeType(),
                item.name(), item.parentResourceTypeCode(), item.parentResourceCode(), item.parentCodeType(), item.path(),
                item.status(), item.extra(), full.scope().sourceService(), item.sourceEntityType(), item.sourceEntityId(),
                item.syncVersion(), full.publicationGeneration());
    }

    /** FULL 相同逐键版本只能确认当前有效值，不以相等版本变更事实。 */
    public boolean matchesExisting(ResourceEntitySyncReq request, ResourceEntity current, Long parentId) {
        if (current == null || !Objects.equals(current.getParentId(), parentId)) return false;
        if (request.name() != null && !Objects.equals(request.name(), current.getName())) return false;
        if (request.path() != null && !Objects.equals(request.path(), current.getPath())) return false;
        if (request.status() != null && !Objects.equals(request.status(), current.getStatus())) return false;
        if (request.extra() == null) return true;
        // 现役有父 UPDATE 使用普通 entity，empty extra 归 null 后被 ORM 忽略；保持其写语义。
        if (request.extra().isEmpty() && parentId != null) return true;
        try {
            JsonNode existing = current.getExtra() == null ? null : storedJson.readTree(current.getExtra());
            JsonNode incoming = json.valueToTree(request.extra());
            return equalValue(emptyExtra(existing), emptyExtra(incoming));
        } catch (Exception e) {
            throw new SystemException(AccessErrorCode.SYSTEM_INIT_FAILED.getCode(), "compare resource extra failed", e);
        }
    }
    private JsonNode emptyExtra(JsonNode value) {
        return value == null || value.isNull() || value.isObject() && value.isEmpty() ? null : value;
    }
    private boolean equalValue(JsonNode left, JsonNode right) {
        if (left == null || right == null) return left == right;
        if (left.isNumber() && right.isNumber()) return left.decimalValue().compareTo(right.decimalValue()) == 0;
        if (left.isObject() && right.isObject()) {
            if (left.size() != right.size()) return false;
            var fields = left.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (!right.has(field.getKey()) || !equalValue(field.getValue(), right.get(field.getKey()))) return false;
            }
            return true;
        }
        if (left.isArray() && right.isArray()) {
            if (left.size() != right.size()) return false;
            for (int i = 0; i < left.size(); i++) if (!equalValue(left.get(i), right.get(i))) return false;
            return true;
        }
        return left.equals(right);
    }
    private ObjectNode payload(ResourceEntitySyncReq request) {
        var node = JsonNodeFactory.instance.objectNode();
        node.put("operation", request.operation());
        node.put("sourceService", request.sourceService());
        node.put("resourceTypeCode", request.resourceTypeCode());
        node.put("resourceCode", request.resourceCode());
        node.put("codeType", codeType(request.codeType()));
        node.put("name", request.name());
        node.put("path", request.path());
        node.put("status", request.status());
        node.set("extra", request.extra() == null ? JsonNodeFactory.instance.nullNode() : json.valueToTree(request.extra()));
        boolean parent = "UPSERT".equals(request.operation()) && request.parentResourceCode() != null && !request.parentResourceCode().isBlank();
        node.put("parentResourceCode", parent ? request.parentResourceCode() : null);
        node.put("parentResourceTypeCode", parent ? blank(request.parentResourceTypeCode()) ? request.resourceTypeCode() : request.parentResourceTypeCode() : null);
        node.put("parentCodeType", parent ? codeType(request.parentCodeType()) : null);
        node.put("sourceEntityType", request.sourceEntityType());
        node.put("sourceEntityId", request.sourceEntityId());
        var version = node.putObject("syncVersion");
        version.put("occurredAt", SyncVersionOrder.roundToMicros(request.syncVersion().occurredAt()).toString());
        version.put("sequenceNo", request.syncVersion().sequenceNo());
        return node;
    }
    private Map<String, Object> copyExtra(Map<String, Object> extra) {
        if (extra == null) return null;
        JsonNode value = json.valueToTree(extra);
        Map<String, Object> copied = new LinkedHashMap<>();
        value.fields().forEachRemaining(entry -> copied.put(entry.getKey(), entry.getValue().deepCopy()));
        return Collections.unmodifiableMap(copied);
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String codeType(String value) { return blank(value) ? "default" : value; }
}
