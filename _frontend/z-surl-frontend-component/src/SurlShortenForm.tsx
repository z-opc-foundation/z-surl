import { Button, Form, Input, Select, Space } from 'antd'
import { SURL_TTL_PRESETS, type SurlShortenInput } from './types'

export interface SurlShortenFormProps {
  /** 提交回调。抛异常即视为失败（父层负责提示） */
  onSubmit: (input: SurlShortenInput) => Promise<void> | void
  loading?: boolean
}

interface FormValues {
  originUrl: string
  tenantCode?: string
  /** 预设档位，0 = 永不过期 */
  ttlPreset: number
  remark?: string
}

/**
 * 生成短码表单.
 *
 * 提交成功后**由父层决定要不要清空表单**：这里只在成功后 reset，
 * 失败时保留用户输入 —— 清掉的话用户得重新敲一遍长 URL，这是最招人烦的一类交互。
 */
export default function SurlShortenForm({ onSubmit, loading = false }: SurlShortenFormProps) {
  const [form] = Form.useForm<FormValues>()

  const handleFinish = async (values: FormValues) => {
    const input: SurlShortenInput = {
      originUrl: values.originUrl.trim(),
      ttlMillis: values.ttlPreset
    }
    if (values.tenantCode?.trim()) {
      input.tenantCode = values.tenantCode.trim()
    }
    if (values.remark?.trim()) {
      input.remark = values.remark.trim()
    }
    try {
      await onSubmit(input)
      form.resetFields()
    } catch {
      // 失败时保留输入，错误提示交给父层（它才知道该用 message 还是别的载体）
    }
  }

  return (
    <Form<FormValues>
      form={form}
      layout="vertical"
      onFinish={handleFinish}
      initialValues={{ ttlPreset: 0 }}
      disabled={loading}
    >
      <Form.Item
        name="originUrl"
        label="原始链接"
        rules={[
          { required: true, message: '请输入要缩短的链接' },
          { max: 2048, message: '长度不超过 2048' },
          {
            validator: (_rule, value: string) => {
              if (!value) return Promise.resolve()
              // 后端只校验非空，不校验 URL 合法性（它不解析 URL），
              // 所以格式校验放在前端，省一次无谓的往返
              try {
                new URL(value.trim())
                return Promise.resolve()
              } catch {
                return Promise.reject(new Error('不是合法的 URL（需含 http:// 或 https://）'))
              }
            }
          }
        ]}
      >
        <Input placeholder="https://example.com/a/very/long/path" allowClear />
      </Form.Item>

      <Space.Compact block style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        <Form.Item name="tenantCode" label="租户（可空）" style={{ flex: '1 1 220px', marginBottom: 16 }}>
          <Input placeholder="留空 = 公共链接" allowClear />
        </Form.Item>
        <Form.Item name="ttlPreset" label="有效期" style={{ flex: '0 1 180px', marginBottom: 16 }}>
          <Select options={SURL_TTL_PRESETS.map((p) => ({ label: p.label, value: p.value }))} />
        </Form.Item>
        <Form.Item name="remark" label="备注（可空）" style={{ flex: '1 1 220px', marginBottom: 16 }}>
          <Input placeholder="用途说明" allowClear />
        </Form.Item>
      </Space.Compact>

      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading}>
          生成短链
        </Button>
      </Form.Item>
    </Form>
  )
}
