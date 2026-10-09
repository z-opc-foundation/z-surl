import { Card, Col, Row, Statistic } from 'antd'

export interface SurlStatsRowProps {
  /** 短链总数 */
  total: number
  /** 命中总次数 */
  totalHits: number
  /** 已过期（或永不过期）以外的"有效"短链数 */
  active: number
  /** 7 天内将过期的短链数 */
  expiringSoon?: number
  loading?: boolean
}

/**
 * 顶部 KPI 行.
 *
 * 纯展示：所有数字由 props 传入，组件自己不算、不取数。
 */
export default function SurlStatsRow(props: SurlStatsRowProps) {
  const { total, totalHits, active, expiringSoon = 0, loading = false } = props
  return (
    <Row gutter={[16, 16]}>
      <Col xs={12} md={6}>
        <Card size="small">
          <Statistic title="短链总数" value={total} loading={loading} />
        </Card>
      </Col>
      <Col xs={12} md={6}>
        <Card size="small">
          <Statistic title="有效短链" value={active} loading={loading} />
        </Card>
      </Col>
      <Col xs={12} md={6}>
        <Card size="small">
          <Statistic title="累计命中" value={totalHits} loading={loading} />
        </Card>
      </Col>
      <Col xs={12} md={6}>
        <Card size="small">
          <Statistic title="7 天内过期" value={expiringSoon} loading={loading} />
        </Card>
      </Col>
    </Row>
  )
}
