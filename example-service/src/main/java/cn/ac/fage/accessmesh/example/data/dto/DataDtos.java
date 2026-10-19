package cn.ac.fage.accessmesh.example.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.databind.JsonNode;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import java.util.List;
import java.util.Map;
import java.util.HashSet;

public final class DataDtos {
    private DataDtos() {}
    public record Filter(@NotNull @Positive Long fieldId,@NotBlank @Pattern(regexp="EQ|IN") String operator,
                         JsonNode value,@Size(min=1,max=100) List<JsonNode> values) {}
    public record Sort(@NotNull @Positive Long fieldId,@NotBlank @Pattern(regexp="ASC|DESC") String direction) {}
    public record Selection(@NotBlank @Size(max=64) String reportCode,
        @Size(min=1,max=100) List<@NotNull @Positive Long> fieldIds,
        @Size(max=8) List<@NotNull @Valid Filter> filters,@Valid Sort sort) {
        @AssertTrue @JsonIgnore public boolean isUniqueSelection() { return unique(fieldIds); }
    }
    public record PageReq(@NotBlank @Size(max=64) String reportCode,
        @Size(min=1,max=100) List<@NotNull @Positive Long> fieldIds,
        @Size(max=8) List<@NotNull @Valid Filter> filters,@Valid Sort sort,
        @Min(1) Integer pageNum,@Min(1) @Max(200) Integer pageSize) {
        public Selection selection() { return new Selection(reportCode,fieldIds,filters,sort); }
        @AssertTrue @JsonIgnore public boolean isUniqueSelection() { return unique(fieldIds); }
    }
    public record GetReq(@NotBlank @Size(max=64) String reportCode,
        @Size(min=1,max=100) List<@NotNull @Positive Long> fieldIds,
        @Size(max=8) List<@NotNull @Valid Filter> filters,@Valid Sort sort,
        @NotBlank @Size(max=64) String recordCode) {
        public Selection selection() { return new Selection(reportCode,fieldIds,filters,sort); }
        @AssertTrue @JsonIgnore public boolean isUniqueSelection() { return unique(fieldIds); }
    }
    public record BatchGetReq(@NotBlank @Size(max=64) String reportCode,
        @Size(min=1,max=100) List<@NotNull @Positive Long> fieldIds,
        @Size(max=8) List<@NotNull @Valid Filter> filters,@Valid Sort sort,
        @NotEmpty @Size(max=100) List<@NotBlank @Size(max=64) String> recordCodes) {
        public Selection selection() { return new Selection(reportCode,fieldIds,filters,sort); }
        @AssertTrue @JsonIgnore public boolean isUniqueSelection() { return unique(fieldIds)&&unique(recordCodes); }
    }
    public record FieldsReq(@NotBlank @Size(max=64) String reportCode) {}
    public record Change(@NotBlank @Size(max=64) String recordCode,@Size(max=1000) String remark,Boolean remarkClear) {
        @AssertTrue @JsonIgnore public boolean isChangeValid() { return validChange(remark,remarkClear); }
    }
    public record UpdateReq(@NotBlank @Size(max=64) String reportCode,@NotBlank @Size(max=64) String recordCode,
                            @Size(max=1000) String remark,Boolean remarkClear) {
        @AssertTrue @JsonIgnore public boolean isChangeValid() { return validChange(remark,remarkClear); }
        public Change change() { return new Change(recordCode,remark,remarkClear); }
    }
    public record BatchUpdateReq(@NotBlank @Size(max=64) String reportCode,@NotEmpty @Size(max=100) List<@NotNull @Valid Change> changes) {
        @AssertTrue @JsonIgnore public boolean isUniqueTargets() {
            return changes==null || changes.stream().anyMatch(java.util.Objects::isNull)
                || unique(changes.stream().map(Change::recordCode).toList());
        }
    }
    public record Column(long fieldId,String fieldKey,String dataType) {}
    public record FieldDescription(long fieldId,String fieldKey,String dataType,boolean filterable,boolean sortable) {}
    public record FieldsResp(long policyVersion,long catalogVersion,List<FieldDescription> items) {}
    public record PageDataResp(String state,long policyVersion,long catalogVersion,List<Column> columns,PageResp<Map<String,Object>> page) {}
    public record RecordResp(String state,long policyVersion,long catalogVersion,List<Column> columns,Map<String,Object> record) {}
    public record BatchItem(int index,boolean available,Map<String,Object> record) {}
    public record BatchResp(String state,long policyVersion,long catalogVersion,List<Column> columns,List<BatchItem> items) {}
    public record UpdatedResp(int updatedCount) {}
    private static boolean unique(List<?> values) { return values==null || new HashSet<>(values).size()==values.size(); }
    private static boolean validChange(String value,Boolean clear) {
        if (value!=null && value.isBlank()) return false;
        return Boolean.TRUE.equals(clear)?value==null:value!=null;
    }
}
