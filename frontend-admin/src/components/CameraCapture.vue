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
      <el-button v-if="!streamActive" :icon="VideoCamera" @click="startCamera">启用摄像头</el-button>
      <el-button v-if="streamActive && !previewUrl" type="primary" :icon="Camera" @click="capture">
        {{ actionLabel }}
      </el-button>
      <el-button v-if="previewUrl" :icon="RefreshLeft" @click="retake">重新拍摄</el-button>
    </div>
    <p v-if="errorMessage" class="camera-error">{{ errorMessage }}</p>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { Camera, RefreshLeft, VideoCamera } from '@element-plus/icons-vue'

withDefaults(defineProps<{ actionLabel?: string }>(), {
  actionLabel: '拍照'
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
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: { ideal: 960 }, height: { ideal: 720 } },
      audio: false
    })
    if (video.value) {
      video.value.srcObject = mediaStream
      await video.value.play()
    }
    streamActive.value = true
  } catch {
    errorMessage.value = '无法访问摄像头，请在浏览器地址栏允许摄像头权限后重试。'
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
