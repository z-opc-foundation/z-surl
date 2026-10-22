import { Button, Popconfirm, Space, Table, Tag, Tooltip, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { EmptyState, RefreshButton } from '@yuku123/z-frontend-common/ui'
import type { SurlItem } from './types'

export interface SurlListViewProps {
  items: SurlItem[]
  loading?: boolean
  /** 短链前缀，例如 `${location.origin}`。传了才显示"短链"列 */
  shortBaseUrl?: string
  /** 跳转变换：默认 `${shortBaseUrl}/s/${encodeURIComponent(mapKey)}` */
  buildShortUrl?: (mapKey: string) => string
  onInvalidate?: (mapKey: string) => Promise<void> | void
  onReload?: () => void
  pagination?: false | { pageSize?: number }
}

const { Text } = Typography

/**
 * 短链列表.
 *
 * 只读表格 + 两个动作回调。**不自己取数**（005 规范 §5）：
 * 消费方传 items进来，接 z-surl-admin、接 z-opc、接 mock 都行。
 */
export default function SurlListView(props: SurlListViewProps) {
  const {
    items,
    loading = false,
    shortBaseUrl,
    buildShortUrl,
    onInvalidate,
    onReload,
    pagination = { pageSize: 10 }
  } = props

  const resolveShortUrl = (mapKey: string): string => {
    if (buildShortUrl) return buildShortUrl(mapKey)
    const base = shortBaseUrl ?? ''
    return `${base}/s/${encodeURIComponent(mapKey)}`
  }

  const isExpired = (item: SurlItem): boolean => {
    if (!item.expireAt) return false
    const ts = Date.parse(item.expireAt)
    return Number.isFinite(ts) && ts <= Date.now()
  }

  const columns: ColumnsType<SurlItem> = [
    {
      title: '短链',
      dataIndex: 'mapKey',
      key: 'mapKey',
      width: 300,
      render: (_v, item) => {
        const url = resolveShortUrl(item.mapKey)
        return (
          <Tooltip title={url}>
            {/* copyable 让用户一键复制；不 copyable 的话短链只能靠肉眼手抄 */}
            <Text copyable={{ text: url }} ellipsis style={{ maxWidth: 280 }}>
              {url}
            </Text>
          </Tooltip>
        )
      }
    },
    {
      title: '原始链接',
      dataIndex: 'originUrl',
      key: 'originUrl',
      ellipsis: true,
      render: (v: string) => (
        <Tooltip title={v}>
          <Text ellipsis style={{ maxWidth: 320 }}>
            {v}
          </Text>
        </Tooltip>
      )
    },
    {
      title: '命中',
      dataIndex: 'hits',
      key: 'hits',
      width: 88,
      align: 'right',
      render: (v?: number | null) => v ?? 0
    },
    {
      title: '有效期',
      dataIndex: 'expireAt',
      key: 'expireAt',
      width: 200,
      // 注意: 形参不能写成 `(v?: string | null, item) => {}` —— 可选参数后面跟必填参数
      // 是 TS1016 语法错误。用必填形参 + 联合类型表达同样的可空语义。
      render: (v: string | null | undefined, item) => {
        if (!v) return <Tag color="default">永不过期</Tag>
        if (isExpired(item)) return <Tag color="error">已过期</Tag>
        return <Tag color="processing">{v}</Tag>
      }
    },
    {
      title: '租户',
      dataIndex: 'tenantCode',
      key: 'tenantCode',
      width: 120,
      render: (v?: string | null) => (v ? <Tag>{v}</Tag> : <Tag color="default">公共</Tag>)
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_v, item) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            onClick={() => {
              window.open(resolveShortUrl(item.mapKey), '_blank', 'noopener')
            }}
          >
            打开
          </Button>
          {onInvalidate && (
            <Popconfirm
              title="失效这条短链？"
              description="失效后访问立即 404，已发出的链接无法回收。"
              okText="失效"
              cancelText="取消"
              okButtonProps={{ danger: true }}
              onConfirm={() => onInvalidate(item.mapKey)}
            >
              <Button type="link" size="small" danger>
                失效
              </Button>
            </Popconfirm>
          )}
        </Space>
      )
    }
  ]

  return (
    <>
      {onReload && (
        <div style={{ marginBottom: 12, textAlign: 'right' }}>
          <RefreshButton onClick={onReload} loading={loading} />
        </div>
      )}
      <Table<SurlItem>
        rowKey={(r) => r.mapKey}
        columns={columns}
        dataSource={items}
        loading={loading}
        pagination={pagination}
        size="middle"
        scroll={{ x: 1000 }}
        locale={{ emptyText: <EmptyState title="暂无短链" description="生成短链后会出现在这里" size="sm" /> }}
      />
    </>
  )
}
