<template>
  <section class="page portal-page service-market-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">呼叫与点餐</h1>
        <p class="page-subtitle">呼叫前台，或使用预存余额购买饮品、零食、简餐、真实宠物陪伴与高手陪玩</p>
      </div>
      <div class="service-balance"><span>可用余额</span><strong>{{ money(balance) }}</strong></div>
    </div>

    <section class="panel quick-call-panel">
      <div class="panel-header">
        <h2>需要帮助</h2>
        <span class="muted-inline">呼叫将自动关联你当前使用的机位</span>
      </div>
      <div class="quick-call-grid">
        <el-button v-for="item in callOptions" :key="item.type" :loading="calling === item.type" @click="createCall(item.type)">
          <el-icon><component :is="item.icon" /></el-icon>
          <span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
        </el-button>
      </div>
    </section>

    <div class="service-market-grid">
      <section class="panel product-panel" v-loading="loading">
        <div class="panel-header product-toolbar">
          <h2>门店商品与服务</h2>
          <el-segmented v-model="category" :options="categoryOptions" size="small" />
        </div>
        <div class="product-grid">
          <article v-for="product in filteredProducts" :key="product.id" class="product-item">
            <div class="product-symbol" :class="`category-${product.category.toLowerCase()}`">
              <el-icon><component :is="productIcon(product.category)" /></el-icon>
            </div>
            <div class="product-copy">
              <strong>{{ product.productName }}</strong>
              <span>{{ categoryLabel(product.category) }} · {{ productMeta(product) }} · 库存 {{ product.stock }}</span>
              <small v-if="product.description">{{ product.description }}</small>
              <b>{{ money(product.price) }}</b>
            </div>
            <el-input-number
              :model-value="cart[product.id] || 0"
              :min="0"
              :max="Math.min(product.stock, 20)"
              size="small"
              @change="(value: number | undefined) => setQuantity(product.id, value || 0)"
            />
          </article>
        </div>
        <el-empty v-if="!loading && !filteredProducts.length" description="当前分类暂无可售商品" :image-size="72" />
      </section>

      <aside class="panel cart-panel">
        <div class="panel-header"><h2>本次订单</h2><el-tag>{{ cartCount }} 件</el-tag></div>
        <div v-if="cartItems.length" class="cart-lines">
          <div v-for="item in cartItems" :key="item.id">
            <span>{{ item.productName }} × {{ cart[item.id] }}</span>
            <strong>{{ money(Number(item.price) * cart[item.id]) }}</strong>
          </div>
        </div>
        <el-empty v-else description="从左侧选择商品" :image-size="66" />
        <el-input v-model="remark" type="textarea" :rows="2" maxlength="80" show-word-limit placeholder="口味、配送等备注（选填）" />
        <div class="cart-total"><span>余额支付</span><strong>{{ money(cartTotal) }}</strong></div>
        <el-button type="primary" :disabled="!cartCount" :loading="paying" @click="submitOrder">
          <el-icon><Wallet /></el-icon>确认支付
        </el-button>
      </aside>
    </div>

    <section class="panel service-history-panel">
      <el-tabs v-model="historyTab" @tab-change="loadHistory">
        <el-tab-pane label="我的订单" name="orders">
          <el-table :data="orders" empty-text="暂无订单">
            <el-table-column prop="orderNo" label="订单号" min-width="180" />
            <el-table-column label="商品" min-width="220">
              <template #default="scope">{{ scope.row.items.map((item: any) => `${item.productName}×${item.quantity}`).join('、') }}</template>
            </el-table-column>
            <el-table-column prop="deviceCode" label="配送机位" width="110" />
            <el-table-column label="金额" width="100"><template #default="scope">{{ money(scope.row.totalAmount) }}</template></el-table-column>
            <el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ orderStatus(scope.row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="时间" width="160"><template #default="scope">{{ formatDate(scope.row.createdAt) }}</template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="服务呼叫" name="calls">
          <el-table :data="calls" empty-text="暂无服务呼叫">
            <el-table-column label="服务" min-width="150"><template #default="scope">{{ callLabel(scope.row.callType) }}</template></el-table-column>
            <el-table-column prop="message" label="说明" min-width="200" />
            <el-table-column prop="deviceCode" label="机位" width="110" />
            <el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ callStatus(scope.row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="时间" width="160"><template #default="scope">{{ formatDate(scope.row.createdAt) }}</template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, reactive, ref } from 'vue'
import { Bell, Box, CoffeeCup, Cpu, Dish, Food, Medal, Service, Star, Trophy, Wallet } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'

interface ApiResponse<T> { data: T }
interface Product {
  id: number
  productName: string
  category: string
  productType: string
  petSpecies?: string
  petBreed?: string
  expertRole?: string
  serviceDurationMinutes?: number
  description?: string
  price: number
  stock: number
}

const callOptions = [
  { type: 'FRONT_DESK', label: '呼叫前台', description: '账户、计费或其他协助', icon: markRaw(Bell) },
  { type: 'CLEANING', label: '清洁机位', description: '桌面或外设需要清洁', icon: markRaw(Service) },
  { type: 'SUPPLIES', label: '补充用品', description: '纸巾、一次性用品等', icon: markRaw(Box) },
  { type: 'DEVICE_HELP', label: '设备协助', description: '外设或电脑使用异常', icon: markRaw(Cpu) }
]
const categoryOptions = [
  { label: '全部', value: 'ALL' }, { label: '饮品', value: 'DRINK' },
  { label: '零食', value: 'SNACK' }, { label: '简餐', value: 'MEAL' },
  { label: '猫类宠物', value: 'PET_CAT' }, { label: '犬类宠物', value: 'PET_DOG' },
  { label: '爬宠陪伴', value: 'PET_REPTILE' }, { label: '高手陪玩', value: 'EXPERT_PLAY' }
]
const loading = ref(false)
const paying = ref(false)
const calling = ref('')
const balance = ref(0)
const products = ref<Product[]>([])
const orders = ref<any[]>([])
const calls = ref<any[]>([])
const category = ref('ALL')
const historyTab = ref('orders')
const remark = ref('')
const cart = reactive<Record<number, number>>({})
const filteredProducts = computed(() => category.value === 'ALL' ? products.value : products.value.filter(item => item.category === category.value))
const cartItems = computed(() => products.value.filter(item => (cart[item.id] || 0) > 0))
const cartCount = computed(() => cartItems.value.reduce((sum, item) => sum + cart[item.id], 0))
const cartTotal = computed(() => cartItems.value.reduce((sum, item) => sum + Number(item.price) * cart[item.id], 0))

onMounted(async () => { await Promise.all([loadProducts(), loadHistory()]) })

async function loadProducts() {
  loading.value = true
  try {
    const response = await http.get<ApiResponse<{ balance: number; products: Product[] }>>('/portal/services/products')
    balance.value = Number(response.data.data.balance)
    products.value = response.data.data.products
  } finally { loading.value = false }
}

async function loadHistory() {
  if (historyTab.value === 'orders') {
    const response = await http.get<ApiResponse<any[]>>('/portal/services/orders')
    orders.value = response.data.data
  } else {
    const response = await http.get<ApiResponse<any[]>>('/portal/services/calls')
    calls.value = response.data.data
  }
}

async function createCall(type: string) {
  try {
    const { value } = await ElMessageBox.prompt('可补充具体需求，也可以直接确认。', callLabel(type), {
      inputPlaceholder: '补充说明（选填）', inputValidator: value => !value || value.length <= 255 || '说明不能超过 255 字'
    })
    calling.value = type
    await http.post('/portal/services/calls', { callType: type, message: value || null })
    ElMessage.success('已通知前台，请留意处理状态')
    historyTab.value = 'calls'
    await loadHistory()
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.response?.data?.message || '呼叫失败')
  } finally { calling.value = '' }
}

async function submitOrder() {
  if (cartTotal.value > balance.value) return ElMessage.warning('预存余额不足，请先到前台充值')
  try {
    await ElMessageBox.confirm(`将从预存余额支付 ${money(cartTotal.value)}，确认下单吗？`, '余额支付', { type: 'warning' })
    paying.value = true
    const items = cartItems.value.map(item => ({ productId: item.id, quantity: cart[item.id] }))
    const response = await http.post<ApiResponse<{ balance: number }>>('/portal/services/orders', { items, remark: remark.value || null })
    balance.value = Number(response.data.data.balance)
    Object.keys(cart).forEach(key => delete cart[Number(key)])
    remark.value = ''
    ElMessage.success('支付成功，前台已收到订单')
    await Promise.all([loadProducts(), loadHistory()])
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.response?.data?.message || '支付失败')
  } finally { paying.value = false }
}

