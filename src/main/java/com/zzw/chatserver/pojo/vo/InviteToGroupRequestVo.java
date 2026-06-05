package com.zzw.chatserver.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InviteToGroupRequestVo {
    private String groupId;
    private List<String> invitedUserIds;
    private String inviterUserId;
}
