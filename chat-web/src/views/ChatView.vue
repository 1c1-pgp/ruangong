<template>
  <div class="im-app">
    <!-- 顶栏 -->
    <header class="im-header">
      <div class="im-header-left">
        <span class="im-logo">💬</span>
        <span class="im-title">即时通讯</span>
        <span class="online-badge">{{ onlineSet.size }} 人在线</span>
      </div>
      <div class="im-header-right">
        <div class="user-chip">
          <el-avatar :size="32" :src="avatarUrl(session.userInfo?.photo)" />
          <span>{{ session.userInfo?.nickname || session.userInfo?.username }}</span>
        </div>
        <el-button class="logout-btn" round @click="logout">退出</el-button>
      </div>
    </header>

    <div class="im-body">
      <!-- 左侧栏 -->
      <aside class="im-sidebar">
        <div class="sidebar-tabs">
          <button
            v-for="tab in sidebarTabs"
            :key="tab.key"
            class="sidebar-tab"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key as typeof activeTab"
          >
            <component :is="tab.icon" class="tab-icon" />
            <span>{{ tab.label }}</span>
            <span v-if="tab.key === 'validate' && pendingCount" class="tab-badge">{{ pendingCount }}</span>
          </button>
        </div>

        <div class="sidebar-toolbar">
          <el-input
            v-if="activeTab === 'friends'"
            v-model="friendFilter"
            clearable
            placeholder="筛选好友..."
            size="small"
          />
          <template v-else-if="activeTab === 'groups'">
            <el-input
              v-model="groupSearchQuery"
              clearable
              placeholder="搜索群号/群名"
              size="small"
              @keyup.enter="searchGroupByCode"
            />
            <el-button size="small" type="primary" :loading="groupSearchLoading" @click="searchGroupByCode">搜</el-button>
            <el-button size="small" @click="openCreateGroupDialog">建群</el-button>
          </template>
          <template v-else-if="activeTab === 'search'">
            <el-input v-model="searchQuery" clearable placeholder="用户名/昵称" size="small" @keyup.enter="doSearch" />
            <el-button size="small" type="primary" :loading="searchLoading" @click="doSearch">搜</el-button>
          </template>
          <template v-else>
            <span class="toolbar-hint">好友与群聊验证请求</span>
          </template>
          <el-button v-if="activeTab === 'friends'" size="small" text @click="openAddDialog">+ 加好友</el-button>
        </div>

        <el-scrollbar class="sidebar-list">
          <!-- 好友 -->
          <template v-if="activeTab === 'friends'">
            <div
              v-for="f in filteredFriends"
              :key="f.id"
              class="list-item"
              :class="{ active: activeConversation?.roomId === f.roomId }"
              @click="selectFriend(f)"
            >
              <div class="avatar-wrap" :class="{ online: onlineSet.has(f.id) }">
                <el-avatar :size="44" :src="avatarUrl(f.photo)" />
              </div>
              <div class="item-body">
                <div class="item-top">
                  <span class="item-name">{{ f.nickname || f.id }}</span>
                  <span v-if="onlineSet.has(f.id)" class="online-dot">在线</span>
                </div>
                <div class="item-sub">{{ f.signature || '暂无签名' }}</div>
              </div>
            </div>
            <div v-if="friendsLoading" class="list-loading">加载中...</div>
            <el-empty v-else-if="!filteredFriends.length" description="暂无好友" :image-size="64" />
          </template>

          <!-- 群聊 -->
          <template v-else-if="activeTab === 'groups'">
            <div v-if="groupSearchResults.length" class="section-label">搜索结果</div>
            <div v-for="g in groupSearchResults" :key="'s-' + g.gid" class="list-item search-result">
              <el-avatar :size="44" :src="avatarUrl(g.img)">{{ (g.title || '群')[0] }}</el-avatar>
              <div class="item-body">
                <div class="item-name">{{ g.title || g.code }}</div>
                <div class="item-sub">群号 {{ g.code }} · {{ g.userNum ?? 0 }} 人</div>
              </div>
              <el-button
                size="small"
                round
                type="primary"
                :disabled="isAlreadyInGroup(g.gid) || applyingGroupSet.has(g.gid || '')"
                @click="applyJoinGroup(g)"
              >
                {{ isAlreadyInGroup(g.gid) ? '已加入' : '申请' }}
              </el-button>
            </div>
            <div v-if="groups.length" class="section-label">我的群聊</div>
            <div
              v-for="g in groups"
              :key="g.groupId || g.id"
              class="list-item"
              :class="{ active: activeConversation?.roomId === getGroupId(g) }"
              @click="selectGroup(g)"
            >
              <el-avatar :size="44" :src="avatarUrl(g.groupInfo?.img)">群</el-avatar>
              <div class="item-body">
                <div class="item-name">{{ g.groupInfo?.title || g.groupInfo?.code || '群聊' }}</div>
                <div class="item-sub">群号 {{ g.groupInfo?.code || getGroupId(g) }}</div>
              </div>
            </div>
            <el-empty v-if="!groups.length && !groupSearchResults.length" description="搜索群号加入或创建群聊" :image-size="64" />
          </template>

          <!-- 搜索用户 -->
          <template v-else-if="activeTab === 'search'">
            <div v-for="user in searchResults" :key="getUserId(user)" class="list-item search-result">
              <div class="avatar-wrap" :class="{ online: onlineSet.has(getUserId(user)) }">
                <el-avatar :size="44" :src="avatarUrl((user as any).photo)" />
              </div>
              <div class="item-body">
                <div class="item-top">
                  <span class="item-name">{{ (user as any).nickname || (user as any).username }}</span>
                  <span v-if="onlineSet.has(getUserId(user))" class="online-dot">在线</span>
                </div>
                <div class="item-sub">{{ (user as any).signature || '暂无签名' }}</div>
              </div>
              <el-button
                size="small"
                round
                type="primary"
                :disabled="isFriend(getUserId(user)) || sendingRequestSet.has(getUserId(user)) || getUserId(user) === session.uid"
                @click="sendFriendRequest(user)"
              >
                {{ getUserId(user) === session.uid ? '自己' : isFriend(getUserId(user)) ? '已是好友' : '加好友' }}
              </el-button>
            </div>
            <el-empty v-if="!searchLoading && !searchResults.length" description="输入关键词搜索用户" :image-size="64" />
          </template>

          <!-- 验证 -->
          <template v-else>
            <div v-for="item in validateList" :key="item.id" class="validate-card">
              <div class="validate-head">
                <el-avatar :size="36" :src="avatarUrl(item.senderAvatar)">{{ (item.senderNickname || '?')[0] }}</el-avatar>
                <div>
                  <div class="item-name">
                    {{ item.senderNickname || item.senderName }}
                    <el-tag size="small" :type="item.validateType === 1 ? 'warning' : 'primary'" effect="plain">
                      {{ item.validateType === 1 ? '群聊' : '好友' }}
                    </el-tag>
                  </div>
                  <div class="item-sub">{{ item.additionMessage || '请求添加' }}</div>
                  <div v-if="item.validateType === 1" class="item-sub">群：{{ item.groupInfo?.title || item.groupInfo?.code || '未知' }}</div>
                </div>
              </div>
              <div v-if="(item.status ?? 0) === 0" class="validate-btns">
                <el-button size="small" type="success" round @click="agreeRequest(item)">同意</el-button>
                <el-button size="small" round @click="rejectRequest(item)">拒绝</el-button>
              </div>
              <el-tag v-else size="small" :type="item.status === 1 ? 'success' : 'info'">
                {{ item.status === 1 ? '已同意' : '已拒绝' }}
              </el-tag>
            </div>
            <el-empty v-if="!validateLoading && !validateList.length" description="暂无验证消息" :image-size="64" />
          </template>
        </el-scrollbar>
      </aside>

      <!-- 聊天主区域 -->
      <main class="im-main">
        <template v-if="activeConversation">
          <!-- 聊天头 -->
          <div class="chat-header">
            <div class="chat-header-info">
              <h2>{{ activeConversation.title }}</h2>
              <p>
                <template v-if="activeConversation.conversationType === 'FRIEND'">
                  <span v-if="activeConversation.peerOnline" class="online-dot">● 在线</span>
                  <span v-else class="offline-text">离线</span>
                  · {{ activeConversation.subtitle }}
                </template>
                <template v-else>{{ activeConversation.subtitle }}</template>
              </p>
            </div>
            <div class="chat-header-actions">
              <el-button v-if="activeConversation.conversationType === 'GROUP'" size="small" round @click="openGroupDrawer">成员</el-button>
              <el-button v-if="activeConversation.conversationType === 'GROUP'" size="small" round @click="openInviteDialog">邀请</el-button>
              <el-button
                v-if="activeConversation.conversationType === 'GROUP' && activeConversation.isHolder"
                size="small"
                round
                type="danger"
                plain
                @click="confirmDissolveGroup"
              >解散</el-button>
              <el-button
                v-else-if="activeConversation.conversationType === 'GROUP'"
                size="small"
                round
                type="warning"
                plain
                @click="confirmQuitGroup"
              >退出</el-button>
            </div>
          </div>

          <!-- 历史搜索 -->
          <div class="history-bar">
            <el-select v-model="historyType" size="small" style="width: 96px">
              <el-option label="全部" value="all" />
              <el-option label="文字" value="text" />
              <el-option label="图片" value="img" />
              <el-option label="文件" value="file" />
            </el-select>
            <el-input v-model="historyQuery" size="small" clearable placeholder="搜索历史消息..." @keyup.enter="searchHistory" />
            <el-button size="small" :loading="historyLoading" @click="searchHistory">搜索</el-button>
            <el-button size="small" text @click="reloadCurrentConversation">最近</el-button>
          </div>

          <!-- 消息区 -->
          <el-scrollbar ref="scrollRef" class="chat-messages">
            <div class="messages-inner">
              <div v-if="hasMoreMessages" class="load-more">
                <el-button size="small" :loading="loadMoreLoading" @click="loadMoreMessages">加载更早消息</el-button>
              </div>
              <div
                v-for="(m, idx) in messages"
                :key="m.id || idx"
                class="msg-row"
                :class="{
                  mine: m.senderId === session.uid && m.messageType !== 'sys',
                  sys: m.messageType === 'sys',
                }"
              >
                <template v-if="m.messageType === 'sys'">
                  <div class="sys-msg">{{ m.message }}</div>
                </template>
                <template v-else>
                  <el-avatar
                    v-if="m.senderId !== session.uid"
                    :size="36"
                    :src="avatarUrl(m.senderAvatar)"
                    class="msg-avatar"
                  >{{ (m.senderNickname || m.senderName || '?')[0] }}</el-avatar>
                  <div class="msg-content">
                    <div v-if="m.senderId !== session.uid" class="msg-sender">{{ m.senderNickname || m.senderName }}</div>
                    <div class="msg-bubble" :class="{ revoked: m.revoked || m.messageType === 'revoked' }">
                      <template v-if="m.revoked || m.messageType === 'revoked'">
                        <span class="revoked-text">消息已撤回</span>
                      </template>
                      <template v-else-if="m.messageType === 'img'">
                        <el-image :src="mediaUrl(m.message)" :preview-src-list="[mediaUrl(m.message)]" fit="cover" class="msg-img" />
                      </template>
                      <template v-else-if="m.messageType === 'file'">
                        <a class="msg-file" :href="mediaUrl(m.message)" target="_blank" rel="noopener">
                          <span class="file-icon">📎</span>
                          <span>{{ m.fileRawName || '下载文件' }}</span>
                        </a>
                      </template>
                      <template v-else>{{ m.message }}</template>
                    </div>
                    <div class="msg-footer">
                      <span class="msg-time">{{ formatSmartTime(m.time) }}</span>
                      <span
                        v-if="m.senderId === session.uid && activeConversation.conversationType === 'FRIEND' && !m.revoked && m.messageType !== 'revoked'"
                        class="read-tag"
                        :class="{ read: readReceiptLabel(m) === '已读' }"
                      >{{ readReceiptLabel(m) }}</span>
                      <template v-if="m.id && activeConversation.conversationType === 'FRIEND'">
                        <el-button link size="small" @click="deleteMessage(m)">删</el-button>
                        <el-button v-if="canRevoke(m)" link size="small" type="warning" @click="revokeMessage(m)">撤回</el-button>
                      </template>
                    </div>
                  </div>
                  <el-avatar
                    v-if="m.senderId === session.uid"
                    :size="36"
                    :src="avatarUrl(session.userInfo?.photo)"
                    class="msg-avatar"
                  />
                </template>
              </div>
            </div>
          </el-scrollbar>

          <!-- 输入区 -->
          <div class="chat-composer">
            <input ref="fileInputRef" type="file" hidden @change="onFileSelected" />
            <div class="composer-toolbar">
              <el-tooltip content="发送图片或文件">
                <el-button circle :loading="uploadLoading" @click="triggerFilePick">📎</el-button>
              </el-tooltip>
            </div>
            <el-input
              v-model="draft"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 5 }"
              placeholder="输入消息，Enter 发送，Shift+Enter 换行"
              @keydown="onDraftKeydown"
            />
            <el-button type="primary" round :disabled="!draft.trim()" class="send-btn" @click="send">
              发送
            </el-button>
          </div>
        </template>

        <!-- 空状态 -->
        <div v-else class="welcome">
          <div class="welcome-icon">💬</div>
          <h2>欢迎使用即时通讯</h2>
          <p>从左侧选择好友或群聊开始对话</p>
          <div class="welcome-stats">
            <div class="stat-card"><strong>{{ friends.length }}</strong><span>好友</span></div>
            <div class="stat-card"><strong>{{ groups.length }}</strong><span>群聊</span></div>
            <div class="stat-card"><strong>{{ pendingCount }}</strong><span>待验证</span></div>
          </div>
        </div>
      </main>
    </div>

    <!-- 对话框 -->
    <el-dialog v-model="showAddDialog" title="添加好友" width="400px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="用户名"><el-input v-model="addUsername" /></el-form-item>
        <el-form-item label="昵称（二次校验）"><el-input v-model="addNickname" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="addLoading" @click="submitAddFriend">添加</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCreateGroupDialog" title="创建群聊" width="420px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="群名称"><el-input v-model="createGroupTitle" maxlength="30" show-word-limit /></el-form-item>
        <el-form-item label="群简介"><el-input v-model="createGroupDesc" type="textarea" :rows="3" maxlength="120" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateGroupDialog = false">取消</el-button>
        <el-button type="primary" :loading="createGroupLoading" @click="submitCreateGroup">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showInviteDialog" title="邀请好友入群" width="440px" destroy-on-close>
      <el-checkbox-group v-model="inviteSelectedIds" class="invite-list">
        <el-checkbox v-for="f in friends" :key="f.id" :label="f.id">{{ f.nickname || f.id }}</el-checkbox>
      </el-checkbox-group>
      <el-empty v-if="!friends.length" description="暂无好友" />
      <template #footer>
        <el-button @click="showInviteDialog = false">取消</el-button>
        <el-button type="primary" :loading="inviteLoading" @click="submitInvite">发送邀请</el-button>
      </template>
    </el-dialog>

    <!-- 群成员抽屉 -->
    <el-drawer v-model="showGroupDrawer" title="群成员" size="320px">
      <div v-if="groupMembersLoading" class="drawer-loading">加载中...</div>
      <div v-for="m in groupMembers" :key="m.userId" class="member-item">
        <el-avatar :size="40" :src="avatarUrl(m.userInfo?.photo)">{{ (m.userInfo?.nickname || m.username || '?')[0] }}</el-avatar>
        <div class="item-body">
          <div class="item-name">
            {{ m.userInfo?.nickname || m.username }}
            <el-tag v-if="m.holder === 1" size="small" type="warning">群主</el-tag>
          </div>
          <div class="item-sub">{{ m.username }}</div>
        </div>
        <span v-if="onlineSet.has(m.userId || '')" class="online-dot">在线</span>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, ChatDotRound, Search, Bell } from '@element-plus/icons-vue'
