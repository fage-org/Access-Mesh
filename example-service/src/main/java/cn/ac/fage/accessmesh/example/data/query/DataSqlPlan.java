package cn.ac.fage.accessmesh.example.data.query;

import cn.ac.fage.accessmesh.example.data.catalog.DemoField;
import cn.ac.fage.accessmesh.example.metadata.DemoResourceKey;
import cn.ac.fage.accessmesh.example.metadata.DemoPermission;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import java.util.List;
import java.util.Map;

/** Internal query assembled from the closed catalog and verified permission facts. Never bound from HTTP. */
public record DataSqlPlan(long tenantId,long datasetId,String reportCode,long reportVersion,long policyVersion,
                          long catalogVersion,Map<DemoResourceKey,Long> scopeVersions,
                          DemoPermission permission,ScopeMode rowMode,List<ColumnBinding> columns,String predicate,List<Object> parameters,String orderBy) {
    public boolean emptyRows() { return rowMode==ScopeMode.EMPTY; }
    public record ColumnBinding(long id,DemoField field,boolean filterable,boolean sortable) {}
}
