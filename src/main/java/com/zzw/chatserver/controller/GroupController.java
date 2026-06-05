package com.zzw.chatserver.controller;

import com.zzw.chatserver.common.R;
import com.zzw.chatserver.common.ResultEnum;
import com.zzw.chatserver.pojo.Group;
import com.zzw.chatserver.pojo.vo.*;
import com.zzw.chatserver.service.GroupService;
import com.zzw.chatserver.service.GroupUserService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/group")
public class GroupController {
    @Resource
    private GroupUserService groupUserService;

    @Resource
    private GroupService groupService;

    /**
     * 根据用户名获取我的群聊列表
     */
    @GetMapping("/getMyGroupList")
    public R getMyGroupList(String username) {
        List<MyGroupResultVo> myGroupList = groupUserService.getGroupUsersByUserName(username);
        return R.ok().data("myGroupList", myGroupList);
    }

    /**
     * 获取最近的群聊
     */
    @PostMapping("/recentGroup")
    public R getRecentGroup(@RequestBody RecentGroupVo recentGroupVo) {
        List<MyGroupResultVo> recentGroups = groupUserService.getRecentGroup(recentGroupVo);
        return R.ok().data("recentGroups", recentGroups);
    }

    /**
     * 获取群聊详情
     */
    @GetMapping("/getGroupInfo")
    public R getGroupInfo(String groupId) {
        Group groupInfo = groupService.getGroupInfo(groupId);
        List<MyGroupResultVo> groupUsers = groupUserService.getGroupUsersByGroupId(groupId);
        return R.ok().data("groupInfo", groupInfo).data("users", groupUsers);
    }

    /**
     * 在客户端搜索群聊
     */
    @PostMapping("/preFetchGroup")
    public R searchGroup(@RequestBody SearchRequestVo requestVo) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<SearchGroupResponseVo> groupResponseVos = groupService.searchGroup(requestVo, userId);
        return R.ok().data("groupList", groupResponseVos);
    }

    /**
     * 创建群聊
     */
    @PostMapping("/createGroup")
    public R createGroup(@RequestBody CreateGroupRequestVo requestVo) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (requestVo.getHolderUserId() != null && !userId.equals(requestVo.getHolderUserId())) {
            return R.error().resultEnum(ResultEnum.ILLEGAL_OPERATION);
        }
        requestVo.setHolderUserId(userId);
        CreateGroupResultVo result = groupService.createGroup(requestVo);
        return R.ok().data("groupCode", result.getGroupCode()).data("groupId", result.getGroupId());
    }

    /**
     * 邀请好友入群（发送群聊验证消息）
     */
    @PostMapping("/inviteToGroup")
    public R inviteToGroup(@RequestBody InviteToGroupRequestVo requestVo) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (requestVo.getInviterUserId() != null && !userId.equals(requestVo.getInviterUserId())) {
            return R.error().resultEnum(ResultEnum.ILLEGAL_OPERATION);
        }
        requestVo.setInviterUserId(userId);
        InviteToGroupResultVo result = groupService.inviteToGroup(requestVo);
        return R.ok()
                .data("invitedCount", result.getInvitedCount())
                .data("invitedUserIds", result.getInvitedUserIds())
                .data("skippedUserIds", result.getSkippedUserIds());
    }

    /**
     * 获取所有群聊
     */
    @GetMapping("/all")
    public R getAllGroup() {
        List<SearchGroupResultVo> allGroup = groupService.getAllGroup();
        return R.ok().data("allGroup", allGroup);
    }

    /**
     * 退出群聊（非群主）
     */
    @PostMapping("/quitGroup")
    public R quitGroup(@RequestBody QuitGroupRequestVo requestVo) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!userId.equals(requestVo.getUserId())) {
            return R.error().resultEnum(ResultEnum.ILLEGAL_OPERATION);
        }
        if (groupService.isGroupHolder(userId, requestVo.getGroupId())) {
            return R.error().message("群主请使用解散群聊接口");
        }
        requestVo.setHolder(0);
        groupService.quitGroup(requestVo);
        return R.ok().message("已退出群聊");
    }

    /**
     * 解散群聊（仅群主）
     */
    @PostMapping("/dissolveGroup")
    public R dissolveGroup(@RequestBody DissolveGroupRequestVo requestVo) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!groupService.isGroupHolder(userId, requestVo.getGroupId())) {
            return R.error().resultEnum(ResultEnum.ILLEGAL_OPERATION);
        }
        QuitGroupRequestVo quitVo = new QuitGroupRequestVo();
        quitVo.setHolder(1);
        quitVo.setGroupId(requestVo.getGroupId());
        quitVo.setUserId(userId);
        groupService.quitGroup(quitVo);
        return R.ok().message("群聊已解散");
    }
}
