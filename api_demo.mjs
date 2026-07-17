/**
 * 健康大数据平台 —— API 调用演示脚本 (Node.js)
 *   运行方式: node scripts/api_demo.mjs
 */

import {createHmac} from 'node:crypto'

// ═══════════════════════════ 配置 ═══════════════════════════
const BASE_URL = (process.argv[2] || 'http://localhost:5173').replace(/\/$/, '')
const APP_KEY = 'ak_2a20f06fedfe09f8a41b4b24335f'
const APP_SECRET = 'sk_3cd6bc91ceab48fec190e00443d1'

// ═══════════════════════════ 签名 ═══════════════════════════
function sign(secret, payload) {
    return createHmac('sha256', secret).update(payload).digest('hex')
}

function authHeaders(method, path, query = '') {
    const ts = Date.now()
    const payload = `${APP_KEY}${ts}${method.toUpperCase()}${path}${query}`
    return {
        'X-App-Key': APP_KEY,
        'X-Timestamp': String(ts),
        'X-Sign': sign(APP_SECRET, payload),
    }
}

// ═══════════════════════════ 请求 ═══════════════════════════
async function apiGet(label, path, params = {}) {
    const query = new URLSearchParams(params).toString()
    const url = query ? `${BASE_URL}${path}?${query}` : `${BASE_URL}${path}`
    const hdrs = authHeaders('GET', path, query)

    try {
        const res = await fetch(url, {headers: hdrs})
        const json = await res.json()
        print(label, res.status, json)
    } catch (e) {
        console.log(`\n  ✗  ${label}  →  请求失败: ${e.message}`)
    }
}

async function rawGet(label, path, headers = {}) {
    try {
        const res = await fetch(`${BASE_URL}${path}`, {headers})
        const json = await res.json()
        print(label, res.status, json)
    } catch (e) {
        console.log(`\n  ✗  ${label}  →  请求失败: ${e.message}`)
    }
}

// ═══════════════════════════ 输出 ═══════════════════════════
function print(label, status, data) {
    const ok = status === 200 ? '✓' : '✗'
    const body = JSON.stringify(data, null, 2)
    console.log(`\n${'─'.repeat(60)}`)
    console.log(`  ${ok}  ${label}`)
    console.log(`  HTTP ${status}`)
    console.log(`${'─'.repeat(60)}`)
    console.log(body.length > 800 ? body.slice(0, 800) + '\n  ... (已截断)' : body)
}

// ═══════════════════════════ 演示 ═══════════════════════════
console.log('='.repeat(60))
console.log('  健康大数据平台 — 外部 API 调用演示')
console.log(`  后端地址: ${BASE_URL}`)
console.log(`  AppKey:   ${APP_KEY}`)
console.log('='.repeat(60))


// . 国家药品目录查询
await apiGet('国家药品目录查询 (阿莫西林)', '/api/external/drug-catalog',
    {drugName: '阿莫西林', page: 1, size: 5})
// . 无鉴权请求 → 预期 401
await rawGet('无鉴权请求 (预期 401)', '/api/external/hospitals?page=1&size=1')

// . 错误签名 → 预期 403
await rawGet('错误签名请求 (预期 403)', '/api/external/hospitals?page=1&size=1', {
    'X-App-Key': APP_KEY,
    'X-Timestamp': String(Date.now()),
    'X-Sign': 'invalid_signature_here',
})


console.log(`\n${'═'.repeat(60)}`)
console.log('  演示完成！')
console.log('═'.repeat(60))