import { useSessionStore, normalizeUid } from '@/stores/session'
import {
  loadFriends, loadRecentMessages, searchSingleHistory, searchGroupHistory,
  deleteSingleMessageForMe, markSingleMessagesRead, uploadChatFile,
  loadGroups, createGroup, inviteToGroup, quitGroup, dissolveGroup,
  loadRecentGroupMessages, loadValidateMessages, searchUsers, searchGroups,
  getGroupInfo, addFriendByUsername,
  type FriendItem, type ChatMessage, type GroupItem, type GroupSearchItem, type ValidateMessageItem,
} from '@/api/chat'
import { ensureSocket, teardownSocket } from '@/socket'

const router = useRouter()
const session = useSessionStore()

const sidebarTabs = [
  { key: 'friends', label: '好友', icon: markRaw(User) },
  { key: 'groups', label: '群聊', icon: markRaw(ChatDotRound) },
  { key: 'search', label: '搜索', icon: markRaw(Search) },
  { key: 'validate', label: '验证', icon: markRaw(Bell) },
]

const friends = ref<FriendItem[]>([])
const friendsLoading = ref(false)
const friendFilter = ref('')
const groups = ref<GroupItem[]>([])
const validateList = ref<ValidateMessageItem[]>([])
const validateLoading = ref(false)
const searchQuery = ref('')
const searchResults = ref<unknown[]>([])
const searchLoading = ref(false)
const sendingRequestSet = ref(new Set<string>())
const showAddDialog = ref(false)
const addUsername = ref('')
const addNickname = ref('')
const addLoading = ref(false)
const showCreateGroupDialog = ref(false)
const createGroupTitle = ref('')
const createGroupDesc = ref('')
const createGroupLoading = ref(false)
const showInviteDialog = ref(false)
const inviteSelectedIds = ref<string[]>([])
const inviteLoading = ref(false)
const activeTab = ref<'friends' | 'groups' | 'search' | 'validate'>('friends')
const activeConversation = ref<{
  roomId: string; title: string; subtitle?: string
  conversationType: 'FRIEND' | 'GROUP'; isHolder?: boolean; peerOnline?: boolean
} | null>(null)
const messages = ref<ChatMessage[]>([])
const draft = ref('')
const onlineSet = ref(new Set<string>())
const scrollRef = ref<{ setScrollTop?: (v: number) => void }>()
const historyQuery = ref('')
const historyType = ref('all')
const historyLoading = ref(false)
const uploadLoading = ref(false)
const fileInputRef = ref<HTMLInputElement>()
const groupSearchQuery = ref('')
const groupSearchResults = ref<GroupSearchItem[]>([])
const groupSearchLoading = ref(false)
const applyingGroupSet = ref(new Set<string>())
const messagePageIndex = ref(0)
const hasMoreMessages = ref(false)
const loadMoreLoading = ref(false)
const showGroupDrawer = ref(false)
const groupMembers = ref<Array<{ userId?: string; username?: string; holder?: number; userInfo?: { nickname?: string; photo?: string } }>>([])
const groupMembersLoading = ref(false)

