<template>
  <div class="camera-capture">
    <div class="camera-stage">
      <video v-show="streamActive && !previewUrl" ref="video" autoplay muted playsinline />
      <img v-if="previewUrl" :src="previewUrl" alt="摄像头采集预览" />
      <div v-if="!streamActive && !previewUrl" class="camera-empty">
        <el-icon><Camera /></el-icon>
        <strong>摄像头尚未启用</strong>
        <span>请正对镜头，并确保画面中只有一人</span>
      </div>
      <div v-if="streamActive && !previewUrl" class="face-guide" aria-hidden="true" />
    </div>
    <canvas ref="canvas" hidden />
    <div class="camera-actions">
      <el-button v-if="!streamActive" :disabled="disabled" :icon="VideoCamera" @click="startCamera">启用摄像头</el-button>
      <el-button v-if="streamActive && !previewUrl" :disabled="disabled" type="primary" :icon="Camera" @click="capture">
        {{ actionLabel }}
      </el-button>
      <el-button v-if="previewUrl" :icon="RefreshLeft" @click="retake">重新拍摄</el-button>
    </div>
    <label class="camera-upload">
      也可选择本人照片进行验证（照片验证不代表活体检测）
      <input type="file" accept="image/jpeg,image/png" :disabled="disabled" @change="choosePhoto" />
    </label>
    <p v-if="disabled">正在处理，请稍候…</p>
    <p v-if="errorMessage" class="camera-error">{{ errorMessage }}</p>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue'
import { Camera, RefreshLeft, VideoCamera } from '@element-plus/icons-vue'

withDefaults(defineProps<{ actionLabel?: string; disabled?: boolean }>(), {
  actionLabel: '拍照', disabled: false
})

const emit = defineEmits<{ captured: [image: Blob] }>()
const video = ref<HTMLVideoElement>()
const canvas = ref<HTMLCanvasElement>()
const streamActive = ref(false)
const previewUrl = ref('')
const errorMessage = ref('')
let mediaStream: MediaStream | null = null

async function startCamera() {
  errorMessage.value = ''
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    errorMessage.value = '当前地址无法使用摄像头。请在本机使用 http://localhost:5173，或通过 localhost 前端代理连接局域网主机；远程访问需要 HTTPS。'
    return
  }
  try {
    mediaStream?.getTracks().forEach((track) => track.stop())
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: { ideal: 960 }, height: { ideal: 720 } },
      audio: false
    })
    streamActive.value = true
    await nextTick()
    if (video.value) {
      video.value.srcObject = mediaStream
      await video.value.play()
    }
    streamActive.value = true
  } catch (error: any) {
    stopCamera()
    errorMessage.value = error?.name === 'NotFoundError' ? '未找到摄像头，请连接摄像头或选择本人照片。'
      : error?.name === 'NotReadableError' ? '摄像头被其他程序占用，请关闭会议或相机软件后重试。'
      : '无法访问摄像头，请在浏览器地址栏允许摄像头权限后重试。'
  }
}

function capture() {
  if (!video.value || !canvas.value || video.value.videoWidth === 0) {
    errorMessage.value = '摄像头画面尚未准备好，请稍后重试。'
    return
  }
  const targetWidth = Math.min(960, video.value.videoWidth)
  const targetHeight = Math.round(targetWidth * video.value.videoHeight / video.value.videoWidth)
  canvas.value.width = targetWidth
  canvas.value.height = targetHeight
  canvas.value.getContext('2d')?.drawImage(video.value, 0, 0, targetWidth, targetHeight)
  canvas.value.toBlob((blob) => {
    if (!blob) {
      errorMessage.value = '照片采集失败，请重新拍摄。'
      return
    }
    revokePreview()
    previewUrl.value = URL.createObjectURL(blob)
    emit('captured', blob)
  }, 'image/jpeg', 0.9)
}

function choosePhoto(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
    errorMessage.value = '请选择不超过 5MB 的 JPG 或 PNG 照片'
    return
  }
  errorMessage.value = ''
  revokePreview()
  previewUrl.value = URL.createObjectURL(file)
  emit('captured', file)
}

function retake() {
  revokePreview()
  errorMessage.value = ''
}

function revokePreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

function stopCamera() {
  mediaStream?.getTracks().forEach((track) => track.stop())
  mediaStream = null
  streamActive.value = false
  revokePreview()
}

onBeforeUnmount(stopCamera)
</script>
