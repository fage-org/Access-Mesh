package cn.ac.fage.accessmesh.example.data.domain;

import cn.ac.fage.accessmesh.example.data.entity.DemoImportBatch;
import cn.ac.fage.accessmesh.example.data.csv.DemoCsvReader.Parsed;
import cn.ac.fage.accessmesh.example.data.query.DataSqlPlan;
import cn.ac.fage.accessmesh.example.data.catalog.DemoField;
import java.util.Set;
import java.util.UUID;

public interface DemoImportDomainService {
    DemoImportBatch owned(long tenant,long actor,UUID key);
    Set<DemoField> originalFields(DemoImportBatch batch);
    DemoImportBatch apply(DataSqlPlan plan,long actor,UUID key,String digest,Parsed input);
    DemoImportBatch revoke(DataSqlPlan plan,long actor,UUID key);
}