const pendingCount = computed(() => validateList.value.filter((i) => i.status === 0).length)
const filteredFriends = computed(() => {
  const q = friendFilter.value.trim().toLowerCase()
  if (!q) return friends.value
  return friends.value.filter((f) =>
    (f.nickname || '').toLowerCase().includes(q) ||
    (f.signature || '').toLowerCase().includes(q) ||
    f.id.includes(q)
  )
})

function avatarUrl(photo?: string) {
  if (!photo) return undefined
  if (photo.startsWith('http')) return photo
  return `/chat${photo.startsWith('/') ? '' : '/'}${photo}`
}

function formatSmartTime(t: ChatMessage['time']) {
  if (!t) return ''
  const d = typeof t === 'string' || typeof t === 'number' ? new Date(t) : t
  const now = new Date()
  const isToday = d.toDateString() === now.toDateString()
  if (isToday) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  return d.toLocaleString([], { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function getUserId(user: any): string {
  if (!user) return ''
  if (typeof user.id === 'string') return user.id
  if (typeof user.uid === 'string') return user.uid
  if (typeof user.userId === 'string') return user.userId
  if (user.userId?.$oid) return user.userId.$oid
  return ''
}

function isFriend(userId: string) {
  return friends.value.some((f) => f.id === userId)
}

function mergeSearchResults(...lists: unknown[][]) {
  const merged: unknown[] = []
  const seen = new Set<string>()
  for (const list of lists) {
    for (const user of list) {
      const id = getUserId(user)
      if (seen.has(id || JSON.stringify(user))) continue
      seen.add(id || JSON.stringify(user))
      merged.push(user)
    }
  }
  return merged
}

function getGroupId(g: GroupItem) {
  return g.groupId || g.id || g.groupInfo?.gid || ''
}

function mediaUrl(path?: string) {
  if (!path) return ''
  if (path.startsWith('http')) return path
  return `/chat${path.startsWith('/') ? '' : '/'}${path}`
}

function detectMessageType(name: string): 'img' | 'file' {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(ext) ? 'img' : 'file'
}

function getPeerId(roomId: string) {
  const uid = session.uid
  if (!uid) return ''
  const p = roomId.split('-')
  return p.length === 2 ? (p[0] === uid ? p[1] : p[0]) : ''
}

function readReceiptLabel(m: ChatMessage) {
  const conv = activeConversation.value
  if (!conv || conv.conversationType !== 'FRIEND') return ''
  const peer = getPeerId(conv.roomId)
  return peer && (m.isReadUser ?? []).includes(peer) ? '已读' : '已发送'
}

function isAlreadyInGroup(gid?: string) {
  return gid ? groups.value.some((g) => getGroupId(g) === gid) : false
}

function cacheKey(roomId: string) { return `single-history:${session.uid}:${roomId}` }
function cacheMessagesIfFriend(roomId: string, list: ChatMessage[]) {
  if (activeConversation.value?.conversationType === 'FRIEND') cacheMessages(roomId, list)
}
function loadCachedMessages(roomId: string): ChatMessage[] {
  try { const r = localStorage.getItem(cacheKey(roomId)); return r ? JSON.parse(r) : [] } catch { return [] }
}
function cacheMessages(roomId: string, list: ChatMessage[]) {
  try { localStorage.setItem(cacheKey(roomId), JSON.stringify(list.slice(-100))) } catch { /* */ }
}

async function loadMessages(conv: NonNullable<typeof activeConversation.value>, pageIndex = 0, append = false) {
  const pageSize = 50
  const list = conv.conversationType === 'FRIEND'
    ? await loadRecentMessages(conv.roomId, pageIndex, pageSize, session.uid)
    : await loadRecentGroupMessages(conv.roomId, pageIndex, pageSize)
  hasMoreMessages.value = list.length >= pageSize
  messagePageIndex.value = pageIndex
  const ordered = [...list].reverse()
  messages.value = append ? [...ordered, ...messages.value] : ordered
}

async function selectFriend(f: FriendItem) {
  if (!f.roomId) return ElMessage.warning('无法打开该会话')
  activeConversation.value = {
    roomId: f.roomId, title: f.nickname || f.id || '好友',
    subtitle: f.signature || '', conversationType: 'FRIEND', peerOnline: onlineSet.value.has(f.id),
  }
  joinRoom(f.roomId)
  messages.value = loadCachedMessages(f.roomId)
  try {
    await loadMessages(activeConversation.value)
    cacheMessages(f.roomId, messages.value)
    await markCurrentRead(f.roomId)
    await nextTick(); scrollBottom()
  } catch { ElMessage.error('加载消息失败'); messages.value = [] }
}

async function selectGroup(g: GroupItem) {
  const roomId = getGroupId(g)
  if (!roomId) return ElMessage.warning('无法打开该群聊')
  activeConversation.value = {
    roomId, title: g.groupInfo?.title || g.groupInfo?.code || '群聊',
    subtitle: `群号 ${g.groupInfo?.code || roomId}`, conversationType: 'GROUP', isHolder: g.holder === 1,
  }
  joinRoom(roomId)
  try {
    await loadMessages(activeConversation.value)
    await nextTick(); scrollBottom()
  } catch { ElMessage.error('加载群消息失败'); messages.value = [] }
}

function joinRoom(roomId: string) { ensureSocket().emit('join', { roomId }) }

async function loadMoreMessages() {
  const conv = activeConversation.value
  if (!conv || loadMoreLoading.value) return
  loadMoreLoading.value = true
  try {
    await loadMessages(conv, messagePageIndex.value + 1, true)
  } catch { ElMessage.error('加载失败') }
  finally { loadMoreLoading.value = false }
}

async function openGroupDrawer() {
  const conv = activeConversation.value
  if (!conv || conv.conversationType !== 'GROUP') return
  showGroupDrawer.value = true
  groupMembersLoading.value = true
  try {
    const { members } = await getGroupInfo(conv.roomId)
    groupMembers.value = members
  } catch { ElMessage.error('加载群成员失败') }
  finally { groupMembersLoading.value = false }
}

async function sendMessagePayload(payload: { message: string; messageType: string; fileRawName?: string }) {
  const conv = activeConversation.value
  const uid = session.uid
  if (!conv?.roomId || !uid || !session.userInfo) return
  const msg = {
    roomId: conv.roomId, senderId: uid,
    senderName: String(session.userInfo.username || ''),
    senderNickname: String(session.userInfo.nickname || session.userInfo.username || ''),
    senderAvatar: String(session.userInfo.photo || ''),
    time: new Date(), fileRawName: payload.fileRawName || '',
    message: payload.message, messageType: payload.messageType,
    isReadUser: [uid], conversationType: conv.conversationType,
  }
  ensureSocket().emit('sendNewMessage', msg)
  messages.value.push({ ...msg, time: msg.time.toISOString() })
  cacheMessagesIfFriend(conv.roomId, messages.value)
  nextTick(() => scrollBottom())
}

async function send() {
  const text = draft.value.trim()
  if (!text) return
  draft.value = ''
  await sendMessagePayload({ message: text, messageType: 'text' })
}

function onDraftKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() }
}

function triggerFilePick() { fileInputRef.value?.click() }

async function onFileSelected(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  ;(e.target as HTMLInputElement).value = ''
  if (!file || !activeConversation.value) return
  uploadLoading.value = true
  try {
    const { filePath, fileRawName } = await uploadChatFile(file)
    if (!filePath) throw new Error('上传失败')
    await sendMessagePayload({ message: filePath, messageType: detectMessageType(fileRawName), fileRawName })
    ElMessage.success('已发送')
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || err?.message || '上传失败')
  } finally { uploadLoading.value = false }
}

