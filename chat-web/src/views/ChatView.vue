<template>
  <el-container class="chat-root">
    <el-header class="header">
      <div class="brand">即时通讯</div>
      <div class="me">
        <span>{{ session.userInfo?.nickname || session.userInfo?.username }}</span>
        <el-button text type="danger" @click="logout">退出</el-button>
      </div>
    </el-header>
    <el-container class="body">
      <el-aside width="320px" class="aside">
        <div class="aside-head">
          <div class="aside-head-title">会话</div>
          <div class="aside-actions">
            <el-button text size="small" @click="activeTab = 'search'">搜索用户</el-button>
          </div>
        </div>

        <div class="aside-tabs">
          <el-button
            type="text"
            class="tab-btn"
            :class="{ active: activeTab === 'friends' }"
            @click="activeTab = 'friends'"
          >
            好友
          </el-button>
          <el-button
            type="text"
            class="tab-btn"
            :class="{ active: activeTab === 'groups' }"
            @click="activeTab = 'groups'"
          >
            群聊
          </el-button>
          <el-badge :value="pendingCount" class="validate-badge">
            <el-button
              type="text"
              class="tab-btn"
              :class="{ active: activeTab === 'validate' }"
              @click="activeTab = 'validate'"
            >
              验证
            </el-button>
          </el-badge>
        </div>

        <el-scrollbar class="friend-scroll">
          <template v-if="activeTab === 'friends'">
            <div
              v-for="f in friends"
              :key="f.id"
              class="friend-item"
              :class="{ active: activeConversation?.roomId === f.roomId }"
              @click="selectFriend(f)"
            >
              <el-avatar :size="40" :src="avatarUrl(f.photo)" />
              <div class="friend-meta">
                <div class="name-row">
                  <span class="nick">{{ f.nickname || f.id }}</span>
                  <span v-if="onlineSet.has(f.id)" class="dot" title="在线" />
                </div>
                <div class="sig">{{ f.signature || '暂无签名' }}</div>
              </div>
            </div>
            <el-empty v-if="!friends.length && !friendsLoading" description="暂无好友" />
          </template>

          <template v-else-if="activeTab === 'groups'">
            <div
              v-for="g in groups"
              :key="g.groupId || g.id"
              class="friend-item"
              :class="{ active: activeConversation?.roomId === getGroupId(g) }"
              @click="selectGroup(g)"
            >
              <el-avatar :size="40" :src="avatarUrl(g.groupInfo?.img)" />
              <div class="friend-meta">
                <div class="name-row">
                  <span class="nick">{{ g.groupInfo?.title || g.groupInfo?.code || '群聊' }}</span>
                </div>
                <div class="sig">群号：{{ g.groupInfo?.code || getGroupId(g) }}</div>
              </div>
            </div>
            <el-empty v-if="!groups.length" description="暂无群聊" />
          </template>

          <template v-else-if="activeTab === 'search'">
            <div class="search-box">
              <el-input
                v-model="searchQuery"
                placeholder="用户名 / 昵称"
                @keyup.enter="doSearch"
                clearable
              />
              <el-button type="primary" @click="doSearch" :loading="searchLoading">搜索</el-button>
            </div>
            <div class="search-list">
              <div
                v-for="user in searchResults"
                :key="getUserId(user)"
                class="search-item"
              >
                <div class="user-info">
                  <div class="nick">{{ user.nickname || user.username || getUserId(user) }}</div>
                  <div class="sig">{{ user.signature || '暂无个性签名' }}</div>
                </div>
                <el-button
                  size="mini"
                  type="primary"
                  :disabled="sendingRequestSet.has(getUserId(user)) || getUserId(user) === session.uid"
                  @click="sendFriendRequest(user)"
                >
                  {{ getUserId(user) === session.uid ? '当前用户' : '加好友' }}
                </el-button>
              </div>
              <el-empty
                v-if="!searchLoading && !searchResults.length"
                description="输入关键词搜索用户"
              />
            </div>
          </template>

          <template v-else>
            <div class="validate-list">
              <div
                v-for="item in validateList"
                :key="item.id"
                class="validate-item"
              >
                <div class="validate-meta">
                  <div>
                    <strong>{{ item.senderNickname || item.senderName }}</strong>
                    <span class="validate-type">{{ item.validateType === 1 ? '群聊申请' : '好友申请' }}</span>
                  </div>
                  <div class="sig">{{ item.additionMessage || '请求添加你为好友' }}</div>
                  <div v-if="item.validateType === 1" class="sub">
                    目标群：{{ item.groupInfo?.title || item.groupInfo?.code || '未知群' }}
                  </div>
                </div>
                <div class="validate-actions">
                  <el-button
                    size="mini"
                    type="success"
                    @click="agreeRequest(item)"
                    :disabled="(item.status ?? 0) !== 0"
                  >
                    同意
                  </el-button>
                  <el-button
                    size="mini"
                    type="danger"
                    @click="rejectRequest(item)"
                    :disabled="(item.status ?? 0) !== 0"
                  >
                    拒绝
                  </el-button>
                  <span v-if="item.status === 1" class="status-label">已同意</span>
                  <span v-else-if="item.status === 2" class="status-label">已拒绝</span>
                </div>
              </div>
              <el-empty
                v-if="!validateLoading && !validateList.length"
                description="暂无验证消息"
              />
            </div>
          </template>
        </el-scrollbar>
      </el-aside>

      <el-main class="main">
        <template v-if="activeConversation">
          <div class="chat-head">
            <span>{{ activeConversation.title }}</span>
            <span class="sub">{{ activeConversation.subtitle }}</span>
          </div>
          <div v-if="activeConversation.conversationType === 'FRIEND'" class="history-tools">
            <el-input
              v-model="historyQuery"
              clearable
              placeholder="搜索当前私聊历史"
              @keyup.enter="searchHistory"
            />
            <el-button :loading="historyLoading" @click="searchHistory">搜索</el-button>
            <el-button text @click="reloadCurrentConversation">最近消息</el-button>
          </div>
          <el-scrollbar ref="scrollRef" class="msg-scroll">
            <div class="msg-list">
              <div
                v-for="(m, idx) in messages"
                :key="m.id || idx"
                class="bubble-row"
                :class="{ mine: m.senderId === session.uid }"
              >
                <div class="bubble">
                  <div class="meta">{{ m.senderNickname || m.senderName }} · {{ formatTime(m.time) }}</div>
                  <div class="text" :class="{ revoked: m.revoked || m.messageType === 'revoked' }">
                    {{ m.revoked || m.messageType === 'revoked' ? '该消息已撤回' : m.message }}
                  </div>
                  <div v-if="m.id && activeConversation.conversationType === 'FRIEND'" class="msg-actions">
                    <el-button text size="small" @click.stop="deleteMessage(m)">删除</el-button>
                    <el-button
                      v-if="canRevoke(m)"
                      text
                      size="small"
                      type="warning"
                      @click.stop="revokeMessage(m)"
                    >
                      撤回
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </el-scrollbar>
          <div class="composer">
            <el-input
              v-model="draft"
              type="textarea"
              :rows="3"
              placeholder="输入消息，Ctrl+Enter 发送"
              @keydown.ctrl.enter.prevent="send"
            />
            <el-button type="primary" :disabled="!draft.trim()" @click="send">发送</el-button>
          </div>
        </template>
        <el-empty v-else description="请选择左侧好友或群聊开始聊天" />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useSessionStore, normalizeUid } from '@/stores/session'
