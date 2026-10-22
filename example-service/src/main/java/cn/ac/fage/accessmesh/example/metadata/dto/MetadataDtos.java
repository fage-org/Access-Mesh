package cn.ac.fage.accessmesh.example.metadata.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class MetadataDtos {
    private MetadataDtos() {}
    public record Caller(long tenantId, long userId, String clientIp) {}
    public record ReportCreateReq(@NotBlank @Size(max=64) String reportCode,
        @NotBlank @Size(max=128) String name, @NotBlank @Size(max=64) String datasetCode,
        @NotBlank @Size(max=64) String fieldResourceTypeCode,
        @Size(max=200) List<@NotBlank @Pattern(regexp="^[A-Z][A-Z0-9_]{0,63}$") String> referencedTypeCodes) {
        public ReportCreateReq(String reportCode, String name, String datasetCode, String fieldResourceTypeCode) {
            this(reportCode, name, datasetCode, fieldResourceTypeCode, null);
        }
    }
    public record ReportGetReq(@NotBlank @Size(max=64) String reportCode) {}
    public record ReportUpdateReq(@NotBlank @Size(max=64) String reportCode,
        @NotNull @Positive Long expectedVersion, @NotBlank @Size(max=128) String name, @NotNull Boolean enabled,
        @Size(max=200) List<@NotBlank @Pattern(regexp="^[A-Z][A-Z0-9_]{0,63}$") String> referencedTypeCodes) {
        public ReportUpdateReq(String reportCode, Long expectedVersion, String name, Boolean enabled) {
            this(reportCode, expectedVersion, name, enabled, null);
        }
    }
    public record FieldRegisterReq(@NotBlank @Size(max=64) String reportCode,
        @NotNull @PositiveOrZero Long expectedCatalogVersion,
        @NotEmpty @Size(max=100) List<@NotBlank @Size(max=64) String> fieldKeys) {}
    public record FieldListReq(@NotBlank @Size(max=64) String reportCode,
        @Min(1) Integer pageNum, @Min(1) @Max(200) Integer pageSize) {}
    public record FieldUpdateReq(@NotBlank @Size(max=64) String reportCode,
        @NotNull @PositiveOrZero Long expectedCatalogVersion, @NotNull @Positive Long fieldId, @NotNull Boolean enabled) {}
    public record FieldRemoveReq(@NotBlank @Size(max=64) String reportCode,
        @NotNull @PositiveOrZero Long expectedCatalogVersion, @NotEmpty @Size(max=100) List<@NotNull @Positive Long> fieldIds) {}
    public record ReportResp(String reportCode, String name, String datasetCode, String resourceTypeCode,
        String fieldResourceTypeCode, boolean enabled, long version, long catalogVersion, String publicationStatus,
        List<String> referencedTypeCodes) {
        public ReportResp { referencedTypeCodes = List.copyOf(referencedTypeCodes); }
    }
    public record FieldResp(long fieldId, String fieldKey, boolean enabled, String resourceTypeCode,
        String resourceCode, String codeType, String publicationStatus) {}
    public record FieldRegistrationResp(List<FieldResp> items, long catalogVersion) {}

}
