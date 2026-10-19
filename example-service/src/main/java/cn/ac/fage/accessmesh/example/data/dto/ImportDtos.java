package cn.ac.fage.accessmesh.example.data.dto;

import jakarta.validation.constraints.*;

public final class ImportDtos {
    private ImportDtos() {}
    public static final String UUID_PATTERN="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    public record ImportReq(@NotBlank @Size(max=64) String reportCode,@NotNull @Pattern(regexp=UUID_PATTERN) String importKey) {}
    public record KeyReq(@NotNull @Pattern(regexp=UUID_PATTERN) String importKey) {}
    public record ImportResp(String importKey,String status,long recordCount) {}
    public record ImportedField(long fieldId,String fieldKey) {}
}
