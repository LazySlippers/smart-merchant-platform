import { createApp } from 'vue'
async function mount() {
  if (new URLSearchParams(location.search).get('preview') === '1') {
    const { default: App } = await import('./prototype/PrototypeApp.vue')
    createApp(App).mount('#app')
  } else {
    const { default: App } = await import('./Marketplace.vue')
    createApp(App).mount('#app')
  }
}
void mount()
