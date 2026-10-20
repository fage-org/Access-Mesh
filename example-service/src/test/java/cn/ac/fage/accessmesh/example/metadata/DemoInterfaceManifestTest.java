package cn.ac.fage.accessmesh.example.metadata;

import cn.ac.fage.accessmesh.example.controller.DemoController;
import cn.ac.fage.accessmesh.example.controller.ReportController;
import cn.ac.fage.accessmesh.example.metadata.controller.DemoMetadataController;
import cn.ac.fage.accessmesh.example.data.controller.DemoPolicyController;
import cn.ac.fage.accessmesh.example.data.controller.DemoDataController;
import cn.ac.fage.accessmesh.example.data.controller.DemoImportController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

/** FULL declarations must preserve every supported old and new route. */
class DemoInterfaceManifestTest {
    @Test
    void shouldDeclareExactlyTheImplementedPostRoutesWithExplicitPermissionRequirements() throws Exception {
        Set<String> implemented=new HashSet<>();
        for (Class<?> controller : Set.of(DemoController.class,ReportController.class,DemoMetadataController.class,DemoPolicyController.class,DemoDataController.class,DemoImportController.class,cn.ac.fage.accessmesh.example.task.controller.DemoExecutionController.class,cn.ac.fage.accessmesh.example.task.controller.DemoTaskController.class)) {
            String prefix=controller.getAnnotation(RequestMapping.class).value()[0];
            for (var method : controller.getDeclaredMethods()) {
                var mapping=method.getAnnotation(PostMapping.class);
                if (mapping!=null) for (String path : mapping.value()) {
                    String full=prefix+path; implemented.add(full);
                    if(full.equals("/api/example/demo-execution/download")) {
                        assertThat(method.getReturnType()).isEqualTo(org.springframework.http.ResponseEntity.class);
                    } else assertThat(method.getReturnType()).isEqualTo(cn.ac.fage.accessmesh.common.model.R.class);
                }
            }
        }
        var manifest=JsonMapper.builder().build().readTree(Files.readString(Path.of("examples","permission-demo-interfaces.json")));
        Set<String> declared=new HashSet<>();
        Set<String> keys=new HashSet<>();
        for (var group : manifest.path("groups")) for (var api : group.path("apis")) {
            assertThat(api.path("httpMethod").asText()).isEqualTo("POST");
            assertThat(declared.add(manifest.path("basePath").asText()+api.path("path").asText())).isTrue();
            assertThat(keys.add(api.path("resourceCode").asText())).isTrue();
            assertThat(api.path("requiredPermission").path("resourceTypeCode").asText()).isNotBlank();
            assertThat(api.path("requiredPermission").path("operationCode").asText()).isNotBlank();
            String path=api.path("path").asText();
            DemoPermission permission=switch (path) {
                case "/demo-report/create" -> DemoPermission.REPORT_CREATE;
                case "/demo-report/get" -> DemoPermission.REPORT_VIEW;
                case "/demo-report/update", "/demo-field/register", "/demo-field/list", "/demo-field/update", "/demo-field/remove" -> DemoPermission.REPORT_UPDATE;
                case "/demo-scope/save", "/demo-publication/get", "/demo-publication/page", "/demo-publication/retry", "/demo-policy/get", "/demo-policy/save" -> DemoPermission.POLICY_MANAGE;
                case "/demo-data/fields", "/demo-data/page", "/demo-data/get", "/demo-data/batch-get" -> DemoPermission.REPORT_READ;
                case "/demo-data/update", "/demo-data/batch-update" -> DemoPermission.REPORT_EDIT;
                case "/demo-data/import", "/demo-data/import-status", "/demo-data/revoke-import" -> DemoPermission.DATASET_MANAGE;
                case "/demo-task/create" -> DemoPermission.TASK_CREATE;
                case "/demo-task/update" -> DemoPermission.TASK_UPDATE;
                case "/demo-task/set-enabled" -> DemoPermission.TASK_ENABLE;
                case "/demo-task/trigger" -> DemoPermission.TASK_TRIGGER;
                case "/demo-task/get", "/demo-task/page" -> DemoPermission.TASK_VIEW;
                case "/demo-snapshot/submit" -> DemoPermission.TASK_TRIGGER;
                case "/demo-execution/get", "/demo-execution/page", "/demo-execution/download" -> DemoPermission.TASK_VIEW;
                default -> null; // Legacy probe requirements are verified by their existing E2E journeys.
            };
            if (permission!=null) {
                assertThat(api.path("requiredPermission").path("resourceTypeCode").asText()).isEqualTo(permission.type());
                assertThat(api.path("requiredPermission").path("operationCode").asText()).isEqualTo(permission.operation());
            }
        }
        assertThat(declared).containsExactlyInAnyOrderElementsOf(implemented);
    }
}
