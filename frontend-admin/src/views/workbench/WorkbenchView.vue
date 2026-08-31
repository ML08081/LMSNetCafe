<template>
  <section>
    <h1 class="page-title">前台工作台</h1>
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>快速开机</template>
          <el-form label-width="96px">
            <el-form-item label="会员编号">
              <el-input placeholder="输入会员编号或手机号" />
            </el-form-item>
            <el-form-item label="机位">
              <el-select placeholder="选择空闲机位" style="width: 100%" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary">开始上机</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>提醒</template>
          <el-empty description="暂无低余额或故障提醒" />
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>