function setQuantity(id: number, value: number) { cart[id] = value }
function money(value: unknown) { return `¥${Number(value || 0).toFixed(2)}` }
function formatDate(value: string) { return value ? value.replace('T', ' ').slice(0, 16) : '-' }
function categoryLabel(value: string) {
  return ({ DRINK: '饮品', SNACK: '零食', MEAL: '简餐', PET_CAT: '猫类宠物', PET_DOG: '犬类宠物', PET_REPTILE: '爬宠陪伴', EXPERT_PLAY: '高手陪玩' } as any)[value] || value
}
function productMeta(product: Product) {
  if (product.productType === 'PET_COMPANION') {
    return `${petSpeciesLabel(product.petSpecies)} / ${product.petBreed || '未填写品种'} / ${product.serviceDurationMinutes || 0} 分钟`
  }
  if (product.productType === 'EXPERT_COMPANION') {
    return `${product.expertRole || '高手服务'} / ${product.serviceDurationMinutes || 0} 分钟`
  }
  return '普通商品'
}
function petSpeciesLabel(value?: string) {
  return ({ CAT: '猫', DOG: '狗', REPTILE: '爬宠' } as Record<string, string>)[value || ''] || '宠物'
}
function productIcon(value: string) {
  if (value === 'DRINK') return CoffeeCup
  if (value === 'MEAL') return Dish
  if (value === 'PET_CAT') return Star
  if (value === 'PET_DOG') return Service
  if (value === 'PET_REPTILE') return Medal
  if (value === 'EXPERT_PLAY') return Trophy
  return Food
}
function callLabel(value: string) { return ({ FRONT_DESK: '呼叫前台', CLEANING: '清洁机位', SUPPLIES: '补充用品', DEVICE_HELP: '设备协助' } as any)[value] || value }
function orderStatus(value: string) { return ({ PENDING: '待接单', PREPARING: '准备中', DELIVERING: '配送中', COMPLETED: '已完成', CANCELLED: '已取消' } as any)[value] || value }
function callStatus(value: string) { return ({ PENDING: '待响应', PROCESSING: '处理中', COMPLETED: '已完成', CANCELLED: '已取消' } as any)[value] || value }
function statusType(value: string) { return value === 'COMPLETED' ? 'success' : value === 'CANCELLED' ? 'info' : value === 'PENDING' ? 'warning' : 'primary' }
</script>