import {
  loadFriends,
  loadRecentMessages,
  searchSingleHistory,
  deleteSingleMessageForMe,
  markSingleMessagesRead,
  loadGroups,
  loadRecentGroupMessages,
  loadValidateMessages,
  searchUsers,
  type FriendItem,
  type ChatMessage,
  type GroupItem,
  type ValidateMessageItem,
} from '@/api/chat'
import { ensureSocket, teardownSocket } from '@/socket'

const router = useRouter()
const session = useSessionStore()

const friends = ref<FriendItem[]>([])
const friendsLoading = ref(false)
const groups = ref<GroupItem[]>([])
const validateList = ref<ValidateMessageItem[]>([])
const validateLoading = ref(false)
const searchQuery = ref('')
const searchResults = ref<unknown[]>([])
const searchLoading = ref(false)
const sendingRequestSet = ref(new Set<string>())
const activeTab = ref<'friends' | 'groups' | 'search' | 'validate'>('friends')
const activeConversation = ref<{
  roomId: string
  title: string
  subtitle?: string
  conversationType: 'FRIEND' | 'GROUP'
} | null>(null)
const messages = ref<ChatMessage[]>([])
const draft = ref('')
const onlineSet = ref(new Set<string>())
const scrollRef = ref<{ setScrollTop?: (v: number) => void }>()
const historyQuery = ref('')
const historyLoading = ref(false)

