package cn.ac.fage.accessmesh.example.metadata.domain;

import cn.ac.fage.accessmesh.example.metadata.entity.DemoReport;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoScope;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoReportField;
import java.util.List;

/** Local metadata operations; callers own the transaction and perform remote authorization before it. */
public interface DemoMetadataDomainService {
    DemoReport createReport(long tenant, long actor, String code, String name, String dataset, String fieldType);
    DemoScope saveScope(long tenant, long actor, String type, String code, String codeType,
                        String name, boolean enabled, long expectedVersion);
    DemoReport getReport(long tenant, String code);
    DemoReport updateReport(long tenant, long actor, String code, long version, String name, boolean enabled);
    List<DemoReportField> getFields(long tenant, String code);
    List<DemoReportField> registerFields(long tenant, long actor, String code, long catalogVersion, List<String> keys);
    void updateField(long tenant, long actor, String code, long catalogVersion, long fieldId, boolean enabled);
    void removeFields(long tenant, long actor, String code, long catalogVersion, List<Long> ids);
}
