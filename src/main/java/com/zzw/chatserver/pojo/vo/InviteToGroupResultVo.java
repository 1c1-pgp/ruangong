package com.zzw.chatserver.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InviteToGroupResultVo {
    private int invitedCount;
    private List<String> invitedUserIds = new ArrayList<>();
    private List<String> skippedUserIds = new ArrayList<>();
}