const pendingCount = computed(() => validateList.value.filter((item) => item.status === 0).length)

function avatarUrl(photo?: string) {
  if (!photo) return undefined
  if (photo.startsWith('http')) return photo
  return `/chat${photo.startsWith('/') ? '' : '/'}${photo}`
}

function formatTime(t: ChatMessage['time']) {
  if (!t) return ''
  const d = typeof t === 'string' || typeof t === 'number' ? new Date(t) : t
  return d.toLocaleString()
}

function getUserId(user: any): string {
  if (!user) return ''
  if (typeof user.id === 'string') return user.id
  if (typeof user.uid === 'string') return user.uid
  if (typeof user.userId === 'string') return user.userId
  if (user.userId && typeof user.userId === 'object' && typeof user.userId.$oid === 'string') return user.userId.$oid
  return ''
}

function mergeSearchResults(...lists: unknown[][]): unknown[] {
  const merged: unknown[] = []
  const seen = new Set<string>()

  for (const list of lists) {
    for (const user of list) {
      const id = getUserId(user)
      const key = id || JSON.stringify(user)
      if (seen.has(key)) continue
      seen.add(key)
      merged.push(user)
    }
  }

  return merged
}

function getGroupId(group: GroupItem) {
  return group.groupId || group.id || group.groupInfo?.gid || ''
}

function cacheKey(roomId: string) {
  return `single-history:${session.uid}:${roomId}`
}

function loadCachedMessages(roomId: string): ChatMessage[] {
  try {
    const raw = localStorage.getItem(cacheKey(roomId))
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? (parsed as ChatMessage[]) : []
  } catch {
    return []
  }
}

function cacheMessages(roomId: string, list: ChatMessage[]) {
  try {
    localStorage.setItem(cacheKey(roomId), JSON.stringify(list.slice(-100)))
  } catch {
    /* 浏览器缓存满时不影响主流程 */
  }
}

async function selectFriend(f: FriendItem) {
  if (!f.roomId) {
    ElMessage.warning('该好友缺少 roomId，无法聊天')
    return
  }

  activeConversation.value = {
    roomId: f.roomId,
    title: f.nickname || f.id || '好友聊天',
    subtitle: f.signature || f.roomId,
    conversationType: 'FRIEND',
  }
  joinRoom(f.roomId)
  const cached = loadCachedMessages(f.roomId)
  if (cached.length) messages.value = cached

  try {
    const list = await loadRecentMessages(f.roomId, 0, 80, session.uid)
    messages.value = [...list].reverse()
    cacheMessages(f.roomId, messages.value)
    await markCurrentRead(f.roomId)
    await nextTick()
    scrollBottom()
  } catch {
    ElMessage.error('加载消息失败')
    messages.value = []
  }
}

