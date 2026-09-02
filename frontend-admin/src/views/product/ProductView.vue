<template>
  <section class="page product-manage-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">商品管理</h1>
        <p class="page-subtitle">维护饮品、零食、简餐、真实宠物陪伴和真人高手陪玩服务。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增商品</el-button>
    </div>

    <section class="panel">
      <el-form class="filter-bar" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="编码 / 名称 / 品种 / 高手类型" clearable @keyup.enter="loadProducts" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.productType" clearable placeholder="全部" style="width: 150px">
            <el-option v-for="item in productTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="filters.category" clearable placeholder="全部" style="width: 150px">
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px">
            <el-option label="上架" value="ENABLED" />
            <el-option label="下架" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadProducts">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="products" style="width: 100%">
        <el-table-column prop="productCode" label="编码" width="150" />
        <el-table-column prop="productName" label="名称" min-width="210" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ productTypeLabel(row.productType) }}</template>
        </el-table-column>
        <el-table-column label="分类" width="120">
          <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column label="品种 / 高手" min-width="160">
          <template #default="{ row }">{{ serviceLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="时长" width="90">
          <template #default="{ row }">{{ row.serviceDurationMinutes ? `${row.serviceDurationMinutes} 分钟` : '-' }}</template>
        </el-table-column>
        <el-table-column label="价格" width="100">
          <template #default="{ row }">{{ money(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存/名额" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '上架' : '下架' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="deleteProduct(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑商品' : '新增商品'" width="680px">
      <el-form :model="form" label-width="112px">
        <el-form-item label="商品编码" required>
          <el-input v-model="form.productCode" placeholder="例如 PET-CAT-LIHUA" />
        </el-form-item>
        <el-form-item label="商品名称" required>
          <el-input v-model="form.productName" placeholder="例如 宠物陪伴·狸花猫 30 分钟" />
        </el-form-item>
        <el-form-item label="商品类型" required>
          <el-select v-model="form.productType" style="width: 100%" @change="syncCategory">
            <el-option v-for="item in productTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品分类" required>
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="item in formCategoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <template v-if="form.productType === 'PET_COMPANION'">
          <el-form-item label="宠物类别" required>
            <el-select v-model="form.petSpecies" style="width: 100%" @change="syncPetCategory">
              <el-option label="猫" value="CAT" />
              <el-option label="狗" value="DOG" />
              <el-option label="爬宠" value="REPTILE" />
            </el-select>
          </el-form-item>
          <el-form-item label="宠物品种" required>
            <el-input v-model="form.petBreed" placeholder="例如 狸花猫 / 布偶猫 / 拉布拉多 / 豹纹守宫" />
          </el-form-item>
        </template>
        <el-form-item v-if="form.productType === 'EXPERT_COMPANION'" label="高手类型" required>
          <el-input v-model="form.expertRole" placeholder="例如 FPS 枪王 / MOBA 指挥 / 副本速刷" />
        </el-form-item>
        <el-form-item v-if="form.productType !== 'MERCHANDISE'" label="服务时长">
          <el-input-number v-model="form.serviceDurationMinutes" :min="0" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
        <div class="product-form-row">
          <el-form-item label="价格" required>
            <el-input-number v-model="form.price" :min="0" :precision="2" :step="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="库存/名额" required>
            <el-input-number v-model="form.stock" :min="0" :step="1" style="width: 100%" />
          </el-form-item>
        </div>
        <div class="product-form-row">
          <el-form-item label="排序" required>
            <el-input-number v-model="form.sortOrder" :min="0" :step="10" style="width: 100%" />
          </el-form-item>
          <el-form-item label="状态" required>
            <el-radio-group v-model="form.status">
              <el-radio-button label="ENABLED">上架</el-radio-button>
              <el-radio-button label="DISABLED">下架</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProduct">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'

interface ApiResponse<T> { data: T }
interface Product {
  id: number
  productCode: string
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
  status: string
  sortOrder: number
}

const productTypeOptions = [
  { label: '普通商品', value: 'MERCHANDISE' },
  { label: '宠物陪伴', value: 'PET_COMPANION' },
  { label: '高手陪玩', value: 'EXPERT_COMPANION' }
]
const categoryOptions = [
  { label: '饮品', value: 'DRINK' },
  { label: '零食', value: 'SNACK' },
  { label: '简餐', value: 'MEAL' },
  { label: '猫类宠物', value: 'PET_CAT' },
  { label: '犬类宠物', value: 'PET_DOG' },
  { label: '爬宠陪伴', value: 'PET_REPTILE' },
  { label: '高手陪玩', value: 'EXPERT_PLAY' }
]
const merchandiseCategories = categoryOptions.filter(item => ['DRINK', 'SNACK', 'MEAL'].includes(item.value))
const petCategories = categoryOptions.filter(item => ['PET_CAT', 'PET_DOG', 'PET_REPTILE'].includes(item.value))
const expertCategories = categoryOptions.filter(item => item.value === 'EXPERT_PLAY')

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const products = ref<Product[]>([])
const filters = reactive({ keyword: '', productType: '', category: '', status: '' })
const form = reactive({
  productCode: '',
  productName: '',
  category: 'DRINK',
  productType: 'MERCHANDISE',
  petSpecies: '',
  petBreed: '',
  expertRole: '',
  serviceDurationMinutes: 0,
  description: '',
  price: 0,
  stock: 0,
  status: 'ENABLED',
  sortOrder: 100
})
const formCategoryOptions = computed(() => {
  if (form.productType === 'PET_COMPANION') return petCategories
  if (form.productType === 'EXPERT_COMPANION') return expertCategories
  return merchandiseCategories
})

