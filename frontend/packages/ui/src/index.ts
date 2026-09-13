import { defineComponent, h } from 'vue'

export const appLabels = {
  platform: '超级运营平台',
  merchant: '商户经营后台',
  store: '门店工作台'
} as const

export type AppKind = keyof typeof appLabels

export function createAppShell(kind: AppKind) {
  return defineComponent({
    name: 'AppShell',
    setup: () => () => h('main', { class: `app-shell app-shell--${kind}` }, [
      h('p', { class: 'app-shell__eyebrow' }, 'SMART MERCHANT SaaS'),
      h('h1', appLabels[kind]),
      h('p', { class: 'app-shell__status' }, '系统服务运行正常')
    ])
  })
}
