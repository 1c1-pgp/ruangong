import http, { type ApiEnvelope } from './http'

export interface FriendItem {
  id: string
  nickname?: string
  photo?: string
  signature?: string
  roomId?: string
  level?: number
}

export interface ChatMessage {
  id?: string
  roomId?: string
  senderId?: string
  senderName?: string
  senderNickname?: string
  senderAvatar?: string
  time?: string | number | Date
  message?: string
  messageType?: string
  fileRawName?: string
  isReadUser?: string[]
  deletedFor?: string[]
  revoked?: boolean
  revokerId?: string
  revokedAt?: string
}

export interface GroupItem {
  id?: string
  groupId?: string
  groupInfo?: {
    gid?: string
    title?: string
    desc?: string
    img?: string
    code?: string
  }
  userInfo?: {
    username?: string
  }
}

export interface ValidateMessageItem {
  id?: string
  roomId?: string
  senderId?: string
  senderName?: string
  senderNickname?: string
  senderAvatar?: string
  receiverId?: string
  time?: string
  additionMessage?: string
  status?: number
  validateType?: number
  groupInfo?: {
    gid?: string
    title?: string
    img?: string
    code?: string
  }
}

export async function loadFriends(userId: string): Promise<FriendItem[]> {
  const { data } = await http.get<ApiEnvelope>('/chat/goodFriend/getMyFriendsList', {
    params: { userId },
  })
  const list = data.data?.myFriendsList
  return Array.isArray(list) ? (list as FriendItem[]) : []
}

export async function loadRecentMessages(
  roomId: string,
  pageIndex = 0,
  pageSize = 50,
  userId?: string,
): Promise<ChatMessage[]> {
  const { data } = await http.get<ApiEnvelope>('/chat/singleMessage/getRecentSingleMessages', {
    params: { roomId, pageIndex, pageSize, userId },
  })
  const list = data.data?.recentMessage
  return Array.isArray(list) ? (list as ChatMessage[]) : []
}

export async function searchSingleHistory(params: {
  roomId: string
  type?: string
  query?: string
  date?: string | Date | null
  pageIndex?: number
  pageSize?: number
  userId?: string
}): Promise<{ total: number; list: ChatMessage[] }> {
  const { data } = await http.post<ApiEnvelope>('/chat/singleMessage/historyMessage', {
    type: 'all',
    query: '',
    pageIndex: 0,
    pageSize: 50,
    ...params,
  })
  const list = data.data?.msgList
  const total = data.data?.total
  return {
    total: typeof total === 'number' ? total : 0,
    list: Array.isArray(list) ? (list as ChatMessage[]) : [],
  }
}

export async function deleteSingleMessageForMe(messageId: string, roomId: string, userId: string): Promise<void> {
  await http.post<ApiEnvelope>('/chat/singleMessage/deleteForMe', { messageId, roomId, userId })
}

export async function revokeSingleMessage(messageId: string, roomId: string, userId: string): Promise<ChatMessage | null> {
  const { data } = await http.post<ApiEnvelope>('/chat/singleMessage/revoke', { messageId, roomId, userId })
  return (data.data?.message as ChatMessage | undefined) ?? null
}

export async function markSingleMessagesRead(roomId: string, userId: string): Promise<void> {
  await http.post<ApiEnvelope>('/chat/singleMessage/isRead', { roomId, userId })
}

export async function loadGroups(username: string): Promise<GroupItem[]> {
  const { data } = await http.get<ApiEnvelope>('/chat/group/getMyGroupList', {
    params: { username },
  })
  const list = data.data?.myGroupList
  return Array.isArray(list) ? (list as GroupItem[]) : []
}

export async function loadRecentGroupMessages(roomId: string, pageIndex = 0, pageSize = 50): Promise<ChatMessage[]> {
  const { data } = await http.get<ApiEnvelope>('/chat/groupMessage/getRecentGroupMessages', {
    params: { roomId, pageIndex, pageSize },
  })
  const list = data.data?.recentGroupMessages
  return Array.isArray(list) ? (list as ChatMessage[]) : []
}

export async function loadValidateMessages(userId: string): Promise<ValidateMessageItem[]> {
  const { data } = await http.get<ApiEnvelope>('/chat/validate/getMyValidateMessageList', {
    params: { userId },
  })
  const list = data.data?.validateMessageList
  return Array.isArray(list) ? (list as ValidateMessageItem[]) : []
}

/** type 为 Mongo 字段名，如 username、nickname */
export async function searchUsers(
  searchContent: string,
  type = 'username',
  pageIndex = 0,
  pageSize = 10,
): Promise<unknown[]> {
  const { data } = await http.post<ApiEnvelope>('/chat/user/preFetchUser', {
    type,
    searchContent,
    pageIndex,
    pageSize,
  })
  const list = data.data?.userList
  return Array.isArray(list) ? list : []
}
