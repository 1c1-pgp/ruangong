<template>
  <div>
    <button class="task-btn" @click="open = true">🗒 日常任务</button>

    <div v-if="open" class="tm-overlay" @click.self="open = false">
      <div class="tm-modal">
        <header class="tm-header">
          <h3>日常任务</h3>
          <div>
            <button @click="openNew">新建</button>
            <button @click="open = false">关闭</button>
          </div>
        </header>

        <section class="tm-body">
          <form v-if="showForm" @submit.prevent="save">
            <div class="row">
              <label>标题</label>
              <input v-model="form.title" required />
            </div>
            <div class="row">
              <label>备注</label>
              <input v-model="form.notes" />
            </div>
            <div class="row">
              <label>到期时间</label>
              <input type="datetime-local" v-model="form.dueAt" />
            </div>
            <div class="row">
              <label>提醒</label>
              <input type="checkbox" v-model="form.remind" />
            </div>
            <div class="actions">
              <button type="submit">保存</button>
              <button type="button" @click="cancelEdit">取消</button>
            </div>
          </form>

          <ul class="task-list">
            <li v-for="(t, idx) in tasks" :key="t.id" :class="{done: t.completed}">
              <div class="left">
                <input type="checkbox" v-model="t.completed" @change="toggleComplete(t)" />
                <div class="meta">
                  <div class="title">{{ t.title }}</div>
                  <div class="notes">{{ t.notes }}</div>
                </div>
              </div>
              <div class="right">
                <div class="due">{{ t.dueAt ? formatDate(t.dueAt) : '' }}</div>
                <button @click="edit(t)">编辑</button>
                <button @click="remove(t.id)">删除</button>
              </div>
            </li>
          </ul>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'

type Task = {
  id: string
  title: string
  notes?: string
  dueAt?: string | null
  remind?: boolean
  completed?: boolean
  notified?: boolean
}

const open = ref(false)
const showForm = ref(false)
const tasks = ref<Task[]>([])
const form = ref<Task>({ id: '', title: '', notes: '', dueAt: null, remind: false, completed: false, notified: false })
let checker: number | null = null

const STORAGE_KEY = 'task_manager_tasks_v1'

function load() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    tasks.value = raw ? JSON.parse(raw) : []
  } catch (e) { tasks.value = [] }
}

function persist() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tasks.value))
}

function uid() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

function openNew() {
  form.value = { id: '', title: '', notes: '', dueAt: null, remind: false, completed: false, notified: false }
  showForm.value = true
}

function save() {
  const f = { ...form.value }
  if (!f.title) return
  if (!f.id) {
    f.id = uid()
    tasks.value.unshift(f)
  } else {
    const idx = tasks.value.findIndex((i) => i.id === f.id)
    if (idx >= 0) tasks.value.splice(idx, 1, f)
  }
  showForm.value = false
  persist()
}

function edit(t: Task) {
  form.value = { ...t }
  showForm.value = true
}

function cancelEdit() { showForm.value = false }

function remove(id: string) {
  tasks.value = tasks.value.filter((t) => t.id !== id)
  persist()
}

function toggleComplete(t: Task) {
  t.completed = !!t.completed
  persist()
}

function formatDate(s?: string | null) {
  if (!s) return ''
  const d = new Date(s)
  return d.toLocaleString()
}

function checkReminders() {
  const now = Date.now()
  for (const t of tasks.value) {
    if (t.remind && !t.notified && !t.completed && t.dueAt) {
      const ts = new Date(t.dueAt).getTime()
      if (!isNaN(ts) && ts <= now) {
        notifyTask(t)
        t.notified = true
      }
    }
  }
  persist()
}

function notifyTask(t: Task) {
  const title = '任务提醒：' + (t.title || '未命名')
  const body = t.notes || ''
  if (window.Notification && Notification.permission === 'granted') {
    new Notification(title, { body })
  } else if (window.Notification && Notification.permission !== 'denied') {
    Notification.requestPermission().then((p) => {
      if (p === 'granted') new Notification(title, { body })
    })
  }
  try { alert(title + (body ? '\n' + body : '')) } catch { }
}

onMounted(() => {
  load()
  if (window.Notification && Notification.permission !== 'granted') {
    try { Notification.requestPermission().catch(() => {}) } catch { }
  }
  checker = window.setInterval(checkReminders, 30 * 1000)
})

onBeforeUnmount(() => {
  if (checker) clearInterval(checker)
})
</script>

<style scoped>
.task-btn {
  position: fixed;
  right: 20px;
  bottom: 24px;
  background: #409eff;
  color: white;
  border: none;
  padding: 10px 14px;
  border-radius: 8px;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(64,158,255,0.24);
}
.tm-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4); display:flex; align-items:center; justify-content:center; z-index:2000;
}
.tm-modal { background: #fff; width:720px; max-width:95%; border-radius:8px; box-shadow:0 8px 30px rgba(0,0,0,0.3); overflow:hidden }
.tm-header { display:flex; justify-content:space-between; align-items:center; padding:12px 16px; border-bottom:1px solid #eee }
.tm-body { padding:16px; max-height:70vh; overflow:auto }
.row { display:flex; gap:8px; align-items:center; margin-bottom:8px }
.row label { width:80px }
.row input[type="text"], .row input[type="datetime-local"], .row input { flex:1; padding:6px 8px }
.actions { display:flex; gap:8px }
.task-list { list-style:none; padding:0; margin:12px 0 }

/* 未完成任务：明显深色背景，浅色文字 */
.task-list li {
  display:flex;
  justify-content:space-between;
  align-items:center;
  padding:12px 10px;
  margin-bottom:8px;
  border-radius:8px;
  background: linear-gradient(180deg, #1f2a3a 0%, #273645 100%);
  color: #f5f7fa;
  box-shadow: 0 4px 12px rgba(15,23,42,0.12);
}

/* 已完成任务：浅色背景，暗色文字 */
.task-list li.done {
  background: #f5f7fa;
  color: #3b4856;
  box-shadow: none;
}

.task-list li .title { font-weight:600 }
.task-list li.done .title { text-decoration:line-through; color: inherit }
.task-list .left { display:flex; gap:8px; align-items:center }
.task-list .meta { display:flex; flex-direction:column }
.task-list .notes { font-size:12px }
.task-list li { transition: background-color 0.2s ease, color 0.2s ease, box-shadow 0.2s ease }
.task-list .right { display:flex; gap:8px; align-items:center }

/* 按钮在深色背景上需要更高对比 */
.task-list button { background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.12); color: #fff; padding:6px 8px; border-radius:6px; cursor:pointer }
.task-list li.done button { background: transparent; border: 1px solid #d8dde3; color: #3b4856 }
</style>