async function reloadCurrentConversation() {
  const conv = activeConversation.value
  if (!conv) return
  historyQuery.value = ''; historyType.value = 'all'
  try {
    await loadMessages(conv)
    if (conv.conversationType === 'FRIEND') await markCurrentRead(conv.roomId)
    await nextTick(); scrollBottom()
  } catch { ElMessage.error('加载失败') }
}

async function searchHistory() {
  const conv = activeConversation.value
  if (!conv) return
  historyLoading.value = true
  try {
    const params = { roomId: conv.roomId, type: historyType.value, query: historyQuery.value.trim(), pageIndex: 0, pageSize: 80 }
    const { list, total } = conv.conversationType === 'FRIEND'
      ? await searchSingleHistory({ ...params, userId: session.uid })
      : await searchGroupHistory(params)
    messages.value = [...list].reverse()
    hasMoreMessages.value = false
    if (conv.conversationType === 'FRIEND') await markCurrentRead(conv.roomId)
    if (!total) ElMessage.info('没有匹配消息')
    await nextTick(); scrollBottom()
  } catch { ElMessage.error('搜索失败') }
  finally { historyLoading.value = false }
}

function canRevoke(m: ChatMessage) {
  return Boolean(m.id && m.senderId === session.uid && !m.revoked && m.messageType !== 'revoked')
}

