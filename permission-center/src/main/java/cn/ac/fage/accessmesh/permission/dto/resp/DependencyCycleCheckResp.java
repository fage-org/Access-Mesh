package cn.ac.fage.accessmesh.permission.dto.resp;

/**
 * Result of dependency cycle check.
 * {@code hasCycle=true} means adding this dependency would create a circular reference.
 */
public record DependencyCycleCheckResp(
    boolean hasCycle,
    String sourceResourceTypeCode,
    String sourceResourceCode,
    String targetResourceTypeCode,
    String targetResourceCode
) {}