async function selectGroup(group: GroupItem) {
  const roomId = getGroupId(group)
  if (!roomId) {
    ElMessage.warning('该群缺少 roomId，无法进入')
    return
  }

  activeConversation.value = {
    roomId,
    title: group.groupInfo?.title || group.groupInfo?.code || '群聊',
    subtitle: `群号 ${group.groupInfo?.code || roomId}`,
    conversationType: 'GROUP',
  }
  joinRoom(roomId)

  try {
    const list = await loadRecentGroupMessages(roomId, 0, 80)
    messages.value = [...list].reverse()
    await nextTick()
    scrollBottom()
  } catch {
    ElMessage.error('加载群消息失败')
    messages.value = []
  }
}

function joinRoom(roomId: string) {
  const sock = ensureSocket()
  sock.emit('join', { roomId })
}

async function send() {
  const text = draft.value.trim()
  const conv = activeConversation.value
  const uid = session.uid
  if (!text || !conv?.roomId || !uid || !session.userInfo) return

  const msg = {
    roomId: conv.roomId,
    senderId: uid,
    senderName: String(session.userInfo.username || ''),
    senderNickname: String(session.userInfo.nickname || session.userInfo.username || ''),
    senderAvatar: String(session.userInfo.photo || ''),
    time: new Date(),
    fileRawName: '',
    message: text,
    messageType: 'text',
    isReadUser: [uid],
    conversationType: conv.conversationType,
  }
  const sock = ensureSocket()
  sock.emit('sendNewMessage', msg)
  messages.value.push({
    ...msg,
    time: msg.time.toISOString(),
  })
  if (conv.conversationType === 'FRIEND') cacheMessages(conv.roomId, messages.value)
  draft.value = ''
  nextTick(() => scrollBottom())
}

async function reloadCurrentConversation() {
  const conv = activeConversation.value
  if (!conv) return
  historyQuery.value = ''
  try {
    const list = conv.conversationType === 'FRIEND'
      ? await loadRecentMessages(conv.roomId, 0, 80, session.uid)
      : await loadRecentGroupMessages(conv.roomId, 0, 80)
    messages.value = [...list].reverse()
    await nextTick()
    scrollBottom()
  } catch {
    ElMessage.error('加载消息失败')
  }
}

async function searchHistory() {
  const conv = activeConversation.value
  if (!conv || conv.conversationType !== 'FRIEND') return
  historyLoading.value = true
  try {
    const { list, total } = await searchSingleHistory({
      roomId: conv.roomId,
      type: 'all',
      query: historyQuery.value.trim(),
      pageIndex: 0,
      pageSize: 80,
      userId: session.uid,
    })
    messages.value = [...list].reverse()
    await markCurrentRead(conv.roomId)
    if (!total) ElMessage.info('没有匹配的历史消息')
    await nextTick()
    scrollBottom()
  } catch {
    ElMessage.error('搜索历史消息失败')
  } finally {
    historyLoading.value = false
  }
}

function canRevoke(message: ChatMessage) {
  return Boolean(message.id && message.senderId === session.uid && !message.revoked && message.messageType !== 'revoked')
}

async function deleteMessage(message: ChatMessage) {
  const conv = activeConversation.value
  if (!message.id || !conv?.roomId || !session.uid) return
  try {
    await deleteSingleMessageForMe(message.id, conv.roomId, session.uid)
    messages.value = messages.value.filter((item) => item.id !== message.id)
    cacheMessages(conv.roomId, messages.value)
    ElMessage.success('已删除')
  } catch {
    ElMessage.error('删除失败')
  }
}

async function revokeMessage(message: ChatMessage) {
  const conv = activeConversation.value
  if (!message.id || !conv?.roomId || !session.uid) return
  try {
    const sock = ensureSocket()
    sock.emit('revokeSingleMessage', {
      messageId: message.id,
      roomId: conv.roomId,
      userId: session.uid,
    })
  } catch {
    ElMessage.error('撤回失败')
  }
}