async function deleteMessage(m: ChatMessage) {
  const conv = activeConversation.value
  if (!m.id || !conv?.roomId || !session.uid) return
  try {
    await deleteSingleMessageForMe(m.id, conv.roomId, session.uid)
    messages.value = messages.value.filter((i) => i.id !== m.id)
    cacheMessages(conv.roomId, messages.value)
    ElMessage.success('已删除')
  } catch { ElMessage.error('删除失败') }
}

async function revokeMessage(m: ChatMessage) {
  const conv = activeConversation.value
  if (!m.id || !conv?.roomId || !session.uid) return
  ensureSocket().emit('revokeSingleMessage', { messageId: m.id, roomId: conv.roomId, userId: session.uid })
}

function applySavedMessage(saved: ChatMessage) {
  if (!saved.roomId || saved.roomId !== activeConversation.value?.roomId) return
  const idx = messages.value.findIndex((i) => !i.id && i.senderId === saved.senderId && i.messageType === saved.messageType
    && (i.message === saved.message || (i.fileRawName && i.fileRawName === saved.fileRawName)))
  if (idx >= 0) { messages.value.splice(idx, 1, saved); cacheMessagesIfFriend(saved.roomId, messages.value) }
}

function applyRevokedMessage(revoked: ChatMessage) {
  const idx = messages.value.findIndex((i) => i.id === revoked.id)
  if (idx >= 0) {
    messages.value.splice(idx, 1, { ...messages.value[idx], ...revoked, revoked: true, messageType: 'revoked' })
    if (revoked.roomId) cacheMessagesIfFriend(revoked.roomId, messages.value)
  }
}

async function markCurrentRead(roomId: string) {
  if (!session.uid || activeConversation.value?.conversationType !== 'FRIEND') return
  try {
    await markSingleMessagesRead(roomId, session.uid)
    ensureSocket().emit('isReadMsg', { roomId, userId: session.uid })
  } catch { /* */ }
}

function scrollBottom() { scrollRef.value?.setScrollTop?.(1e9) }

function logout() { teardownSocket(); session.clear(); router.push('/login') }

async function searchGroupByCode() {
  const kw = groupSearchQuery.value.trim()
  if (!kw) return ElMessage.warning('请输入群号或群名')
  groupSearchLoading.value = true
  try {
    const [a, b] = await Promise.all([searchGroups(kw, 'code', 0, 10), searchGroups(kw, 'title', 0, 10)])
    const seen = new Set<string>()
    groupSearchResults.value = [...a, ...b].filter((g) => { if (!g.gid || seen.has(g.gid)) return false; seen.add(g.gid); return true })
    if (!groupSearchResults.value.length) ElMessage.info('未找到群聊')
  } catch { ElMessage.error('搜索失败') }
  finally { groupSearchLoading.value = false }
}

