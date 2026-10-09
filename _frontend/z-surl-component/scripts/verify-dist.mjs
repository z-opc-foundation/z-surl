/**
 * 产物自检：确认 dist 里有"能用的" JS 与类型声明。
 *
 * 【为什么必须显式断言，而不是"构建成功就当有 d.ts"】
 * 踩坑实录：build 脚本曾写成 `tsc -p tsconfig.build.json && vite build` ——
 * tsc 先把 .d.ts 吐进 dist/，紧接着 vite build 因 emptyOutDir: true **把 dist 清空**
 * 再只写 index.js。构建全程绿灯、退出码 0，但发布的包里**没有类型声明**，
 * 消费方 TS 报 "Could not find a declaration file"，而这个错误在发布侧完全看不见。
 * 也就是说：d.ts 缺失是一个"构建成功但交付物残缺"的静默失败，只能靠断言拦。
 *
 * 检查项：
 *   1. dist/index.js 存在且非空
 *   2. dist/index.d.ts 存在且非空
 *   3. d.ts 里能查到对外导出的关键符号（防止只声明了一部分）
 *   4. index.js 里 React/antd 没被打进产物（external 生效的粗验证）
 */
import { readFileSync, existsSync, statSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const dist = resolve(here, '..', 'dist')

const REQUIRED_EXPORTS = ['SurlListView', 'SurlShortenForm', 'SurlStatsRow', 'SURL_TTL_PRESETS']
const PEER_DEPS = ['react', 'antd']

const failures = []

function requireFile(rel, label) {
  const p = resolve(dist, rel)
  if (!existsSync(p)) {
    failures.push(`缺少 ${label}：dist/${rel} 不存在`)
    return null
  }
  if (statSync(p).size === 0) {
    failures.push(`${label} 为空：dist/${rel}`)
    return null
  }
  return p
}

const jsPath = requireFile('index.js', 'JS 产物')
const dtsPath = requireFile('index.d.ts', '类型声明')

if (dtsPath) {
  const dts = readFileSync(dtsPath, 'utf8')
  const missing = REQUIRED_EXPORTS.filter((n) => !new RegExp(`\\b${n}\\b`).test(dts))
  if (missing.length) {
    failures.push(`dist/index.d.ts 缺少导出：${missing.join(', ')}`)
  }
}

if (jsPath) {
  const js = readFileSync(jsPath, 'utf8')
  // 粗验证：external 生效时产物里应该出现 from 'antd' / from "react" 这类裸 import
  const notExternalised = PEER_DEPS.filter(
    (dep) => !new RegExp(`from\\s*['"]${dep}`).test(js)
  )
  if (notExternalised.length) {
    failures.push(
      `peerDependencies 没被 external 化（会被打进产物，重复 React 运行时）：${notExternalised.join(', ')}`
    )
  }
}

if (failures.length) {
  console.error('FAIL: 产物自检未通过')
  for (const f of failures) console.error('  - ' + f)
  process.exit(1)
}

const size = (statSync(jsPath).size / 1024).toFixed(2)
console.log(`PASS: 产物自检通过（index.js ${size} kB + index.d.ts，${REQUIRED_EXPORTS.length} 个导出齐全）`)
