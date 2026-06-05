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
  holder?: number
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

export interface GroupSearchItem {
  gid?: string
  title?: string
  desc?: string
  img?: string
  code?: string
  userNum?: number
  holderName?: string
  holderUserInfo?: {
    uid?: string
    username?: string
    nickname?: string
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

export async function uploadChatFile(
  file: File,
  onProgress?: (percent: number) => void,
): Promise<{ filePath: string; fileRawName: string }> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await http.post<ApiEnvelope>('/chat/sys/uploadFile', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (ev: ProgressEvent) => {
      if (onProgress && ev.total) {
        const p = Math.round((ev.loaded / ev.total) * 100)
        onProgress(p)
      }
    },
  })
  return {
    filePath: String(data.data?.filePath ?? ''),
    fileRawName: String(data.data?.fileRawName ?? file.name),
  }
}

export async function searchGroupHistory(params: {
  roomId: string
  type?: string
  query?: string
  pageIndex?: number
  pageSize?: number
}): Promise<{ total: number; list: ChatMessage[] }> {
  const { data } = await http.post<ApiEnvelope>('/chat/groupMessage/historyMessages', {
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

export async function searchGroups(
  searchContent: string,
  type: 'code' | 'title' = 'code',
  pageIndex = 0,
  pageSize = 10,
): Promise<GroupSearchItem[]> {
  const { data } = await http.post<ApiEnvelope>('/chat/group/preFetchGroup', {
    type,
    searchContent,
    pageIndex,
    pageSize,
  })
  const list = data.data?.groupList
  return Array.isArray(list) ? (list as GroupSearchItem[]) : []
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

export async function createGroup(params: {
  title: string
  desc?: string
  holderName: string
  holderUserId: string
}): Promise<{ groupId: string; groupCode: string }> {
  const { data } = await http.post<ApiEnvelope>('/chat/group/createGroup', params)
  return {
    groupId: String(data.data?.groupId ?? ''),
    groupCode: String(data.data?.groupCode ?? ''),
  }
}

export async function inviteToGroup(params: {
  groupId: string
  invitedUserIds: string[]
  inviterUserId: string
}): Promise<{ invitedCount: number; invitedUserIds: string[]; skippedUserIds: string[] }> {
  const { data } = await http.post<ApiEnvelope>('/chat/group/inviteToGroup', params)
  return {
    invitedCount: Number(data.data?.invitedCount ?? 0),
    invitedUserIds: Array.isArray(data.data?.invitedUserIds) ? (data.data?.invitedUserIds as string[]) : [],
    skippedUserIds: Array.isArray(data.data?.skippedUserIds) ? (data.data?.skippedUserIds as string[]) : [],
  }
}

export async function quitGroup(groupId: string, userId: string): Promise<void> {
  await http.post<ApiEnvelope>('/chat/group/quitGroup', { groupId, userId, holder: 0 })
}

export async function dissolveGroup(groupId: string): Promise<void> {
  await http.post<ApiEnvelope>('/chat/group/dissolveGroup', { groupId })
}

export async function loadRecentGroupMessages(roomId: string, pageIndex = 0, pageSize = 50): Promise<ChatMessage[]> {
  const { data } = await http.get<ApiEnvelope>('/chat/groupMessage/getRecentGroupMessages', {
    params: { roomId, pageIndex, pageSize },
  })
  const list = data.data?.recentGroupMessages
  return Array.isArray(list) ? (list as ChatMessage[]) : []
}

export async function getGroupInfo(groupId: string): Promise<{
  groupInfo: Record<string, unknown>
  members: Array<{ userId?: string; username?: string; holder?: number; userInfo?: { nickname?: string; photo?: string } }>
}> {
  const { data } = await http.get<ApiEnvelope>('/chat/group/getGroupInfo', { params: { groupId } })
  return {
    groupInfo: (data.data?.groupInfo as Record<string, unknown>) ?? {},
    members: Array.isArray(data.data?.users) ? data.data.users : [],
  }
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

export async function addFriendByUsername(targetUsername: string, targetNickname: string): Promise<void> {
  await http.post<ApiEnvelope>('/chat/goodFriend/addByUsername', {
    targetUsername,
    targetNickname,
  })
}
