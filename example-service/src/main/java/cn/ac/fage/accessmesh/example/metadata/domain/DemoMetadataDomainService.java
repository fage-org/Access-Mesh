package cn.ac.fage.accessmesh.example.metadata.domain;

import cn.ac.fage.accessmesh.example.metadata.entity.DemoReport;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoReportField;
import java.util.List;

/** Local metadata operations; callers own the transaction and perform remote authorization before it. */
public interface DemoMetadataDomainService {
    DemoReport createReport(long tenant, long actor, String code, String name, String dataset, String fieldType, List<String> referencedTypes);
    default DemoReport createReport(long tenant, long actor, String code, String name, String dataset, String fieldType) {
        return createReport(tenant, actor, code, name, dataset, fieldType, List.of());
    }
    DemoReport getReport(long tenant, String code);
    DemoReport getReportById(long tenant,long id);
    DemoReport updateReport(long tenant, long actor, String code, long version, String name, boolean enabled, List<String> referencedTypes);
    default DemoReport updateReport(long tenant, long actor, String code, long version, String name, boolean enabled) {
        return updateReport(tenant, actor, code, version, name, enabled, null);
    }
    List<DemoReportField> getFields(long tenant, String code);
    List<DemoReportField> registerFields(long tenant, long actor, String code, long catalogVersion, List<String> keys);
    void updateField(long tenant, long actor, String code, long catalogVersion, long fieldId, boolean enabled);
    void removeFields(long tenant, long actor, String code, long catalogVersion, List<Long> ids);
    long lockDataset(long tenant,long actor);
    void validatePolicyTypes(long tenant,String fieldType,java.util.Set<String> rowTypes);
    List<DemoReportField> fieldsByResourceCodes(long tenant,String type,java.util.Set<String> codes);
}
