import { ElMessageBox } from "element-plus";
import type { OrgTreeNode } from "@/api/user-manage";

/**
 * 跨层级移动组织确认（定案④，T-FE-052）：组织树父链经 upsertAdminOrg 镜像进
 * abstract_role(ORG) 与 resource_entity(ORG) 父链，换父即改变实例级管理范围与
 * 级联删除范围——仅 parentId 变化的拖拽弹确认。
 *
 * 确认返回 true（调用方 emit node-move）；取消/关闭返回 false（el-tree 已按落点
 * 移动节点，调用方须重拉树恢复）。parentId 相等分支在任何 allow-drop 形态下结构性
 * 不可达：inner 拖回原父被 element-plus 判 dropType="none" 不触发回调、before/after
 * 落点被调用方的 dropType!=="inner" 守卫拦截——保留仅为纯函数语义完备。
 */
export async function confirmOrgMoveIfNeeded(
  node: Pick<OrgTreeNode, "orgName" | "parentOrgId">,
  target: Pick<OrgTreeNode, "id" | "orgName">
): Promise<boolean> {
  if (node.parentOrgId === target.id) return true;
  try {
    await ElMessageBox.confirm(
      `确认将组织「${node.orgName}」移入「${target.orgName}」下？\n组织层级变化会同步改变组织资源父链，影响实例级管理范围与级联删除范围。`,
      "移动组织确认",
      {
        confirmButtonText: "确认移动",
        cancelButtonText: "取消",
        type: "warning"
      }
    );
    return true;
  } catch {
    return false;
  }
}
