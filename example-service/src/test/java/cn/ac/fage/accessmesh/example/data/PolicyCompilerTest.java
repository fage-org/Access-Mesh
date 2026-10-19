package cn.ac.fage.accessmesh.example.data;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.data.catalog.DemoField;
import cn.ac.fage.accessmesh.example.data.policy.PolicyCompiler;
import cn.ac.fage.accessmesh.example.data.policy.PolicyConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;

class PolicyCompilerTest {
    private final JsonMapper json=JsonMapper.builder().configureForJackson2().build();
    private final PolicyCompiler compiler=new PolicyCompiler(json);

    @Test
    void shouldCompileConfiguredFieldScopesAndSubjectPredicateWithoutHardcodedRoleNames() {
        var configuration=compiler.parse(valid());
        assertNotNull(configuration);
        assertEquals("CUSTOM_READ",configuration.read().rowOperationCode());
        assertEquals(9,configuration.fields().size());
        var predicate=configuration.bindings().get(new cn.ac.fage.accessmesh.example.metadata.DemoResourceKey("SCOPES","self","default")).getFirst();
        assertEquals(DemoField.OWNER_USER_ID,predicate.field());
        assertEquals(PolicyConfiguration.ValueSource.SUBJECT_ID,predicate.source());
        assertTrue(predicate.literals().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings={"missing-field","unknown-field","duplicate-field","unknown-key","empty-predicate","wrong-subject-field","subject-value","mixed-type-purpose","wrong-operator","unknown-source"})
    void shouldRejectInvalidConfigurationInsteadOfCompilingUnrestrictedQuery(String defect) {
        var input=valid();
        var fields=(tools.jackson.databind.node.ArrayNode)input.path("fields");
        var predicate=(ObjectNode)input.path("bindings").get(0).path("predicates").get(0);
        switch(defect) {
            case "missing-field" -> fields.remove(0);
            case "unknown-field" -> ((ObjectNode)fields.get(0)).put("fieldKey","tenantId");
            case "duplicate-field" -> fields.add(fields.get(0).deepCopy());
            case "unknown-key" -> input.put("sql","SELECT * FROM demo_record");
            case "empty-predicate" -> ((ObjectNode)input.path("bindings").get(0)).putArray("predicates");
            case "wrong-subject-field" -> predicate.put("fieldKey","teamCode");
            case "subject-value" -> predicate.put("value",42);
            case "mixed-type-purpose" -> ((ObjectNode)input.path("read")).put("rowResourceTypeCode","FIELDS");
            case "wrong-operator" -> predicate.put("operator","OR 1=1");
            case "unknown-source" -> predicate.put("valueSource","REQUEST_USER");
            default -> throw new AssertionError(defect);
        }
        assertEquals(30102,assertThrows(BizException.class,()->compiler.parse(input)).getErrorCode());
    }

    @ParameterizedTest @ValueSource(strings={"bindings","predicates","in-values","bytes"})
    void shouldRejectConfiguredCapacityRatherThanTruncate(String budget) {
        var input=valid();
        var bindings=(tools.jackson.databind.node.ArrayNode)input.path("bindings");
        var first=bindings.get(0).deepCopy();
        if(budget.equals("bindings")) for(int i=1;i<=256;i++) bindings.add(first.deepCopy());
        else if(budget.equals("predicates")) {
            var predicates=(tools.jackson.databind.node.ArrayNode)bindings.get(0).path("predicates");
            var predicate=predicates.get(0).deepCopy(); for(int i=1;i<=8;i++) predicates.add(predicate.deepCopy());
        } else {
            bindings.removeAll();
            int scopes=budget.equals("bytes")?3:1;
            for(int i=0;i<scopes;i++) {
                var predicates=bindings.addObject().put("resourceTypeCode","SCOPES").put("resourceCode","scope-"+i).putArray("predicates");
                for(int p=0;p<(budget.equals("bytes")?8:1);p++) {
                    var values=predicates.addObject().put("fieldKey","productName").put("operator","IN").put("valueSource","LITERAL").putArray("values");
                    for(int v=0;v<(budget.equals("bytes")?100:101);v++) values.add("x".repeat(128));
                }
            }
        }
        assertEquals(30106,assertThrows(BizException.class,()->compiler.parse(input)).getErrorCode());
    }

    static ObjectNode valid() {
        var json=JsonMapper.builder().build();
        var node=json.createObjectNode();
        var fields=node.putArray("fields");
        for(String key:java.util.List.of("recordCode","ownerUserId","teamCode","departmentCode","regionCode","saleDate","productName","amount","remark")) {
            fields.addObject().put("fieldKey",key).put("filterable",true).put("sortable",true);
        }
        for(String mode:java.util.List.of("read","edit")) node.putObject(mode)
            .put("rowResourceTypeCode","SCOPES").put("rowOperationCode",mode.equals("read")?"CUSTOM_READ":"CUSTOM_EDIT")
            .put("fieldResourceTypeCode","FIELDS").put("fieldOperationCode",mode.equals("read")?"DATA_READ":"DATA_EDIT");
        node.putArray("bindings").addObject().put("resourceTypeCode","SCOPES").put("resourceCode","self")
            .putArray("predicates").addObject().put("fieldKey","ownerUserId").put("operator","EQ").put("valueSource","SUBJECT_ID");
        return node;
    }
}
