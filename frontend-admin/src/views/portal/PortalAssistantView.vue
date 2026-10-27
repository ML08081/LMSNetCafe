<template>
  <section class="page assistant-page">
    <div class="page-header">
      <div><h1 class="page-title">智能服务助手</h1><p class="page-subtitle">查余额、解释计费、了解上机与报修流程。操作需要你到对应页面确认。</p></div>
      <el-button :disabled="sending" @click="clearChat">清空对话</el-button>
    </div>
    <el-alert v-if="status" :title="status.message" :type="status.configured ? 'success' : 'info'" show-icon :closable="false" />
    <div class="assistant-layout">
      <section class="panel assistant-conversation">
        <div ref="chatBox" class="assistant-messages" aria-live="polite" aria-relevant="additions">
          <div v-if="!messages.length" class="assistant-welcome">
            <h2>你好，有什么需要帮忙？</h2><p>回答会结合你本人的账户和当前会话，并显示数据依据。</p>
            <div class="assistant-prompts"><el-button v-for="question in suggestions" :key="question" :disabled="sending" @click="ask(question)">{{ question }}</el-button></div>
          </div>
          <article v-for="message in messages" :key="message.id" class="assistant-message" :class="message.role">
            <div class="assistant-message-label">{{ message.role === 'user' ? '我' : '服务助手' }}
              <el-tag v-if="message.reply" size="small" :type="message.reply.mode === 'MODEL' ? 'success' : 'info'">{{ message.reply.mode === 'MODEL' ? 'AI 回答' : '基础问答' }}</el-tag>
            </div>
            <p class="assistant-text">{{ message.content }}</p>
            <template v-if="message.reply">
              <p class="assistant-note">{{ message.reply.notice }} · 数据时间 {{ formatTime(message.reply.asOf) }}</p>
              <div class="assistant-sources"><el-button v-for="source in message.reply.sources" :key="source.title" text type="primary" @click="router.push(source.path)">{{ source.title }}</el-button></div>
            </template>
          </article>
          <p v-if="sending" class="assistant-note" role="status">正在查询业务数据并整理回答…</p>
        </div>
        <form class="assistant-composer" @submit.prevent="ask()">
          <el-input v-model="input" type="textarea" :rows="3" maxlength="500" show-word-limit :disabled="sending" placeholder="例如：我现在下机需要多少钱？（Ctrl + Enter 发送）" aria-label="输入问题" @keydown.ctrl.enter.prevent="ask()" />
          <el-button type="primary" native-type="submit" :loading="sending" :disabled="!input.trim()">发送问题</el-button>
        </form>
      </section>
      <aside class="panel assistant-facts">
        <div class="panel-header"><h2>本次查询依据</h2></div>
        <template v-if="latest">
          <div class="assistant-fact"><span>账户余额</span><strong>{{ money(latest.facts.balance) }}</strong></div>
          <div class="assistant-fact"><span>账户状态</span><strong>{{ latest.facts.accountStatus === 'ACTIVE' ? '正常' : '受限' }}</strong></div>
          <template v-if="latest.facts.currentSession">
            <div class="assistant-fact"><span>当前机位</span><strong>{{ latest.facts.currentSession.deviceCode }}</strong></div>
            <div class="assistant-fact"><span>当前上机费</span><strong>{{ money(latest.facts.currentSession.estimatedAmount) }}</strong></div>
            <div class="assistant-fact"><span>预计结算后余额</span><strong>{{ money(latest.facts.currentSession.availableBalance) }}</strong></div>
            <div class="assistant-fact"><span>预算剩余时长</span><strong>约 {{ latest.facts.currentSession.remainingMinutesEstimate }} 分钟</strong></div>
            <p class="assistant-note">其他消费会减少预算，最终费用以确认下机时为准。</p>
          </template>
          <el-empty v-else description="当前未上机" :image-size="55" />
          <p class="assistant-note">这是 {{ formatTime(latest.asOf) }} 的查询快照，再次提问会更新数据。</p>
        </template>
        <p v-else class="assistant-note">发送问题后，会显示本人余额、当前机位和计费信息。</p>
        <div class="assistant-shortcuts"><el-button @click="router.push('/portal/devices')">机位与下机</el-button><el-button @click="router.push('/portal/support')">报修当前机位</el-button><el-button @click="router.push('/portal/account')">账户流水</el-button></div>
      </aside>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { askAssistant, getAiStatus, type AiStatus, type AssistantReply } from '../../api/ai'
