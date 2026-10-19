package cn.ac.fage.accessmesh.example.data.policy;

import cn.ac.fage.accessmesh.example.data.catalog.DemoField;
import cn.ac.fage.accessmesh.example.metadata.DemoResourceKey;
import java.util.List;
import java.util.Map;

/** Validated immutable policy consumed by the SQL compiler; not an HTTP input type. */
public record PolicyConfiguration(String json, Map<DemoField, FieldFlags> fields,
                                  Operations read, Operations edit, Map<DemoResourceKey, List<Predicate>> bindings) {
    public record FieldFlags(boolean filterable, boolean sortable) {}
    public record Operations(String rowResourceTypeCode, String rowOperationCode,
                             String fieldResourceTypeCode, String fieldOperationCode) {}
    public enum Operator { EQ, IN }
    public enum ValueSource { LITERAL, SUBJECT_ID }
    public record Predicate(DemoField field, Operator operator, ValueSource source, List<Object> literals) {}
}