onMounted(loadProducts)

async function loadProducts() {
  loading.value = true
  try {
    const response = await http.get<ApiResponse<Product[]>>('/products', { params: filters })
    products.value = response.data.data
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  Object.assign(filters, { keyword: '', productType: '', category: '', status: '' })
  loadProducts()
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    productCode: '',
    productName: '',
    category: 'DRINK',
    productType: 'MERCHANDISE',
    petSpecies: '',
    petBreed: '',
    expertRole: '',
    serviceDurationMinutes: 0,
    description: '',
    price: 0,
    stock: 0,
    status: 'ENABLED',
    sortOrder: 100
  })
  dialogVisible.value = true
}

function openEdit(row: Product) {
  editingId.value = row.id
  Object.assign(form, {
    productCode: row.productCode,
    productName: row.productName,
    category: row.category,
    productType: row.productType,
    petSpecies: row.petSpecies || '',
    petBreed: row.petBreed || '',
    expertRole: row.expertRole || '',
    serviceDurationMinutes: Number(row.serviceDurationMinutes || 0),
    description: row.description || '',
    price: Number(row.price),
    stock: Number(row.stock),
    status: row.status,
    sortOrder: Number(row.sortOrder || 0)
  })
  dialogVisible.value = true
}

async function saveProduct() {
  if (!form.productCode || !form.productName) return ElMessage.warning('请填写商品编码和名称')
  if (form.productType === 'PET_COMPANION' && (!form.petSpecies || !form.petBreed)) return ElMessage.warning('请填写宠物类别和品种')
  if (form.productType === 'EXPERT_COMPANION' && !form.expertRole) return ElMessage.warning('请填写高手类型')
  saving.value = true
  try {
    const payload = {
      productCode: form.productCode.trim(),
      productName: form.productName.trim(),
      category: form.category,
      productType: form.productType,
      petSpecies: form.productType === 'PET_COMPANION' ? form.petSpecies : null,
      petBreed: form.productType === 'PET_COMPANION' ? form.petBreed.trim() : null,
      expertRole: form.productType === 'EXPERT_COMPANION' ? form.expertRole.trim() : null,
      serviceDurationMinutes: form.productType === 'MERCHANDISE' ? 0 : form.serviceDurationMinutes,
      description: form.description?.trim(),
      price: form.price,
      stock: form.stock,
      status: form.status,
      sortOrder: form.sortOrder
    }
    if (editingId.value) {
      await http.patch(`/products/${editingId.value}`, payload)
    } else {
      await http.post('/products', payload)
    }
    ElMessage.success('商品信息已保存')
    dialogVisible.value = false
    await loadProducts()
  } finally {
    saving.value = false
  }
}

async function deleteProduct(row: Product) {
  await ElMessageBox.confirm(`确认删除 ${row.productName} 吗？历史订单会保留已写入的商品名称。`, '删除商品', { type: 'warning' })
  await http.delete(`/products/${row.id}`)
  ElMessage.success('商品已删除')
  await loadProducts()
}

function syncCategory() {
  if (form.productType === 'PET_COMPANION') {
    form.petSpecies = form.petSpecies || 'CAT'
    syncPetCategory()
  } else if (form.productType === 'EXPERT_COMPANION') {
    form.category = 'EXPERT_PLAY'
    form.petSpecies = ''
    form.petBreed = ''
  } else {
    form.category = 'DRINK'
    form.petSpecies = ''
    form.petBreed = ''
    form.expertRole = ''
    form.serviceDurationMinutes = 0
  }
}

function syncPetCategory() {
  form.category = ({ CAT: 'PET_CAT', DOG: 'PET_DOG', REPTILE: 'PET_REPTILE' } as Record<string, string>)[form.petSpecies] || 'PET_CAT'
}

function productTypeLabel(value: string) {
  return ({ MERCHANDISE: '普通商品', PET_COMPANION: '宠物陪伴', EXPERT_COMPANION: '高手陪玩' } as Record<string, string>)[value] || value
}
function categoryLabel(value: string) {
  return ({ DRINK: '饮品', SNACK: '零食', MEAL: '简餐', PET_CAT: '猫类宠物', PET_DOG: '犬类宠物', PET_REPTILE: '爬宠陪伴', EXPERT_PLAY: '高手陪玩' } as Record<string, string>)[value] || value
}
function serviceLabel(row: Product) {
  if (row.productType === 'PET_COMPANION') return `${petSpeciesLabel(row.petSpecies)} / ${row.petBreed || '-'}`
  if (row.productType === 'EXPERT_COMPANION') return row.expertRole || '-'
  return row.description || '-'
}
function petSpeciesLabel(value?: string) {
  return ({ CAT: '猫', DOG: '狗', REPTILE: '爬宠' } as Record<string, string>)[value || ''] || '宠物'
}
function money(value: unknown) { return `¥${Number(value || 0).toFixed(2)}` }
</script>