const router = useRouter()
const status = ref<AiStatus>(), input = ref(''), sending = ref(false), chatBox = ref<HTMLElement>()
const messages = ref<{ id: number; role: 'user' | 'assistant'; content: string; reply?: AssistantReply }[]>([])
const suggestions = ['我现在下机需要多少钱？', '我的余额还能用多久？', '为什么刚上机就产生费用？', '怎样报修当前机位？']
const latest = computed(() => [...messages.value].reverse().find(message => message.reply)?.reply)
let id = 0
onMounted(async () => { try { status.value = await getAiStatus() } catch { ElMessage.error('暂时无法获取助手状态，请刷新重试') } })
async function scroll() { await nextTick(); if (chatBox.value) chatBox.value.scrollTop = chatBox.value.scrollHeight }
async function ask(question?: string) {
  const content = (question ?? input.value).trim()
  if (!content || sending.value) return
  const history = messages.value.slice(-8).map(message => ({ role: message.role, content: message.content.slice(0, 1000) }))
  messages.value.push({ id: ++id, role: 'user', content }); input.value = ''; sending.value = true; await scroll()
  try {
    const reply = await askAssistant(content, history)
    messages.value.push({ id: ++id, role: 'assistant', content: reply.answer, reply })
  } catch (error: any) {
    messages.value.push({ id: ++id, role: 'assistant', content: error.response?.data?.message ?? '服务暂时不可用，请稍后重试。你也可以直接到机位、账户或报修页面办理。' })
  } finally { sending.value = false; await scroll() }
}
function clearChat() { messages.value = []; input.value = '' }
function money(value: unknown) { return `¥${Number(value ?? 0).toFixed(2)}` }
function formatTime(value: string) { return new Date(value).toLocaleString('zh-CN', { hour12: false }) }
</script>
<style scoped>
.assistant-layout { display:grid; grid-template-columns:minmax(0,1fr) 300px; gap:18px; align-items:start }
.assistant-conversation { padding:0; overflow:hidden }
.assistant-messages { min-height:360px; max-height:62vh; overflow:auto; padding:24px }
.assistant-welcome { padding:24px 0; color:#546174 }.assistant-welcome h2 { color:#17202a }
.assistant-prompts { display:flex; flex-wrap:wrap; gap:12px }.assistant-prompts .el-button { margin:0; height:auto; white-space:normal; padding:12px }
.assistant-message { margin-bottom:20px; padding:16px; border-radius:12px; background:#f5f7fa }
.assistant-message.user { background:#eaf3ff; margin-left:10% }.assistant-message.assistant { margin-right:3% }
.assistant-message-label { display:flex; align-items:center; gap:10px; font-weight:600; color:#52647c; font-size:13px }
.assistant-text { white-space:pre-wrap; overflow-wrap:anywhere; line-height:1.8; margin:10px 0 }
.assistant-note { font-size:12px; color:#737f90; line-height:1.7; overflow-wrap:anywhere }
.assistant-sources { display:flex; flex-wrap:wrap; gap:4px }.assistant-sources .el-button { margin:0; white-space:normal; height:auto }
.assistant-composer { border-top:1px solid #e7ebf0; padding:18px; display:grid; gap:12px; justify-items:end }
.assistant-fact { display:flex; justify-content:space-between; gap:12px; padding:13px 0; border-bottom:1px solid #edf0f4; font-size:14px }
.assistant-fact span { color:#737f90 }.assistant-shortcuts { display:grid; gap:10px; margin-top:20px }.assistant-shortcuts .el-button { margin:0 }
@media(max-width:1000px) { .assistant-layout { grid-template-columns:1fr }.assistant-messages { max-height:55vh }.assistant-facts { width:auto } }
</style>
