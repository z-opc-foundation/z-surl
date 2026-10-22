import { useCallback, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Descriptions,
  Input,
  Row,
  Space,
  Typography
} from 'antd'
import { EmptyState } from '@yuku123/z-frontend-common/ui'
import {
  SurlListView,
  SurlShortenForm,
  SurlStatsRow,
  type SurlItem,
  type SurlShortenInput
} from '@fc'
import { buildShortUrl, info, invalidate, shorten, SurlApiError } from './api'

const { Text, Paragraph } = Typography

/**
 * z-surl 控制台（应用层）。
 *
 * 【关于"列表"的重要说明】
 * 后端目前**没有**列表接口 —— 只有 shorten / info / invalidate / resolve 四个端点，
 * 组织现有的 z-opc SurlManagement 控制台同样没有（它的 api.js 里也只有这四个）。
 * 所以本页的表格是**本次会话创建**的短链，不是全量库存。
 * 表格标题写明了这一点，避免有人把它误当成管理后台的完整清单。
 * 要做真·全量列表，需要后端补一个分页接口（PaginationInnerInterceptor 已经装好了），
 * 已记入 _doc/001_arch/02-feature.md 的待办。
 */
export default function App() {
  const { message } = AntdApp.useApp()
  const [sessionItems, setSessionItems] = useState<SurlItem[]>([])
  const [creating, setCreating] = useState(false)
  const [lookupKey, setLookupKey] = useState('')
  const [lookupBusy, setLookupBusy] = useState(false)
  const [lookupResult, setLookupResult] = useState<SurlItem | null>(null)

  const stats = useMemo(() => {
    const now = Date.now()
    let totalHits = 0
    let active = 0
    let expiringSoon = 0
    for (const it of sessionItems) {
      totalHits += it.hits ?? 0
      const ts = it.expireAt ? Date.parse(it.expireAt) : NaN
      const expired = Number.isFinite(ts) && ts <= now
      if (!expired) {
        active += 1
        if (Number.isFinite(ts) && ts - now <= 7 * 24 * 3600 * 1000) {
          expiringSoon += 1
        }
      }
    }
    return { total: sessionItems.length, totalHits, active, expiringSoon }
  }, [sessionItems])

  const handleCreate = useCallback(
    async (input: SurlShortenInput) => {
      setCreating(true)
      try {
        const res = await shorten(input)
        // 生成接口只回 4 个字段，列表展示要 createdAt/tenantCode/remark，
        // 所以再查一次 info 补全 —— 少一次往返 vs 少一个字段，这里选字段完整。
        let full: SurlItem = {
          mapKey: res.mapKey,
          originUrl: res.originUrl,
          expireAt: res.expireAt,
          hits: res.hits,
          tenantCode: input.tenantCode ?? null,
          remark: input.remark ?? null
        }
        try {
          full = await info(res.mapKey)
        } catch {
          // 补全失败不阻断：短码已经生效了，只是这张卡片字段少几个
        }
        setSessionItems((prev) => [full, ...prev.filter((p) => p.mapKey !== full.mapKey)])
        message.success(`短链已生成：${buildShortUrl(res.mapKey)}`)
      } catch (e) {
        const msg = e instanceof SurlApiError ? e.message : '生成失败，请稍后重试'
        message.error(msg)
        throw e // 抛回去让表单保留用户输入
      } finally {
        setCreating(false)
      }
    },
    [message]
  )

  const handleLookup = useCallback(async () => {
    const key = lookupKey.trim()
    if (!key) {
      message.warning('请输入短码')
      return
    }
    setLookupBusy(true)
    setLookupResult(null)
    try {
      setLookupResult(await info(key))
    } catch (e) {
      const msg = e instanceof SurlApiError && e.code === 404 ? '短码不存在或已失效' : '查询失败'
      message.error(msg)
    } finally {
      setLookupBusy(false)
    }
  }, [lookupKey, message])

  const handleInvalidate = useCallback(
    async (mapKey: string) => {
      try {
        await invalidate(mapKey)
        setSessionItems((prev) => prev.filter((p) => p.mapKey !== mapKey))
        setLookupResult((prev) => (prev && prev.mapKey === mapKey ? null : prev))
        message.success(`已失效：${mapKey}`)
      } catch (e) {
        const msg = e instanceof SurlApiError && e.code === 404 ? '短码不存在或已失效' : '失效失败'
        message.error(msg)
      }
    },
    [message]
  )

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto', padding: 24 }}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div>
          <Text strong style={{ fontSize: 22 }}>
            z-surl 控制台
          </Text>
          <Paragraph type="secondary" style={{ margin: '4px 0 0' }}>
            短网址服务 · 短码生成 / 解析 / 命中统计 / 失效
          </Paragraph>
        </div>

        <SurlStatsRow {...stats} loading={creating} />

        <Row gutter={[16, 16]}>
          <Col xs={24} lg={10}>
            <Card title="生成短链" size="small">
              <SurlShortenForm onSubmit={handleCreate} loading={creating} />
            </Card>
          </Col>
          <Col xs={24} lg={14}>
            <Card title="按短码查询" size="small">
              <Space.Compact style={{ width: '100%' }}>
                <Input
                  placeholder="输入短码，如 Ab12Cd34"
                  value={lookupKey}
                  onChange={(e) => setLookupKey(e.target.value)}
                  onPressEnter={handleLookup}
                />
                <Button type="primary" loading={lookupBusy} onClick={handleLookup}>
                  查询
                </Button>
              </Space.Compact>
              {lookupResult && (
                <Descriptions
                  size="small"
                  column={1}
                  bordered
                  style={{ marginTop: 12 }}
                  items={[
                    { key: 'k', label: '短码', children: lookupResult.mapKey },
                    {
                      key: 'u',
                      label: '原始链接',
                      children: (
                        <Text copyable ellipsis style={{ maxWidth: 420 }}>
                          {lookupResult.originUrl}
                        </Text>
                      )
                    },
                    { key: 'h', label: '命中', children: lookupResult.hits ?? 0 },
                    {
                      key: 'e',
                      label: '有效期',
                      children: lookupResult.expireAt ?? '永不过期'
                    },
                    { key: 't', label: '租户', children: lookupResult.tenantCode ?? '公共' },
                    { key: 'c', label: '创建时间', children: lookupResult.createdAt ?? '-' },
                    { key: 'by', label: '创建人', children: lookupResult.createdBy ?? '-' },
                    { key: 'r', label: '备注', children: lookupResult.remark ?? '-' }
                  ]}
                />
              )}
            </Card>
          </Col>
        </Row>

        <Card
          title="本次会话创建的短链"
          size="small"
          extra={
            <Text type="secondary" style={{ fontSize: 12 }}>
              非全量库存：后端暂无列表接口，刷新页面即清空
            </Text>
          }
        >
          {sessionItems.length === 0 ? (
            // 空态走共享 EmptyState：与列表页的空态文案/视觉一致（lead 008 全仓统一）
            <EmptyState
              title="还没有创建过短链"
              description="上面生成短链后会自动出现在这里，可复制链接或一键失效。"
              size="sm"
            />
          ) : (
            <SurlListView
              items={sessionItems}
              loading={creating}
              buildShortUrl={buildShortUrl}
              onInvalidate={handleInvalidate}
            />
          )}
        </Card>

        <Text type="secondary" style={{ fontSize: 12 }}>
          跳转面在 <Text code>/s/&#123;短码&#125;</Text>
          ，不经 /api 前缀；管理面在 <Text code>/api/ctc/surl/**</Text>。
        </Text>
      </Space>
    </div>
  )
}
