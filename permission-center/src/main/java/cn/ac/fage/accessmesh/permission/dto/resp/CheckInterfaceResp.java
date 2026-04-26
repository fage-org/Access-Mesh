package cn.ac.fage.accessmesh.permission.dto.resp;

/**
 * Gateway callback response: allowed/denied with matched role and operation info.
 */
public record CheckInterfaceResp(
    boolean allowed,
    Long matchedRoleId,
    String matchedOperationCode,
    String denyReason
) {
    public static CheckInterfaceResp allow(Long roleId, String opCode) {
        return new CheckInterfaceResp(true, roleId, opCode, null);
    }

    public static CheckInterfaceResp deny(String reason) {
        return new CheckInterfaceResp(false, null, null, reason);
    }
}
