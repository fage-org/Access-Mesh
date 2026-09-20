package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 独立依赖发布快照；服务身份由认证上下文提供。集合拷贝保证重试使用原快照。 */
public record PermissionManifestReq(
        @NotNull @Min(1) @Max(1) Integer schemaVersion,
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String publicationGeneration,
        @NotBlank @Size(max = 128) String revision,
        @NotNull List<@NotNull @Valid Dependency> dependencies) {
    public PermissionManifestReq {
        dependencies = dependencies == null ? null : List.copyOf(dependencies);
    }

    public record ResourceKey(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String resourceTypeCode,
            @NotBlank @Size(max = 256) String resourceCode,
            @Size(max = 64) String codeType) {
        public ResourceKey {
            codeType = codeType == null || codeType.isBlank() ? "default" : codeType;
        }
    }

    public record Dependency(
            @NotBlank @Size(max = 128) String declarationKey,
            @NotNull @Valid ResourceKey source,
            @Size(max = 1) List<@NotBlank @Size(max = 64) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String> sourceOperationCodes,
            @NotEmpty List<@NotNull @Valid Requirement> requires,
            @Size(max = 512) String description) {
        public Dependency {
            sourceOperationCodes = sourceOperationCodes == null ? List.of() : List.copyOf(sourceOperationCodes);
            requires = requires == null ? null : List.copyOf(requires);
        }
    }

    public record Requirement(
            @NotNull @Valid ResourceKey target,
            @NotEmpty List<@NotBlank @Size(max = 64) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String> operationCodes) {
        public Requirement {
            operationCodes = operationCodes == null ? null : List.copyOf(operationCodes);
        }
    }
}
