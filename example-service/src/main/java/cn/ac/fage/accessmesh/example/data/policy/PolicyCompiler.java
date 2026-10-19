package cn.ac.fage.accessmesh.example.data.policy;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.data.catalog.DemoField;
import cn.ac.fage.accessmesh.example.metadata.DemoTypePurpose;
import cn.ac.fage.accessmesh.example.metadata.DemoResourceKey;
import cn.ac.fage.accessmesh.example.data.policy.PolicyConfiguration.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PolicyCompiler {
    private final JsonMapper json;
    public PolicyCompiler(JsonMapper json) { this.json=json; }
    public PolicyConfiguration parse(JsonNode configuration) {
        object(configuration,Set.of("fields","read","edit","bindings"));
        String encoded=json.writeValueAsString(configuration);
        if (encoded.getBytes(StandardCharsets.UTF_8).length>262144) throw limit();
        var fieldArray=configuration.get("fields");
        if (fieldArray==null || !fieldArray.isArray() || fieldArray.size()!=DemoField.values().length) throw invalid();
        Map<DemoField,FieldFlags> fields=new EnumMap<>(DemoField.class);
        for (var item:fieldArray) {
            object(item,Set.of("fieldKey","filterable","sortable"));
            var field=DemoField.require(text(item,"fieldKey",64));
            if (!item.path("filterable").isBoolean() || !item.path("sortable").isBoolean()) throw invalid();
            if (fields.put(field,new FieldFlags(item.path("filterable").asBoolean(),item.path("sortable").asBoolean()))!=null) throw invalid();
        }
        var read=operations(configuration.get("read"));
        var edit=operations(configuration.get("edit"));
        if (!read.fieldResourceTypeCode().equals(edit.fieldResourceTypeCode())
                || read.rowResourceTypeCode().equals(edit.fieldResourceTypeCode())
                || edit.rowResourceTypeCode().equals(read.fieldResourceTypeCode())) throw invalid();
        Map<DemoResourceKey,List<Predicate>> bindings=new LinkedHashMap<>();
        var rows=array(configuration.get("bindings"),0,256);
        for (var item:rows) {
            object(item,Set.of("resourceTypeCode","resourceCode","codeType","predicates"));
            String type=text(item,"resourceTypeCode",64);
            if (!type.equals(read.rowResourceTypeCode()) && !type.equals(edit.rowResourceTypeCode())) throw invalid();
            String code=text(item,"resourceCode",64);
            String codeType="default";
            if (item.hasNonNull("codeType")) {
                if (!item.get("codeType").isTextual() || item.get("codeType").asText().length()>32) throw invalid();
                if (!item.get("codeType").asText().isBlank()) codeType=item.get("codeType").asText().trim();
            }
            List<Predicate> predicates=new ArrayList<>();
            for (var predicate:array(item.get("predicates"),1,8)) predicates.add(predicate(predicate));
            if (bindings.put(new DemoResourceKey(type,code,codeType),List.copyOf(predicates))!=null) throw invalid();
        }
        return new PolicyConfiguration(encoded,Map.copyOf(fields),read,edit,Map.copyOf(bindings));
    }

    private Operations operations(JsonNode value) {
        object(value,Set.of("rowResourceTypeCode","rowOperationCode","fieldResourceTypeCode","fieldOperationCode"));
        String row=text(value,"rowResourceTypeCode",64);
        String field=text(value,"fieldResourceTypeCode",64);
        DemoTypePurpose.requireDynamic(row); DemoTypePurpose.requireDynamic(field);
        if (row.equals(field)) throw invalid();
        String rowOp=operation(text(value,"rowOperationCode",64));
        String fieldOp=operation(text(value,"fieldOperationCode",64));
        return new Operations(row,rowOp,field,fieldOp);
    }

    private Predicate predicate(JsonNode value) {
        object(value,Set.of("fieldKey","operator","valueSource","value","values"));
        var field=DemoField.require(text(value,"fieldKey",64));
        Operator operator;
        ValueSource source;
        try { operator=Operator.valueOf(text(value,"operator",16)); source=ValueSource.valueOf(text(value,"valueSource",16)); }
        catch (IllegalArgumentException e) { throw invalid(); }
        if (source==ValueSource.SUBJECT_ID) {
            if (field.kind()!=DemoField.Kind.USER_ID || operator!=Operator.EQ || value.has("value") || value.has("values")) throw invalid();
            return new Predicate(field,operator,source,List.of());
        }
        return new Predicate(field,operator,source,literals(field,operator,value));
    }

    /** Shared typed literal validation for both policy predicates and user filters. */
    public static List<Object> literals(DemoField field, Operator operator, JsonNode value) {
        if (operator==Operator.EQ) {
            if (value.has("values") || !value.hasNonNull("value")) throw invalid();
            return List.of(field.literal(value.get("value")));
        }
        if (value.has("value")) throw invalid();
        List<Object> result=new ArrayList<>();
        for (var item:array(value.get("values"),1,100)) result.add(field.literal(item));
        return List.copyOf(result);
    }

    public static void object(JsonNode value,Set<String> allowed) {
        if (value==null || !value.isObject()) throw invalid();
        for (var property:value.properties()) if (!allowed.contains(property.getKey())) throw invalid();
    }
    private static JsonNode array(JsonNode value,int min,int max) {
        if (value==null || !value.isArray() || value.size()<min) throw invalid();
        if (value.size()>max) throw limit();
        return value;
    }
    private static String text(JsonNode value,String key,int max) {
        var item=value.get(key);
        if (item==null || !item.isTextual() || item.asText().isBlank() || item.asText().length()>max) throw invalid();
        return item.asText();
    }
    private static String operation(String value) { if (!value.matches("[A-Z][A-Z0-9_]*")) throw invalid(); return value; }
    private static BizException invalid() { return new BizException(ExampleErrorCode.DEMO_POLICY_INVALID.getCode(),"策略字段、类型、谓词或值配置无效"); }
    private static BizException limit() { return new BizException(ExampleErrorCode.DEMO_LIMIT_EXCEEDED.getCode(),ExampleErrorCode.DEMO_LIMIT_EXCEEDED.getMessage()); }
}