function applySavedMessage(saved: ChatMessage) {
  if (!saved.roomId || saved.roomId !== activeConversation.value?.roomId) return
  const pendingIndex = messages.value.findIndex((item) =>
    !item.id
    && item.senderId === saved.senderId
    && item.message === saved.message
    && item.messageType === saved.messageType
  )
  if (pendingIndex >= 0) {
    messages.value.splice(pendingIndex, 1, saved)
    cacheMessages(saved.roomId, messages.value)
  }
}

function applyRevokedMessage(revoked: ChatMessage) {
  if (!revoked.id) return
  const index = messages.value.findIndex((item) => item.id === revoked.id)
  if (index >= 0) {
    messages.value.splice(index, 1, { ...messages.value[index], ...revoked, revoked: true, messageType: 'revoked' })
    if (revoked.roomId) cacheMessages(revoked.roomId, messages.value)
  }
}

async function markCurrentRead(roomId: string) {
  if (!session.uid) return
  try {
    await markSingleMessagesRead(roomId, session.uid)
    const sock = ensureSocket()
    sock.emit('isReadMsg', { roomId, userId: session.uid })
  } catch {
    /* 已读状态失败不影响聊天主流程 */
  }
}

function scrollBottom() {
  scrollRef.value?.setScrollTop?.(1e9)
}

function logout() {
  teardownSocket()
  session.clear()
  router.push('/login')
}

async function doSearch() {
  const keyword = searchQuery.value.trim()
  if (!keyword) {
    ElMessage.warning('请输入搜索内容')
    return
  }
  searchLoading.value = true
  try {
    const [usernameMatches, nicknameMatches] = await Promise.all([
      searchUsers(keyword, 'username', 0, 20),
      searchUsers(keyword, 'nickname', 0, 20),
    ])
    searchResults.value = mergeSearchResults(usernameMatches, nicknameMatches)
    if (!searchResults.value.length) {
      ElMessage.info('未找到匹配用户')
    }
  } catch {
    ElMessage.error('搜索用户失败')
  } finally {
    searchLoading.value = false
  }
}

async function sendFriendRequest(user: unknown) {
  const targetId = getUserId(user)
  if (!targetId) {
    ElMessage.warning('无法识别用户ID')
    return
  }
  if (targetId === session.uid) {
    ElMessage.warning('不能添加自己为好友')
    return
  }
  sendingRequestSet.value.add(targetId)
  try {
    const sock = ensureSocket()
    sock.emit('sendValidateMessage', {
      roomId: targetId,
      senderId: session.uid,
      senderName: String(session.userInfo?.username || ''),
      senderNickname: String(session.userInfo?.nickname || session.userInfo?.username || ''),
      senderAvatar: String(session.userInfo?.photo || ''),
      receiverId: targetId,
      additionMessage: '我想加你为好友',
      time: new Date().toISOString(),
      status: 0,
      validateType: 0,
    })
    ElMessage.success('好友申请已发送')
  } catch {
    ElMessage.error('发送好友申请失败')
  } finally {
    sendingRequestSet.value.delete(targetId)
  }
}

async function agreeRequest(item: ValidateMessageItem) {
  if (!item.id) return
  try {
    const sock = ensureSocket()
    if (item.validateType === 1) {
      sock.emit('sendAgreeGroupValidate', item)
    } else {
      sock.emit('sendAgreeFriendValidate', item)
    }
    ElMessage.success('已同意')
    await reloadValidateAndLists()
  } catch {
    ElMessage.error('同意失败')
  }
}

async function rejectRequest(item: ValidateMessageItem) {
  if (!item.id) return
  try {
    const sock = ensureSocket()
    if (item.validateType === 1) {
      sock.emit('sendDisAgreeGroupValidate', item)
    } else {
      sock.emit('sendDisAgreeFriendValidate', item)
    }
    ElMessage.success('已拒绝')
    await reloadValidateAndLists()
  } catch {
    ElMessage.error('拒绝失败')
  }
}

async function loadFriendList() {
  const uid = session.uid
  if (!uid) {
    router.push('/login')
    return
  }
  friendsLoading.value = true
  try {
    friends.value = await loadFriends(uid)
  } catch {
    ElMessage.error('加载好友列表失败')
  } finally {
    friendsLoading.value = false
  }
}

