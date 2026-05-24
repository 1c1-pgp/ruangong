package com.zzw.chatserver.service;

import com.zzw.chatserver.dao.SingleMessageDao;
import com.zzw.chatserver.pojo.SingleMessage;
import com.zzw.chatserver.pojo.vo.HistoryMsgRequestVo;
import com.zzw.chatserver.pojo.vo.IsReadMessageRequestVo;
import com.zzw.chatserver.pojo.vo.SingleMessageActionRequestVo;
import com.zzw.chatserver.pojo.vo.SingleHistoryResultVo;
import com.zzw.chatserver.pojo.vo.SingleMessageResultVo;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class SingleMessageService {
    @Resource
    private SingleMessageDao singleMessageDao;
    @Resource
    private MongoTemplate mongoTemplate;

    public SingleMessageResultVo getLastMessage(String roomId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("roomId").is(roomId))
                .with(Sort.by(Sort.Direction.DESC, "_id"));
        SingleMessageResultVo message = mongoTemplate.findOne(query, SingleMessageResultVo.class, "singlemessages");
        if (message == null) {
            message = new SingleMessageResultVo();
            message.setRoomId(roomId);
            message.setMessageType("text");
            message.setMessage("");
        }
        return message;
    }

    //获取好友之间的聊天记录，通过房间id来获取，房间id是由两个好友id组成，所以是唯一的
    public List<SingleMessageResultVo> getRecentMessage(String roomId, Integer pageIndex, Integer pageSize) {
        return getRecentMessage(roomId, pageIndex, pageSize, null);
    }

    public List<SingleMessageResultVo> getRecentMessage(String roomId, Integer pageIndex, Integer pageSize, String userId) {
        Query query = new Query();
        Criteria criteria = Criteria.where("roomId").is(roomId);
        if (userId != null && !userId.trim().isEmpty()) {
            criteria.and("deletedFor").ne(userId);
        }
        int safePageIndex = pageIndex == null || pageIndex < 0 ? 0 : pageIndex;
        int safePageSize = pageSize == null || pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        query.addCriteria(criteria)
                .with(Sort.by(Sort.Direction.DESC, "time").and(Sort.by(Sort.Direction.DESC, "_id")))
                .skip((long) safePageIndex * safePageSize)
                .limit(safePageSize);
        return mongoTemplate.find(query, SingleMessageResultVo.class, "singlemessages");
    }

    //在用户切换到某条会话之后将给会话下的所有消息设置为已读
    public void userIsReadMessage(IsReadMessageRequestVo ivo) {
        if (ivo.getUserId() == null || ivo.getRoomId() == null || !ObjectId.isValid(ivo.getUserId())) return;
        Update update = new Update();
        update.addToSet("isReadUser", ivo.getUserId());
        Query query = new Query();
        query.addCriteria(Criteria.where("roomId").is(ivo.getRoomId()).and("senderId").ne(new ObjectId(ivo.getUserId())));
        mongoTemplate.updateMulti(query, update, "singlemessages");
    }

    public SingleHistoryResultVo getSingleHistoryMsg(HistoryMsgRequestVo historyMsgRequestVo) {
        Criteria cri1 = new Criteria();
        cri1.and("roomId").is(historyMsgRequestVo.getRoomId());
        if (historyMsgRequestVo.getUserId() != null && !historyMsgRequestVo.getUserId().trim().isEmpty()) {
            cri1.and("deletedFor").ne(historyMsgRequestVo.getUserId());
        }
        //若查询条件是全部，则模糊匹配 message 或者 fileRawName
        //若查询条件不是全部，则设置搜索类型，并且模糊匹配 fileRawName
        Criteria cri2 = null;
        String type = historyMsgRequestVo.getType() == null ? "all" : historyMsgRequestVo.getType();
        String keyword = historyMsgRequestVo.getQuery() == null ? "" : historyMsgRequestVo.getQuery();
        if (!type.equals("all")) {
            //若查询类型是文件或图片，则模糊匹配原文件名
            cri1.and("messageType").is(type)
                    .and("fileRawName").regex(Pattern.compile("^.*" + Pattern.quote(keyword) + ".*$", Pattern.CASE_INSENSITIVE));
        } else {
            cri2 = new Criteria().orOperator(Criteria.where("message").regex(Pattern.compile("^.*" + Pattern.quote(keyword) + ".*$", Pattern.CASE_INSENSITIVE)),
                    Criteria.where("fileRawName").regex(Pattern.compile("^.*" + Pattern.quote(keyword) + ".*$", Pattern.CASE_INSENSITIVE)));
        }
        if (historyMsgRequestVo.getDate() != null) {
            Calendar calendar = new GregorianCalendar();
            calendar.setTime(historyMsgRequestVo.getDate());
            calendar.add(Calendar.DATE, 1);
            Date tomorrow = calendar.getTime();
            cri1.and("time").gte(historyMsgRequestVo.getDate()).lt(tomorrow);
            // System.out.println("today：" + historyMsgRequestVo.getDate() + "，tomorrow：" + tomorrow);
        }
        // 创建查询对象
        Query query = new Query();
        if (cri2 != null) query.addCriteria(new Criteria().andOperator(cri1, cri2));
        else query.addCriteria(cri1);
        // 统计总数
        long total = mongoTemplate.count(query, SingleMessageResultVo.class, "singlemessages");
        // 设置分页
        int safePageIndex = historyMsgRequestVo.getPageIndex() == null || historyMsgRequestVo.getPageIndex() < 0 ? 0 : historyMsgRequestVo.getPageIndex();
        int safePageSize = historyMsgRequestVo.getPageSize() == null || historyMsgRequestVo.getPageSize() <= 0 ? 50 : Math.min(historyMsgRequestVo.getPageSize(), 200);
        query.with(Sort.by(Sort.Direction.DESC, "time").and(Sort.by(Sort.Direction.DESC, "_id")));
        query.skip((long) safePageIndex * safePageSize); //页码
        query.limit(safePageSize); //每页显示数量
        List<SingleMessageResultVo> messageList = mongoTemplate.find(query, SingleMessageResultVo.class, "singlemessages"); //必须带上集合名称
        return new SingleHistoryResultVo(messageList, total);
    }

    public SingleMessageResultVo addNewSingleMessage(SingleMessage message) {
        SingleMessage saved = singleMessageDao.save(message);
        return toResultVo(saved);
    }

    public boolean deleteForUser(SingleMessageActionRequestVo requestVo) {
        if (!isValidActionRequest(requestVo)) return false;
        Query query = new Query(Criteria.where("_id").is(new ObjectId(requestVo.getMessageId()))
                .and("roomId").is(requestVo.getRoomId()));
        Update update = new Update().addToSet("deletedFor", requestVo.getUserId());
        return mongoTemplate.updateFirst(query, update, SingleMessage.class).getModifiedCount() > 0;
    }

    public SingleMessageResultVo revokeMessage(SingleMessageActionRequestVo requestVo) {
        if (!isValidActionRequest(requestVo)) return null;
        Query query = new Query(Criteria.where("_id").is(new ObjectId(requestVo.getMessageId()))
                .and("roomId").is(requestVo.getRoomId())
                .and("senderId").is(new ObjectId(requestVo.getUserId()))
                .and("revoked").ne(true));
        Update update = new Update()
                .set("revoked", true)
                .set("revokerId", requestVo.getUserId())
                .set("revokedAt", new Date())
                .set("message", "该消息已撤回")
                .set("messageType", "revoked");
        if (mongoTemplate.updateFirst(query, update, SingleMessage.class).getModifiedCount() == 0) {
            return null;
        }
        SingleMessage message = mongoTemplate.findById(new ObjectId(requestVo.getMessageId()), SingleMessage.class);
        return toResultVo(message);
    }

    private boolean isValidActionRequest(SingleMessageActionRequestVo requestVo) {
        return requestVo != null
                && ObjectId.isValid(requestVo.getMessageId())
                && requestVo.getRoomId() != null
                && ObjectId.isValid(requestVo.getUserId());
    }

    private SingleMessageResultVo toResultVo(SingleMessage message) {
        if (message == null) return null;
        SingleMessageResultVo vo = new SingleMessageResultVo();
        BeanUtils.copyProperties(message, vo);
        if (message.getId() != null) vo.setId(message.getId().toHexString());
        if (message.getSenderId() != null) vo.setSenderId(message.getSenderId().toHexString());
        return vo;
    }

}
