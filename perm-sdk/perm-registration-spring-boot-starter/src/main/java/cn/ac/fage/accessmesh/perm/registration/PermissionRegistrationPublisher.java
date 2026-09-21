package cn.ac.fage.accessmesh.perm.registration;

import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.perm.registration.feign.PermissionManifestClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 显式发布不可变输入；无自动重新扫描、代次生成或后台重试。 */
public final class PermissionRegistrationPublisher {
    /** 清单 DTO 的 schemaVersion 当前值（契约 @Min(1)@Max(1) 钉死为 1，升级时同批放开）。 */
    private static final int SCHEMA_VERSION = 1;

    private final PermissionManifestClient client;
    private final ObjectMapper json;
    private final ManifestValidation validation;

    public PermissionRegistrationPublisher(PermissionManifestClient client, ObjectMapper json, Validator validator, Boolean allowInsecure) {
        // 与现役 perm.allow-insecure 一致：声明信任域，不以静态 URL 猜测服务发现后的实际 hop。
        Objects.requireNonNull(allowInsecure, "perm.allow-insecure must be explicitly declared");
        this.client = client;
        this.json = json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.validation = new ManifestValidation(validator);
    }

    public PermissionManifestReq capture(String generation, String revision, PermissionManifestProvider provider) {
        var manifest = new PermissionManifestReq(SCHEMA_VERSION, generation, revision,
                Objects.requireNonNull(provider).dependencies());
        validation.validate(manifest);
        return manifest;
    }

    public List<TenantRegistrationProvider.TenantPublication> capture(TenantRegistrationProvider provider) {
        var publications = List.copyOf(Objects.requireNonNull(provider.publications(), "tenant provider returned null"));
        var scopes = new HashSet<Map.Entry<Long, String>>();
        for (var publication : publications) {
            validation.validate(publication.manifest());
            if (!scopes.add(Map.entry(publication.target().tenantId(), publication.target().serviceCode()))) {
                throw new IllegalArgumentException("duplicate tenant/service publication");
            }
        }
        return publications;
    }

    /** JSON 与请求 DTO 同构，文件自身携带源侧绑定的 generation；流由调用方关闭。 */
    public PermissionManifestReq read(InputStream input) throws IOException {
        var manifest = json.readValue(input, PermissionManifestReq.class);
        validation.validate(manifest);
        return manifest;
    }

    public SyncResultResp publish(RegistrationTarget target, PermissionManifestReq manifest) {
        Objects.requireNonNull(target);
        validation.validate(manifest);
        var response = client.fullSync(target.tenantId(), target.serviceCode(),
                target.credentialId(), target.credentialSecret(), manifest);
        if (response == null || response.getCode() != 200 || response.getData() == null || response.getData().detail() == null) {
            throw new IllegalStateException("manifest request failed or returned an invalid response envelope"
                    + (response == null ? "" : ": code=" + response.getCode()));
        }
        return response.getData();
    }

    public RegistrationResult prepareAndPublish(RegistrationTarget target, PermissionManifestReq manifest, ResourcePreparation preparation) {
        Objects.requireNonNull(target);
        validation.validate(manifest);
        var outcomes = List.copyOf(Objects.requireNonNull(preparation.prepare(target), "resource preparation returned null"));
        if (!outcomes.stream().allMatch(RegistrationResult::complete)) return new RegistrationResult(outcomes, null);
        return new RegistrationResult(outcomes, publish(target, manifest));
    }
}
