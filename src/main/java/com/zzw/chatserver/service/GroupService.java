package com.zzw.chatserver.service;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.zzw.chatserver.common.ConstValueEnum;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.zzw.chatserver.dao.AccountPoolDao;
import com.zzw.chatserver.dao.GroupDao;
import com.zzw.chatserver.dao.GroupMessageDao;
import com.zzw.chatserver.dao.GroupUserDao;
import com.zzw.chatserver.dao.UserDao;
import com.zzw.chatserver.pojo.AccountPool;
import com.zzw.chatserver.pojo.Group;
import com.zzw.chatserver.pojo.GroupMessage;
import com.zzw.chatserver.pojo.GroupUser;
import com.zzw.chatserver.pojo.User;
import com.zzw.chatserver.pojo.ValidateMessage;
import com.zzw.chatserver.pojo.vo.*;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;


@Service
public class GroupService {
    @Resource
    private GroupDao groupDao;
    @Resource
    private GroupUserDao groupUserDao;
    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private AccountPoolDao accountPoolDao;

    @Resource
    private GroupMessageDao groupMessageDao;

    @Resource
    private UserDao userDao;

    @Resource
    private GoodFriendService goodFriendService;

    @Resource
    private ValidateMessageService validateMessageService;

    @Resource
    private SocketIOServer socketIOServer;

    public Group getGroupInfo(String groupId) {
        Optional<Group> res = groupDao.findById(new ObjectId(groupId));
        return res.orElse(null);
    }

