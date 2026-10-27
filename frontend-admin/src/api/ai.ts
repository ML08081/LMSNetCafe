import { http } from './http'

interface Envelope<T> { data: T }
export interface AiStatus { configured: boolean; model: string; message: string }
export interface AssistantReply {
  answer: string; mode: 'MODEL' | 'BUILTIN'; model: string; notice: string; asOf: string
  facts: { balance: number; accountStatus: string; idleDevices: number; currentSession: null | {
    deviceCode: string; durationMinutes: number; estimatedAmount: number; availableBalance: number
    remainingMinutesEstimate: number; pricePerHour: number; minMinutes: number; billingUnitMinutes: number
  } }
  sources: { title: string; path: string }[]
}
export interface ReportMetrics {
  date: string; asOf: string; netConsumption: number; grossConsumption: number; refundAmount: number
  machineConsumption: number; shopConsumption: number; rechargeCash: number; rechargeGifts: number
  previousNetConsumption: number; netChange: number; netChangePercent: number | null
  startedSessions: number; settledSessions: number; visitingMembers: number; newMembers: number
  utilizationPercent: number; currentDeviceCount: number; currentFaultDevices: number
  openFaultsNow: number; pendingOrdersNow: number; newFaults: number; paidOrders: number; cancelledOrders: number
  definitions: string[]
  areas: { area: string; devices: number; currentFaultDevices: number; currentIdleDevices: number; usedMinutes: number; utilizationPercent: number }[]
  hourlyConsumption: { hour: number; amount: number }[]
}
export interface DailyReport {
  date: string; analysis: string; mode: 'MODEL' | 'BUILTIN'; model: string; notice: string
  generatedAt: string; metrics: ReportMetrics
}
export interface ReportHistory { reportDate: string; mode: string; generatedAt: string }
export async function getAiStatus() { return (await http.get<Envelope<AiStatus>>('/ai/status')).data.data }
export async function askAssistant(question: string, history: { role: string; content: string }[]) {
  return (await http.post<Envelope<AssistantReply>>('/portal/assistant', { question, history }, { timeout: 25000 })).data.data
}
export async function getDailyReport(date: string) { return (await http.get<Envelope<DailyReport | null>>(`/ai/reports/${date}`)).data.data }
export async function getReportHistory() { return (await http.get<Envelope<ReportHistory[]>>('/ai/reports')).data.data }
export async function generateDailyReport(date: string) {
  return (await http.post<Envelope<DailyReport>>('/ai/reports/generate', { date }, { timeout: 30000 })).data.data
}
