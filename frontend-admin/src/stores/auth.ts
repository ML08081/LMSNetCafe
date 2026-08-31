import { defineStore } from 'pinia'
import { http } from '../api/http'

export interface UserProfile {
  id: number
  memberId: number | null
  username: string
  realName: string
  status: string
  roles: string[]
  permissions: string[]
}

interface LoginResponse {
  token: string
  user: UserProfile
}

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('lms-token') || '',
    user: readStoredUser()
  }),
  getters: {
    primaryRole: (state) => state.user?.roles[0] ?? '',
    roleLabel(): string {
      return {
        super_admin: '超级管理员',
        front_desk: '前台人员',
        customer: '普通用户'
      }[this.primaryRole] ?? '系统用户'
    },
    homePath(): string {
      return this.primaryRole === 'customer' ? '/portal' : '/'
    }
  },
  actions: {
    async login(username: string, password: string) {
      const response = await http.post<ApiResponse<LoginResponse>>('/auth/login', { username, password })
      if (response.data.code !== 0) {
        throw new Error(response.data.message)
      }
      this.token = response.data.data.token
      this.user = response.data.data.user
      localStorage.setItem('lms-token', this.token)
      localStorage.setItem('lms-user', JSON.stringify(this.user))
    },
    async fetchProfile() {
      const response = await http.get<ApiResponse<UserProfile>>('/auth/profile')
      this.user = response.data.data
      localStorage.setItem('lms-user', JSON.stringify(this.user))
    },
    hasPermission(permission: string) {
      return Boolean(this.user?.permissions.includes(permission))
    },
    hasRole(role: string) {
      return Boolean(this.user?.roles.includes(role))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('lms-token')
      localStorage.removeItem('lms-user')
    }
  }
})

function readStoredUser(): UserProfile | null {
  const raw = localStorage.getItem('lms-user')
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as UserProfile
  } catch {
    localStorage.removeItem('lms-user')
    return null
  }
}
