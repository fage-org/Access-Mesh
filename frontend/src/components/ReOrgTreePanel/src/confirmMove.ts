import { ElMessageBox } from "element-plus";
import type { OrgTreeNode } from "@/api/user-manage";

/**
 * 跨层级移动组织确认（定案④，T-FE-052）：组织树父链经 upsertAdminOrg 镜像进
 * abstract_role(ORG) 与 resource_entity(ORG) 父链，换父即改变实例级管理范围与
 * 级联删除范围——仅 parentId 变化的移动弹确认。两个调用面：组织树拖拽（面板）
 * 与编辑弹窗提交（user/index.vue，claude 外评 P3 处置——表单换父走与拖拽相同
 * 的后端路径）。
 *
 * 确认返回 true（拖拽面 emit node-move / 表单面继续提交）；取消/关闭返回 false
 * （拖拽面 el-tree 已按落点移动节点，调用方须重拉树恢复；表单面保持弹窗打开）。
 * parentId 相等直接放行：拖拽面该分支在任何 allow-drop 形态下结构性不可达
 * （inner 拖回原父被 element-plus 判 dropType="none" 不触发回调、before/after
 * 落点被调用方的 dropType!=="inner" 守卫拦截）；表单面 = 仅改名等未换父提交。
 * target.id=null 表达移至顶层（表单面「设为顶层」，拖拽面 inner-only 不可达）。
 */
export async function confirmOrgMoveIfNeeded(
  node: Pick<OrgTreeNode, "orgName" | "parentOrgId">,
  target: { id: number | null; orgName: string }
): Promise<boolean> {
  if (node.parentOrgId === target.id) return true;
  const toTop = target.id == null;
  const moveText = toTop
    ? `确认将组织「${node.orgName}」移至顶层？`
    : `确认将组织「${node.orgName}」移入「${target.orgName}」下？`;
  try {
    await ElMessageBox.confirm(
      `${moveText}\n组织层级变化会同步改变组织资源父链，影响实例级管理范围与级联删除范围。`,
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
