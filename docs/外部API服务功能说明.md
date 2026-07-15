# 外部 API 服务功能说明

## 概述

平台在原有门户 API 基础上，新增了面向第三方应用的 **外部 API 服务**。外部开发者可通过申请的 AppKey
调用平台真实数据接口，包括全国医院目录、三级公立医院名单、复旦医院等级分档、国家药品目录等数据集。
外部 API 采用业界标准的 **AppKey + HMAC-SHA256 签名** 鉴权方式
---

## 核心流程

```
科研人员申请 → 管理员审批 → 获取密钥 → 签名调用
```

1. **申请密钥** — 已通过科研人员认证的用户在"我的 API 密钥"页面提交申请，填写应用名称和套餐等级
2. **审批启用** — 管理员在管理后台"API 密钥审核"页面审批，通过后密钥生效
3. **获取凭证** — 申请通过后获得 AppKey（公钥）和 AppSecret（私钥），AppSecret 仅在创建和重置时展示一次
4. **签名调用** — 调用方使用 AppSecret 对请求进行 HMAC-SHA256 签名，在 Header 中传递鉴权信息

---

## 鉴权方式

每次请求需在 HTTP Header 中传递三个字段：

| Header 字段     | 说明                                |
|---------------|-----------------------------------|
| `X-App-Key`   | 申请到的 AppKey（公钥），用于标识调用方身份         |
| `X-Timestamp` | 当前 Unix 毫秒时间戳，服务端允许 ±5 分钟偏差，用于防重放 |
| `X-Sign`      | HMAC-SHA256 签名，用于身份验证和防篡改         |

**签名原文构造规则：**

```
签名原文 = AppKey + 时间戳 + HTTP方法(大写) + 请求路径 + 查询字符串 [+ 请求体]
```

**签名计算方式：**

```
X-Sign = Hex(HMAC-SHA256(AppSecret, 签名原文))
```

## 管理功能

管理员可在后台对已生成的密钥进行全生命周期管理：

| 操作    | 说明                     |
|-------|------------------------|
| 审批    | 审核科研人员的密钥申请，通过后密钥启用    |
| 启用/禁用 | 立即生效，禁用后调用被拒绝          |
| 重置密钥  | 重新生成 AppSecret，原密钥立即失效 |
| 调整配额  | 修改每日调用上限和 QPS 限制       |

---

## 使用示例

**Node.js 调用示例：**

```javascript
const crypto = require('crypto');

const appKey    = 'ak_您申请的AppKey';
const appSecret = 'sk_您申请的AppSecret';
const timestamp = Date.now();

// 构造签名原文
const signPayload = appKey + timestamp + 'GET' +
  '/api/external/hospitals' + 'province=湖南省&page=1&size=10';

// 计算签名
const sign = crypto
  .createHmac('sha256', appSecret)
  .update(signPayload)
  .digest('hex');

// 发起请求
fetch('http://平台地址/api/external/hospitals?province=湖南省&page=1&size=10', {
  headers: {
    'X-App-Key': appKey,
    'X-Timestamp': timestamp,
    'X-Sign': sign
  }
});
```