async function applyJoinGroup(g: GroupSearchItem) {
  const { gid, holderUserInfo } = g
  const uid = session.uid
  if (!gid || !holderUserInfo?.uid || !uid) return ElMessage.warning('群信息不完整')
  if (isAlreadyInGroup(gid)) return ElMessage.info('已在群内')
  applyingGroupSet.value.add(gid)
  try {
    ensureSocket().emit('sendValidateMessage', {
      roomId: holderUserInfo.uid, senderId: uid,
      senderName: String(session.userInfo?.username || ''),
      senderNickname: String(session.userInfo?.nickname || session.userInfo?.username || ''),
      senderAvatar: String(session.userInfo?.photo || ''),
      receiverId: holderUserInfo.uid,
      additionMessage: `申请加入群聊「${g.title || g.code}」`,
      time: new Date().toISOString(), status: 0, validateType: 1, groupId: gid,
    })
    ElMessage.success('申请已发送')
  } catch { ElMessage.error('发送失败') }
  finally { applyingGroupSet.value.delete(gid) }
}

async function doSearch() {
  const kw = searchQuery.value.trim()
  if (!kw) return ElMessage.warning('请输入关键词')
  searchLoading.value = true
  try {
    searchResults.value = mergeSearchResults(
      await searchUsers(kw, 'username', 0, 20),
      await searchUsers(kw, 'nickname', 0, 20),
    )
    if (!searchResults.value.length) ElMessage.info('未找到用户')
  } catch { ElMessage.error('搜索失败') }
  finally { searchLoading.value = false }
}

async function sendFriendRequest(user: unknown) {
  const id = getUserId(user)
  if (!id || id === session.uid) return
  sendingRequestSet.value.add(id)
  try {
    ensureSocket().emit('sendValidateMessage', {
      roomId: id, senderId: session.uid,
      senderName: String(session.userInfo?.username || ''),
      senderNickname: String(session.userInfo?.nickname || session.userInfo?.username || ''),
      senderAvatar: String(session.userInfo?.photo || ''),
      receiverId: id, additionMessage: '我想加你为好友',
      time: new Date().toISOString(), status: 0, validateType: 0,
    })
    ElMessage.success('申请已发送')
  } catch { ElMessage.error('发送失败') }
  finally { sendingRequestSet.value.delete(id) }
}

function openAddDialog() { addUsername.value = ''; addNickname.value = ''; showAddDialog.value = true }

async function submitAddFriend() {
  if (!addUsername.value.trim()) return ElMessage.warning('请输入用户名')
  if (!addNickname.value.trim()) return ElMessage.warning('请输入昵称')
  addLoading.value = true
  try {
    await addFriendByUsername(addUsername.value.trim(), addNickname.value.trim())
    ElMessage.success('添加成功'); showAddDialog.value = false; await loadFriendList()
  } catch (err: any) { ElMessage.error(err?.response?.data?.message || '添加失败') }
  finally { addLoading.value = false }
}

async function agreeRequest(item: ValidateMessageItem) {
  if (!item.id) return
  const sock = ensureSocket()
  if (item.validateType === 1) sock.emit('sendAgreeGroupValidate', item)
  else sock.emit('sendAgreeFriendValidate', item)
  ElMessage.success('已同意'); await reloadValidateAndLists()
}

async function rejectRequest(item: ValidateMessageItem) {
  if (!item.id) return
  const sock = ensureSocket()
  if (item.validateType === 1) sock.emit('sendDisAgreeGroupValidate', item)
  else sock.emit('sendDisAgreeFriendValidate', item)
  ElMessage.success('已拒绝'); await reloadValidateAndLists()
}

async function loadFriendList() {
  const uid = session.uid
  if (!uid) { router.push('/login'); return }
  friendsLoading.value = true
  try { friends.value = await loadFriends(uid) }
  catch { ElMessage.error('加载好友失败') }
  finally { friendsLoading.value = false }
}

async function loadGroupList() {
  const name = session.userInfo?.username
  if (!name) return
  try { groups.value = await loadGroups(name) } catch { ElMessage.error('加载群聊失败') }
}

function openCreateGroupDialog() { createGroupTitle.value = ''; createGroupDesc.value = ''; showCreateGroupDialog.value = true }

async function submitCreateGroup() {
  const title = createGroupTitle.value.trim()
  const uid = session.uid, username = session.userInfo?.username
  if (!title || !uid || !username) return ElMessage.warning('请填写群名称')
  createGroupLoading.value = true
  try {
    const { groupId, groupCode } = await createGroup({ title, desc: createGroupDesc.value.trim(), holderName: username, holderUserId: uid })
    ElMessage.success(`创建成功，群号 ${groupCode}`)
    showCreateGroupDialog.value = false; await loadGroupList()
    activeTab.value = 'groups'
    await selectGroup({ groupId, holder: 1, groupInfo: { gid: groupId, title, code: groupCode } })
  } catch (err: any) { ElMessage.error(err?.response?.data?.message || '创建失败') }
  finally { createGroupLoading.value = false }
}

function openInviteDialog() {
  if (activeConversation.value?.conversationType !== 'GROUP') return ElMessage.warning('请先选择群聊')
  inviteSelectedIds.value = []; showInviteDialog.value = true
}

async function submitInvite() {
  const conv = activeConversation.value, uid = session.uid
  if (!conv?.roomId || !uid || !inviteSelectedIds.value.length) return ElMessage.warning('请选择好友')
  inviteLoading.value = true
  try {
    const r = await inviteToGroup({ groupId: conv.roomId, invitedUserIds: inviteSelectedIds.value, inviterUserId: uid })
    ElMessage.success(r.invitedCount ? `已邀请 ${r.invitedCount} 人` : '没有成功邀请')
    showInviteDialog.value = false
  } catch (err: any) { ElMessage.error(err?.response?.data?.message || '邀请失败') }
  finally { inviteLoading.value = false }
}

function notifyQuitGroup(roomId: string) { ensureSocket().emit('sendQuitGroup', { roomId }) }
function clearGroupConversation(roomId: string) {
  if (activeConversation.value?.roomId === roomId) { activeConversation.value = null; messages.value = [] }
}

