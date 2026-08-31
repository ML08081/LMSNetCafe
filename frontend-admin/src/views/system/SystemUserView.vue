<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">系统用户</h1>
        <p class="page-subtitle">维护后台账号、角色绑定、会员入口和启停状态。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增用户</el-button>
    </div>

    <section class="panel">
      <el-table v-loading="loading" :data="users" style="width: 100%">
        <el-table-column prop="username" label="账号" width="130" />
        <el-table-column prop="realName" label="姓名" width="130" />
        <el-table-column prop="role" label="角色" width="150" />
        <el-table-column prop="memberNo" label="绑定会员" width="130" />
        <el-table-column prop="phone" label="手机号" width="150" />
        <el-table-column prop="lastLoginAt" label="最后登录" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="warning" link @click="resetPassword(row)">重置密码</el-button>
            <el-button :type="row.enabled ? 'danger' : 'success'" link @click="toggleStatus(row)">
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新增用户'" width="520px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="账号" required>
          <el-input v-model="form.username" :disabled="Boolean(editingId)" placeholder="请输入登录账号" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="初始密码" required>
          <el-input v-model="form.password" type="password" show-password placeholder="默认可填 123456" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="form.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.roleId" style="width: 100%" placeholder="请选择角色">
            <el-option v-for="role in roles" :key="role.id" :label="role.roleName" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定会员">
          <el-select v-model="form.memberId" clearable filterable style="width: 100%" placeholder="普通用户可绑定会员">
            <el-option
              v-for="member in members"
              :key="member.id"
              :label="`${member.memberNo} - ${member.name}`"
              :value="member.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button label="ENABLED">启用</el-radio-button>
            <el-radio-button label="DISABLED">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'

interface ApiResponse<T> {
  data: T
}

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const users = ref<any[]>([])
const roles = ref<any[]>([])
const members = ref<any[]>([])
const form = reactive({
  username: '',
  password: '123456',
  realName: '',
  phone: '',
  memberId: undefined as number | undefined,
  status: 'ENABLED',
  roleId: undefined as number | undefined
})

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const [usersRes, rolesRes, membersRes] = await Promise.all([
      http.get<ApiResponse<any[]>>('/system/users'),
      http.get<ApiResponse<any[]>>('/system/users/roles'),
      http.get<ApiResponse<any[]>>('/members')
    ])
    roles.value = rolesRes.data.data
    members.value = membersRes.data.data
    users.value = usersRes.data.data.map((item) => ({
      ...item,
      role: item.roleNames ?? '-',
      roleId: parseRoleId(item.roleIds),
      lastLoginAt: item.lastLoginAt ? item.lastLoginAt.replace('T', ' ').slice(0, 16) : '-',
      enabled: item.status === 'ENABLED'
    }))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    username: '',
    password: '123456',
    realName: '',
    phone: '',
    memberId: undefined,
    status: 'ENABLED',
    roleId: roles.value[1]?.id ?? roles.value[0]?.id
  })
  dialogVisible.value = true
}

function openEdit(row: any) {
  editingId.value = row.id
  Object.assign(form, {
    username: row.username,
    password: '',
    realName: row.realName,
    phone: row.phone,
    memberId: row.memberId,
    status: row.status,
    roleId: row.roleId
  })
  dialogVisible.value = true
}

async function saveUser() {
  if (!form.username || !form.realName || !form.roleId || (!editingId.value && !form.password)) {
    ElMessage.warning('请补全账号、姓名、角色和密码')
    return
  }
  saving.value = true
  try {
    const payload = {
      username: form.username.trim(),
      password: form.password,
      realName: form.realName.trim(),
      phone: form.phone?.trim(),
      memberId: form.memberId,
      status: form.status,
      roleId: form.roleId
    }
    if (editingId.value) {
      await http.patch(`/system/users/${editingId.value}`, payload)
    } else {
      await http.post('/system/users', payload)
    }
    ElMessage.success('用户信息已保存')
    dialogVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

async function resetPassword(row: any) {
  try {
    const result = await ElMessageBox.prompt(`为 ${row.username} 设置新密码`, '重置密码', {
      confirmButtonText: '确认重置',
      cancelButtonText: '取消',
      inputValue: '123456',
      inputPattern: /^.{6,32}$/,
      inputErrorMessage: '密码长度需为 6-32 位'
    })
    await http.post(`/system/users/${row.id}/reset-password`, { password: result.value })
    ElMessage.success('密码已重置')
  } catch (error: any) {
    if (error !== 'cancel') showError(error)
  }
}

async function toggleStatus(row: any) {
  const status = row.enabled ? 'DISABLED' : 'ENABLED'
  await http.patch(`/system/users/${row.id}/status`, { status })
  ElMessage.success(status === 'ENABLED' ? '用户已启用' : '用户已停用')
  await loadData()
}

function parseRoleId(value: unknown) {
  const first = String(value ?? '').split(',')[0]
  return first ? Number(first) : undefined
}

function showError(error: any) {
  ElMessage.error(error?.response?.data?.message ?? '操作失败')
}
</script>
