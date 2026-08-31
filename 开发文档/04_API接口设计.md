# API 接口设计

## 1. 通用约定

### 1.1 基础路径

```text
/api/v1
```

### 1.2 认证方式

- 登录接口返回 JWT。
- 前端请求在 Header 中携带：

```text
Authorization: Bearer <token>
```

### 1.3 统一响应

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "202608310001"
}
```

错误响应：

```json
{
  "code": 40001,
  "message": "会员余额不足",
  "data": null,
  "traceId": "202608310002"
}
```

### 1.4 分页响应

```json
{
  "records": [],
  "page": 1,
  "pageSize": 20,
  "total": 100
}
```

## 2. 系统管理接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/login` | 登录 |
| POST | `/auth/logout` | 退出登录 |
| GET | `/auth/profile` | 当前用户信息 |
| GET | `/users` | 用户列表 |
| POST | `/users` | 创建用户 |
| PUT | `/users/{id}` | 更新用户 |
| PATCH | `/users/{id}/status` | 启用或停用用户 |
| POST | `/users/{id}/reset-password` | 重置密码 |
| GET | `/roles` | 角色列表 |
| POST | `/roles` | 创建角色 |
| PUT | `/roles/{id}` | 更新角色 |

登录请求：

```json
{
  "username": "admin",
  "password": "123456"
}
```

## 3. 会员管理接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/members` | 会员分页查询 |
| POST | `/members` | 注册会员 |
| GET | `/members/{id}` | 会员详情 |
| PUT | `/members/{id}` | 更新会员 |
| PATCH | `/members/{id}/status` | 冻结或解冻会员 |
| GET | `/members/{id}/account` | 账户余额 |
| POST | `/members/{id}/recharge` | 会员充值 |
| GET | `/members/{id}/recharges` | 充值记录 |
| GET | `/members/{id}/consumes` | 消费记录 |

注册会员请求：

```json
{
  "name": "张三",
  "phone": "13800000000",
  "idCardNo": "加密前由后端处理",
  "level": "NORMAL"
}
```

充值请求：

```json
{
  "amount": 100,
  "giftAmount": 10,
  "payMethod": "CASH",
  "remark": "新会员充值"
}
```

## 4. 设备管理接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/devices` | 设备列表 |
| POST | `/devices` | 新增设备 |
| GET | `/devices/{id}` | 设备详情 |
| PUT | `/devices/{id}` | 更新设备 |
| PATCH | `/devices/{id}/status` | 修改设备状态 |
| POST | `/devices/{id}/faults` | 登记故障 |
| GET | `/devices/{id}/faults` | 故障记录 |
| POST | `/faults/{id}/repairs` | 填写维修记录 |

## 5. 上机计费接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/billing/rules` | 计费规则列表 |
| POST | `/billing/rules` | 创建计费规则 |
| PUT | `/billing/rules/{id}` | 更新计费规则 |
| GET | `/sessions/running` | 当前上机会话 |
| POST | `/sessions/start` | 开始上机 |
| POST | `/sessions/{id}/renew` | 续费 |
| POST | `/sessions/{id}/settle` | 下机结算 |
| POST | `/sessions/{id}/force-end` | 强制下机 |
| GET | `/sessions/{id}` | 会话详情 |
| GET | `/sessions` | 上机记录查询 |

开始上机请求：

```json
{
  "memberId": 1,
  "deviceId": 10,
  "billingRuleId": 1,
  "faceVerifyToken": "可选的人脸验证凭证"
}
```

下机结算响应：

```json
{
  "sessionId": 1001,
  "durationMinutes": 126,
  "finalAmount": 21.00,
  "balanceAfter": 89.00,
  "deviceStatus": "IDLE"
}
```

## 6. 人脸认证接口

后端主服务接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/faces/members/{memberId}/enroll` | 会员人脸录入 |
| POST | `/faces/members/{memberId}/verify` | 指定会员人脸验证 |
| POST | `/faces/identify` | 通过图片识别会员 |
| GET | `/faces/members/{memberId}` | 人脸档案状态 |
| DELETE | `/faces/members/{memberId}` | 删除或停用人脸档案 |

后端调用人脸服务接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/face-service/enroll` | 提取并保存特征 |
| POST | `/face-service/verify` | 指定特征比对 |
| POST | `/face-service/identify` | 特征库检索 |
| GET | `/face-service/health` | 健康检查 |

## 7. 数据统计接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/statistics/dashboard` | 首页看板 |
| GET | `/statistics/business` | 营业统计 |
| GET | `/statistics/sessions` | 上机统计 |
| GET | `/statistics/recharges` | 充值统计 |
| GET | `/statistics/consumes` | 消费统计 |
| GET | `/statistics/devices/usage` | 设备使用率 |

## 8. 桌宠接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/clients/register` | 客户机注册或绑定 |
| POST | `/clients/{deviceCode}/heartbeat` | 客户机心跳 |
| GET | `/clients/{deviceCode}/session` | 当前机位上机状态 |

WebSocket 消息类型：

| 类型 | 说明 |
| --- | --- |
| `SESSION_STARTED` | 上机开始 |
| `SESSION_UPDATED` | 计费状态更新 |
| `LOW_BALANCE` | 余额不足提醒 |
| `REST_REMINDER` | 休息提醒 |
| `SESSION_ENDED` | 下机完成 |
| `DEVICE_LOCKED` | 设备锁定 |

## 9. 错误码建议

| 错误码 | 说明 |
| --- | --- |
| 0 | 成功 |
| 40000 | 请求参数错误 |
| 40001 | 会员余额不足 |
| 40002 | 会员状态不可用 |
| 40003 | 设备状态不可用 |
| 40004 | 上机会话不存在 |
| 40005 | 上机会话已结束 |
| 40006 | 人脸验证失败 |
| 40100 | 未登录或令牌失效 |
| 40300 | 无权限 |
| 50000 | 系统异常 |
| 50010 | 人脸服务不可用 |