    public List<SearchGroupResponseVo> searchGroup(SearchRequestVo requestVo, String uid) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.lookup(
                        "users",
                        "holderUserId",
                        "_id",
                        "holderUsers"
                ), Aggregation.match(
                        Criteria.where(requestVo.getType()).regex(Pattern.compile("^.*" + requestVo.getSearchContent() + ".*$", Pattern.CASE_INSENSITIVE))
                ), Aggregation.skip(Long.valueOf(requestVo.getPageIndex() * requestVo.getPageSize())),
                Aggregation.limit((long) requestVo.getPageSize()),
                Aggregation.sort(Sort.Direction.DESC, "_id")
        );
        List<SearchGroupResultVo> results = mongoTemplate.aggregate(aggregation, "groups", SearchGroupResultVo.class).getMappedResults();
        List<SearchGroupResponseVo> groups = new ArrayList<>();
        SearchGroupResponseVo item;
        for (SearchGroupResultVo son : results) {
            //群主账号不为当前登录的账号
            if (!son.getHolderUsers().get(0).getUid().equals(uid)) {
                item = new SearchGroupResponseVo();
                BeanUtils.copyProperties(son, item);
                item.setGid(son.getId());
                BeanUtils.copyProperties(son.getHolderUsers().get(0), item.getHolderUserInfo());
                groups.add(item);
            }
        }
        return groups;
    }

    public CreateGroupResultVo createGroup(CreateGroupRequestVo requestVo) {
        AccountPool accountPool = new AccountPool();
        accountPool.setType(ConstValueEnum.GROUPTYPE);
        accountPool.setStatus(ConstValueEnum.ACCOUNT_USED);
        accountPoolDao.save(accountPool);
        Group group = new Group();
        if (requestVo.getTitle() != null) group.setTitle(requestVo.getTitle());
        if (requestVo.getDesc() != null) group.setDesc(requestVo.getDesc());
        if (requestVo.getImg() != null) group.setImg(requestVo.getImg());
        group.setHolderName(requestVo.getHolderName());
        group.setHolderUserId(new ObjectId(requestVo.getHolderUserId()));
        group.setCode(String.valueOf(accountPool.getCode() + ConstValueEnum.INITIAL_NUMBER));
        groupDao.save(group);
        GroupUser groupUser = new GroupUser();
        groupUser.setGroupId(group.getGroupId());
        groupUser.setUserId(group.getHolderUserId());
        groupUser.setUsername(group.getHolderName());
        groupUser.setHolder(1);
        groupUserDao.save(groupUser);
        Update update = new Update();
        update.set("gid", group.getGroupId().toString());
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(group.getGroupId()));
        mongoTemplate.upsert(query, update, Group.class);
        addGroupSystemMessage(group.getGroupId().toString(), group.getHolderUserId(),
                group.getHolderName() + " 创建了群聊");
        return new CreateGroupResultVo(group.getGroupId().toString(), group.getCode());
    }

    public InviteToGroupResultVo inviteToGroup(InviteToGroupRequestVo requestVo) {
        InviteToGroupResultVo result = new InviteToGroupResultVo();
        if (requestVo == null || !StringUtils.hasText(requestVo.getGroupId())
                || requestVo.getInvitedUserIds() == null || requestVo.getInvitedUserIds().isEmpty()) {
            return result;
        }
        ObjectId groupId = new ObjectId(requestVo.getGroupId());
        Group group = groupDao.findById(groupId).orElse(null);
        if (group == null) {
            return result;
        }
        ObjectId inviterId = new ObjectId(requestVo.getInviterUserId());
        GroupUser inviter = groupUserDao.findGroupUserByUserIdAndGroupId(inviterId, groupId);
        if (inviter == null) {
            return result;
        }
        User inviterUser = userDao.findById(inviterId).orElse(null);
        String inviterDisplay = inviterUser != null && StringUtils.hasText(inviterUser.getNickname())
                ? inviterUser.getNickname() : inviter.getUsername();
        String groupTitle = StringUtils.hasText(group.getTitle()) ? group.getTitle() : group.getCode();

        for (String invitedUserId : requestVo.getInvitedUserIds()) {
            if (!StringUtils.hasText(invitedUserId) || invitedUserId.equals(requestVo.getInviterUserId())) {
                result.getSkippedUserIds().add(invitedUserId);
                continue;
            }
            ObjectId invitedId = new ObjectId(invitedUserId);
            if (groupUserDao.findGroupUserByUserIdAndGroupId(invitedId, groupId) != null) {
                result.getSkippedUserIds().add(invitedUserId);
                continue;
            }
            if (!goodFriendService.areFriends(requestVo.getInviterUserId(), invitedUserId)) {
                result.getSkippedUserIds().add(invitedUserId);
                continue;
            }
            User invitedUser = userDao.findById(invitedId).orElse(null);
            if (invitedUser == null) {
                result.getSkippedUserIds().add(invitedUserId);
                continue;
            }
            if (hasPendingGroupInvite(invitedUserId, requestVo.getGroupId())) {
                result.getSkippedUserIds().add(invitedUserId);
                continue;
            }

            ValidateMessage validateMessage = new ValidateMessage();
            validateMessage.setRoomId(invitedUserId);
            validateMessage.setSenderId(invitedId);
            validateMessage.setSenderName(invitedUser.getUsername());
            validateMessage.setSenderNickname(invitedUser.getNickname());
            validateMessage.setSenderAvatar(invitedUser.getPhoto());
            validateMessage.setReceiverId(invitedId);
            validateMessage.setAdditionMessage(inviterDisplay + " 邀请你加入群聊「" + groupTitle + "」");
            validateMessage.setTime(Instant.now().toString());
            validateMessage.setStatus(0);
            validateMessage.setValidateType(1);
            validateMessage.setGroupId(groupId);

            ValidateMessage saved = validateMessageService.addValidateMessage(validateMessage);
            if (saved != null) {
                result.setInvitedCount(result.getInvitedCount() + 1);
                result.getInvitedUserIds().add(invitedUserId);
                pushValidateMessage(invitedUserId, saved);
            } else {
                result.getSkippedUserIds().add(invitedUserId);
            }
        }
        return result;
    }

    public boolean isGroupHolder(String userId, String groupId) {
        Group group = groupDao.findById(new ObjectId(groupId)).orElse(null);
        return group != null && group.getHolderUserId() != null
                && group.getHolderUserId().toString().equals(userId);
    }

    public List<SearchGroupResultVo> getAllGroup() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.lookup(
                        "users",
                        "holderUserId",
                        "_id",
                        "holderUsers"
                )
        );
        AggregationResults<SearchGroupResultVo> groups = mongoTemplate.aggregate(aggregation, "groups", SearchGroupResultVo.class);
        return groups.getMappedResults();
    }

    @Transactional
    public void quitGroup(QuitGroupRequestVo requestVo) {
        Group group = groupDao.findById(new ObjectId(requestVo.getGroupId())).orElse(null);
        if (group == null) {
            return;
        }
        GroupUser groupUser = groupUserDao.findGroupUserByUserIdAndGroupId(
                new ObjectId(requestVo.getUserId()), new ObjectId(requestVo.getGroupId()));
        if (groupUser == null) {
            return;
        }
        if (requestVo.getHolder() != null && requestVo.getHolder() == 1) {
            addGroupSystemMessage(requestVo.getGroupId(), new ObjectId(requestVo.getUserId()),
                    groupUser.getUsername() + " 解散了群聊");
            releaseGroupAccount(group);
            delGroupAllMessagesByGroupId(requestVo.getGroupId());
            delGroupAllUsersByGroupId(requestVo.getGroupId());
            groupDao.deleteById(new ObjectId(requestVo.getGroupId()));
        } else {
            addGroupSystemMessage(requestVo.getGroupId(), new ObjectId(requestVo.getUserId()),
                    groupUser.getUsername() + " 退出了群聊");
            delGroupMessagesByGroupIdAndSenderId(requestVo.getGroupId(), requestVo.getUserId());
            delGroupUserByGroupIdAndUserId(requestVo.getGroupId(), requestVo.getUserId());
            decrGroupUserNum(requestVo.getGroupId());
        }
    }

    private boolean hasPendingGroupInvite(String invitedUserId, String groupId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("receiverId").is(new ObjectId(invitedUserId))
                .and("groupId").is(new ObjectId(groupId))
                .and("status").is(0)
                .and("validateType").is(1));
        return mongoTemplate.exists(query, ValidateMessage.class);
    }

    private void pushValidateMessage(String roomId, ValidateMessage validateMessage) {
        Collection<SocketIOClient> clients = socketIOServer.getRoomOperations(roomId).getClients();
        for (SocketIOClient client : clients) {
            client.sendEvent("receiveValidateMessage", validateMessage);
        }
    }

    private void addGroupSystemMessage(String groupId, ObjectId senderId, String message) {
        GroupMessage groupMessage = new GroupMessage();
        groupMessage.setRoomId(groupId);
        groupMessage.setSenderId(senderId);
        groupMessage.setMessageType("sys");
        groupMessage.setMessage(message);
        groupMessageDao.save(groupMessage);
    }

    private void releaseGroupAccount(Group group) {
        if (!StringUtils.hasText(group.getCode())) {
            return;
        }
        try {
            long poolCode = Long.parseLong(group.getCode()) - ConstValueEnum.INITIAL_NUMBER;
            Query query = new Query(Criteria.where("_id").is(poolCode));
            Update update = new Update().set("status", ConstValueEnum.ACCOUNT_NOT_USED);
            mongoTemplate.updateFirst(query, update, AccountPool.class);
        } catch (NumberFormatException ignored) {
            /* 群号格式异常时跳过账号池回收 */
        }
    }

    private void delGroupAllMessagesByGroupId(String groupId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("roomId").is(groupId));
        DeleteResult groupmessages = mongoTemplate.remove(query, "groupmessages");
        // System.out.println("删除该群所有消息是否成功？" + groupmessages.getDeletedCount());
    }

    private void delGroupAllUsersByGroupId(String groupId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("groupId").is(new ObjectId(groupId)));
        DeleteResult groupusers = mongoTemplate.remove(query, "groupusers");
        // System.out.println("删除该群所有成员是否成功？" + groupusers.getDeletedCount());
    }

    private void delGroupMessagesByGroupIdAndSenderId(String groupId, String senderId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("roomId").is(groupId).and("senderId").is(new ObjectId(senderId)));
        DeleteResult groupmessages = mongoTemplate.remove(query, "groupmessages");
        // System.out.println("删除该用户所发的群消息是否成功？" + groupmessages.getDeletedCount());
    }

    private void delGroupUserByGroupIdAndUserId(String groupId, String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("groupId").is(new ObjectId(groupId)).and("userId").is(new ObjectId(userId)));
        DeleteResult groupusers = mongoTemplate.remove(query, "groupusers");
        // System.out.println("删除该群成员是否成功？" + groupusers.getDeletedCount());
    }

    private void decrGroupUserNum(String gid) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(new ObjectId(gid)));
        Update update = new Update();
        update.inc("userNum", -1); //该群人数减去1
        UpdateResult groups = mongoTemplate.upsert(query, update, "groups");
        // System.out.println("该群人数递减1是否成功？" + groups.getModifiedCount());
    }
}
