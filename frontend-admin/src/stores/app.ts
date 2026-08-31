import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    shopName: 'LMS Net Cafe',
    apiOnline: false
  }),
  actions: {
    setApiOnline(value: boolean) {
      this.apiOnline = value
    }
  }
})