async function confirmQuitGroup() {
  const conv = activeConversation.value, uid = session.uid
  if (!conv?.roomId || !uid) return
  try {
    await ElMessageBox.confirm('确定退出该群聊？', '提示', { type: 'warning' })
    await quitGroup(conv.roomId, uid); notifyQuitGroup(conv.roomId)
    clearGroupConversation(conv.roomId); await loadGroupList(); ElMessage.success('已退出')
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '操作失败') }
}

async function confirmDissolveGroup() {
  const conv = activeConversation.value
  if (!conv?.roomId) return
  try {
    await ElMessageBox.confirm('解散后群聊与消息将被删除，确定继续？', '警告', { type: 'warning' })
    await dissolveGroup(conv.roomId); notifyQuitGroup(conv.roomId)
    clearGroupConversation(conv.roomId); await loadGroupList(); ElMessage.success('已解散')
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '操作失败') }
}

async function loadValidateList() {
  const uid = session.uid; if (!uid) return
  validateLoading.value = true
  try { validateList.value = await loadValidateMessages(uid) }
  catch { ElMessage.error('加载验证消息失败') }
  finally { validateLoading.value = false }
}

async function reloadValidateAndLists() {
  await Promise.all([loadValidateList(), loadFriendList(), loadGroupList()])
}

function bindSocket() {
  const sock = ensureSocket()
  const onOnline = (uids: unknown) => {
    onlineSet.value = new Set((Array.isArray(uids) ? uids : []).map(String))
    const conv = activeConversation.value
    if (conv?.conversationType === 'FRIEND') {
      conv.peerOnline = onlineSet.value.has(getPeerId(conv.roomId))
    }
  }
  const onRecv = (raw: Record<string, unknown>) => {
    const roomId = String(raw.roomId || '')
    if (roomId !== activeConversation.value?.roomId) return
    messages.value.push(raw as unknown as ChatMessage)
    cacheMessagesIfFriend(roomId, messages.value)
    if (activeConversation.value?.conversationType === 'FRIEND') markCurrentRead(roomId)
    nextTick(() => scrollBottom())
  }
  sock.off('onlineUser').off('receiveMessage').off('messageSaved').off('singleMessageRevoked')
    .off('revokeSingleMessageFailed').off('isReadMsg').off('receiveValidateMessage')
    .off('receiveAgreeFriendValidate').off('receiveAgreeGroupValidate').off('receiveDelGoodFriend').off('receiveQuitGroup')
  sock.on('onlineUser', onOnline)
  sock.on('receiveMessage', onRecv)
  sock.on('messageSaved', (r) => applySavedMessage(r as unknown as ChatMessage))
  sock.on('singleMessageRevoked', (r) => applyRevokedMessage(r as unknown as ChatMessage))
  sock.on('revokeSingleMessageFailed', () => ElMessage.error('撤回失败'))
  sock.on('isReadMsg', (raw) => {
    const roomId = String((raw as any).roomId || ''), userId = String((raw as any).userId || '')
    if (roomId !== activeConversation.value?.roomId) return
    messages.value = messages.value.map((m) => {
      const u = m.isReadUser ?? []
      return u.includes(userId) ? m : { ...m, isReadUser: [...u, userId] }
    })
  })
  sock.on('receiveValidateMessage', () => { loadValidateList(); ElMessage.info('收到新的验证消息') })
  sock.on('receiveAgreeFriendValidate', () => { loadFriendList(); ElMessage.success('好友请求已通过') })
  sock.on('receiveAgreeGroupValidate', () => { loadGroupList(); ElMessage.success('群聊申请已通过') })
  sock.on('receiveDelGoodFriend', () => { loadFriendList(); ElMessage.info('好友列表已更新') })
  sock.on('receiveQuitGroup', (raw) => { clearGroupConversation(String((raw as any).roomId || '')); loadGroupList() })
  const payload = session.userInfo ? { ...session.userInfo, uid: session.uid || normalizeUid(session.userInfo) } : {}
  const go = () => { sock.emit('goOnline', payload); if (session.uid) sock.emit('join', { roomId: session.uid }) }
  sock.off('connect', go).on('connect', go)
  if (sock.connected) go()
}

onMounted(() => { bindSocket(); loadFriendList(); loadGroupList(); loadValidateList() })
onBeforeUnmount(teardownSocket)
</script>

<style scoped>
.im-app {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--im-bg);
  color: var(--im-text);
}

/* Header */
.im-header {
  height: 56px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--im-surface);
  border-bottom: 1px solid var(--im-border);
  flex-shrink: 0;
}
.im-header-left { display: flex; align-items: center; gap: 10px; }
.im-logo { font-size: 22px; }
.im-title { font-size: 17px; font-weight: 700; }
.online-badge {
  font-size: 12px; color: var(--im-success); background: rgba(52, 211, 153, 0.12);
  padding: 2px 10px; border-radius: 20px;
}
.im-header-right { display: flex; align-items: center; gap: 12px; }
.user-chip {
  display: flex; align-items: center; gap: 8px;
  padding: 4px 12px 4px 4px; border-radius: 24px;
  background: var(--im-surface-2); font-size: 14px;
}
.logout-btn { --el-button-bg-color: transparent; --el-button-border-color: var(--im-border); color: var(--im-muted); }

/* Body layout */
.im-body { flex: 1; display: flex; min-height: 0; }

/* Sidebar */
.im-sidebar {
  width: 320px; flex-shrink: 0;
  display: flex; flex-direction: column;
  background: var(--im-surface);
  border-right: 1px solid var(--im-border);
}
.sidebar-tabs {
  display: grid; grid-template-columns: repeat(4, 1fr);
  gap: 4px; padding: 10px 10px 0;
}
.sidebar-tab {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: 8px 4px; border: none; border-radius: 10px;
  background: transparent; color: var(--im-muted);
  cursor: pointer; font-size: 11px; position: relative;
  transition: all 0.15s;
}
.sidebar-tab:hover { background: var(--im-surface-2); color: var(--im-text); }
.sidebar-tab.active { background: var(--im-accent-soft); color: var(--im-accent-hover); }
.tab-icon { width: 20px; height: 20px; }
.tab-badge {
  position: absolute; top: 2px; right: 8px;
  background: var(--im-danger); color: #fff;
  font-size: 10px; min-width: 16px; height: 16px;
  border-radius: 8px; display: flex; align-items: center; justify-content: center;
}
.sidebar-toolbar {
  display: flex; flex-wrap: wrap; gap: 6px;
  padding: 10px; align-items: center;
}
.toolbar-hint { font-size: 12px; color: var(--im-muted); flex: 1; }
.sidebar-list { flex: 1; padding: 0 8px 8px; }
.section-label {
  font-size: 11px; color: var(--im-muted); padding: 8px 8px 4px;
  text-transform: uppercase; letter-spacing: 0.05em;
}