async function loadGroupList() {
  const username = session.userInfo?.username
  if (!username) return
  try {
    groups.value = await loadGroups(username)
  } catch {
    ElMessage.error('加载群聊列表失败')
  }
}

async function loadValidateList() {
  const uid = session.uid
  if (!uid) return
  validateLoading.value = true
  try {
    validateList.value = await loadValidateMessages(uid)
  } catch {
    ElMessage.error('加载验证消息失败')
  } finally {
    validateLoading.value = false
  }
}

async function reloadValidateAndLists() {
  await Promise.all([loadValidateList(), loadFriendList(), loadGroupList()])
}

function bindSocket() {
  const sock = ensureSocket()

  const onOnline = (uids: unknown) => {
    const arr = Array.isArray(uids) ? uids : []
    onlineSet.value = new Set(arr.map((x) => String(x)))
  }

  const onRecv = (raw: Record<string, unknown>) => {
    const roomId = String(raw.roomId || '')
    if (!activeConversation.value?.roomId || roomId !== activeConversation.value.roomId) return
    messages.value.push(raw as unknown as ChatMessage)
    cacheMessages(roomId, messages.value)
    markCurrentRead(roomId)
    nextTick(() => scrollBottom())
  }

  const onMessageSaved = (raw: Record<string, unknown>) => {
    applySavedMessage(raw as unknown as ChatMessage)
  }

  const onMessageRevoked = (raw: Record<string, unknown>) => {
    applyRevokedMessage(raw as unknown as ChatMessage)
  }

  const onRead = (raw: Record<string, unknown>) => {
    const roomId = String(raw.roomId || '')
    const userId = String(raw.userId || '')
    if (!roomId || !userId || roomId !== activeConversation.value?.roomId) return
    messages.value = messages.value.map((item) => {
      const users = item.isReadUser ?? []
      return users.includes(userId) ? item : { ...item, isReadUser: [...users, userId] }
    })
  }

  const onValidateMessage = () => {
    loadValidateList()
    ElMessage.info('你收到一条新的验证消息')
  }

  const onAgreeFriend = () => {
    loadFriendList()
    ElMessage.success('好友请求已被对方同意')
  }

  const onAgreeGroup = () => {
    loadGroupList()
    ElMessage.success('群聊申请已被同意')
  }

  const onDelFriend = () => {
    loadFriendList()
    ElMessage.info('好友关系发生变更，已刷新好友列表')
  }

  const onQuitGroup = () => {
    loadGroupList()
    ElMessage.info('群聊列表已更新')
  }

  sock.off('onlineUser')
  sock.off('receiveMessage')
  sock.off('messageSaved')
  sock.off('singleMessageRevoked')
  sock.off('revokeSingleMessageFailed')
  sock.off('isReadMsg')
  sock.off('receiveValidateMessage')
  sock.off('receiveAgreeFriendValidate')
  sock.off('receiveAgreeGroupValidate')
  sock.off('receiveDelGoodFriend')
  sock.off('receiveQuitGroup')

  sock.on('onlineUser', onOnline)
  sock.on('receiveMessage', onRecv)
  sock.on('messageSaved', onMessageSaved)
  sock.on('singleMessageRevoked', onMessageRevoked)
  sock.on('revokeSingleMessageFailed', () => ElMessage.error('撤回失败'))
  sock.on('isReadMsg', onRead)
  sock.on('receiveValidateMessage', onValidateMessage)
  sock.on('receiveAgreeFriendValidate', onAgreeFriend)
  sock.on('receiveAgreeGroupValidate', onAgreeGroup)
  sock.on('receiveDelGoodFriend', onDelFriend)
  sock.on('receiveQuitGroup', onQuitGroup)

  const payload = session.userInfo ? { ...session.userInfo, uid: session.uid || normalizeUid(session.userInfo) } : {}

  const go = () => {
    sock.emit('goOnline', payload)
    if (session.uid) {
      sock.emit('join', { roomId: session.uid })
    }
  }

  sock.off('connect', go)
  sock.on('connect', go)
  if (sock.connected) go()
}