/* List items */
.list-item {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 12px; border-radius: 12px;
  cursor: pointer; margin-bottom: 2px;
  transition: background 0.15s;
}
.list-item:hover { background: var(--im-surface-2); }
.list-item.active { background: var(--im-accent-soft); }
.list-item.search-result { cursor: default; }
.item-body { flex: 1; min-width: 0; }
.item-top { display: flex; align-items: center; justify-content: space-between; gap: 6px; }
.item-name { font-weight: 600; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-sub { font-size: 12px; color: var(--im-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 2px; }
.avatar-wrap { flex-shrink: 0; }
.avatar-wrap.online :deep(.el-avatar) {
  box-shadow: 0 0 0 2px var(--im-success), 0 0 8px rgba(52, 211, 153, 0.4);
}
.online-dot { font-size: 11px; color: var(--im-success); flex-shrink: 0; }
.list-loading { text-align: center; padding: 20px; color: var(--im-muted); font-size: 13px; }

/* Validate cards */
.validate-card {
  padding: 12px; border-radius: 12px;
  background: var(--im-surface-2); margin-bottom: 8px;
  border: 1px solid var(--im-border);
}
.validate-head { display: flex; gap: 10px; margin-bottom: 10px; }
.validate-btns { display: flex; gap: 8px; }

/* Main chat area */
.im-main { flex: 1; display: flex; flex-direction: column; min-width: 0; background: var(--im-bg); }

.chat-header {
  padding: 14px 20px; display: flex; align-items: center; justify-content: space-between;
  background: var(--im-surface); border-bottom: 1px solid var(--im-border);
}
.chat-header-info h2 { margin: 0; font-size: 16px; font-weight: 700; }
.chat-header-info p { margin: 4px 0 0; font-size: 12px; color: var(--im-muted); }
.offline-text { color: var(--im-muted); }
.chat-header-actions { display: flex; gap: 6px; }

.history-bar {
  display: flex; gap: 8px; align-items: center;
  padding: 8px 16px; background: var(--im-surface);
  border-bottom: 1px solid var(--im-border);
}
.history-bar .el-input { flex: 1; max-width: 280px; }

.chat-messages { flex: 1; }
.messages-inner { padding: 16px 20px; display: flex; flex-direction: column; gap: 16px; }
.load-more { text-align: center; padding: 4px 0 8px; }

/* Message rows */
.msg-row { display: flex; align-items: flex-start; gap: 10px; }
.msg-row.mine { flex-direction: row-reverse; }
.msg-row.sys { justify-content: center; }
.sys-msg {
  font-size: 12px; color: var(--im-muted);
  background: var(--im-surface-2); padding: 4px 14px;
  border-radius: 12px; border: 1px dashed var(--im-border);
}
.msg-content { max-width: min(480px, 70%); display: flex; flex-direction: column; gap: 4px; }
.msg-row.mine .msg-content { align-items: flex-end; }
.msg-sender { font-size: 12px; color: var(--im-muted); padding-left: 4px; }
.msg-bubble {
  padding: 10px 14px; border-radius: 16px;
  background: var(--im-surface-2); border: 1px solid var(--im-border);
  line-height: 1.5; word-break: break-word; white-space: pre-wrap;
}
.msg-row.mine .msg-bubble {
  background: linear-gradient(135deg, rgba(99,102,241,0.35), rgba(99,102,241,0.2));
  border-color: rgba(99,102,241,0.3);
  border-bottom-right-radius: 4px;
}
.msg-row:not(.mine):not(.sys) .msg-bubble { border-bottom-left-radius: 4px; }
.msg-bubble.revoked { opacity: 0.6; }
.revoked-text { font-style: italic; color: var(--im-muted); font-size: 13px; }
.msg-img { max-width: 240px; max-height: 200px; border-radius: 10px; display: block; }
.msg-file {
  display: flex; align-items: center; gap: 8px;
  color: var(--im-accent-hover); text-decoration: none; font-size: 14px;
}
.msg-file:hover { text-decoration: underline; }
.file-icon { font-size: 20px; }
.msg-footer {
  display: flex; align-items: center; gap: 6px;
  font-size: 11px; color: var(--im-muted); padding: 0 4px;
}
.msg-row.mine .msg-footer { flex-direction: row-reverse; }
.read-tag { color: var(--im-muted); }
.read-tag.read { color: var(--im-accent-hover); }
.msg-avatar { flex-shrink: 0; }

/* Composer */
.chat-composer {
  display: flex; align-items: flex-end; gap: 10px;
  padding: 12px 16px; background: var(--im-surface);
  border-top: 1px solid var(--im-border);
}
.composer-toolbar { flex-shrink: 0; }
.chat-composer .el-textarea { flex: 1; }
.send-btn { flex-shrink: 0; height: 40px; padding: 0 24px; }

/* Welcome */
.welcome {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 12px;
  color: var(--im-muted);
}
.welcome-icon { font-size: 64px; opacity: 0.5; }
.welcome h2 { margin: 0; color: var(--im-text); font-size: 22px; }
.welcome p { margin: 0; font-size: 14px; }
.welcome-stats { display: flex; gap: 16px; margin-top: 24px; }
.stat-card {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 16px 28px; border-radius: 14px;
  background: var(--im-surface); border: 1px solid var(--im-border);
}
.stat-card strong { font-size: 28px; color: var(--im-accent-hover); }
.stat-card span { font-size: 12px; color: var(--im-muted); }

/* Drawer */
.drawer-loading { text-align: center; padding: 20px; color: var(--im-muted); }
.member-item {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 0; border-bottom: 1px solid var(--im-border);
}
.invite-list { display: flex; flex-direction: column; gap: 8px; max-height: 300px; overflow-y: auto; }
</style>