onMounted(() => {
  bindSocket()
  loadFriendList()
  loadGroupList()
  loadValidateList()
})

onBeforeUnmount(() => {
  teardownSocket()
})
</script>

<style scoped>
.chat-root {
  height: 100vh;
  background: #0b1220;
  color: #e8eef8;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(15, 23, 42, 0.95);
}
.brand {
  font-weight: 600;
  letter-spacing: 0.05em;
}
.me {
  display: flex;
  align-items: center;
  gap: 12px;
}
.body {
  flex: 1;
  min-height: 0;
}
.aside {
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(15, 23, 42, 0.6);
  display: flex;
  flex-direction: column;
}
.aside-head {
  padding: 12px 16px;
  font-size: 13px;
  opacity: 0.85;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.aside-head-title {
  font-weight: 600;
}
.aside-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.aside-tabs {
  display: flex;
  gap: 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  padding: 8px 16px;
  background: rgba(15, 23, 42, 0.35);
}
.tab-btn {
  flex: 1;
  color: #cbd5e1;
  justify-content: center;
}
.tab-btn.active {
  color: #38bdf8;
  font-weight: 600;
}
.validate-badge {
  margin-left: 4px;
}
.search-box {
  display: flex;
  gap: 8px;
  padding: 10px 0;
}
.search-list,
.validate-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.search-item,
.validate-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.04);
}
.validate-item {
  flex-direction: column;
  align-items: flex-start;
}
.validate-meta {
  width: 100%;
}
.validate-type {
  margin-left: 8px;
  color: #38bdf8;
  font-size: 12px;
}
.validate-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 10px;
}
.status-label {
  color: #94a3b8;
  font-size: 12px;
}
.user-info {
  min-width: 0;
  flex: 1;
}
.friend-scroll {
  flex: 1;
  padding: 8px;
}
.friend-item {
  display: flex;
  gap: 10px;
  padding: 10px;
  border-radius: 10px;
  cursor: pointer;
  margin-bottom: 4px;
}
.friend-item:hover {
  background: rgba(255, 255, 255, 0.06);
}
.friend-item.active {
  background: rgba(56, 189, 248, 0.15);
}
.friend-meta {
  flex: 1;
  min-width: 0;
}
.name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.nick {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  flex-shrink: 0;
}
.sig {
  font-size: 12px;
  opacity: 0.55;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.main {
  display: flex;
  flex-direction: column;
  padding: 0;
  background: radial-gradient(1200px 600px at 20% 0%, rgba(56, 189, 248, 0.08), transparent),
    #0b1220;
}
.chat-head {
  padding: 12px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.history-tools {
  padding: 10px 20px;
  display: flex;
  gap: 8px;
  align-items: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(15, 23, 42, 0.35);
}
.history-tools .el-input {
  max-width: 320px;
}
.sub {
  font-size: 11px;
  opacity: 0.45;
  word-break: break-all;
}
.msg-scroll {
  flex: 1;
  padding: 16px 20px;
}
.msg-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.bubble-row {
  display: flex;
  justify-content: flex-start;
}
.bubble-row.mine {
  justify-content: flex-end;
}
.bubble {
  max-width: min(560px, 80%);
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.bubble-row.mine .bubble {
  background: rgba(56, 189, 248, 0.2);
  border-color: rgba(56, 189, 248, 0.35);
}
.meta {
  font-size: 11px;
  opacity: 0.55;
  margin-bottom: 4px;
}
.text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.45;
}
.text.revoked {
  color: #94a3b8;
  font-style: italic;
}
.msg-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 6px;
}
.composer {
  padding: 12px 20px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  gap: 12px;
  align-items: flex-end;
  background: rgba(15, 23, 42, 0.5);
}
.composer .el-button {
  flex-shrink: 0;
}
</style>
