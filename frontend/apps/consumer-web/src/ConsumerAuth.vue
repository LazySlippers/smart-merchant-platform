<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError } from './api'
import { account, authenticateAccount, enterTenant } from './account'
const props = defineProps<{ tenantId?: string; addAccount?: boolean; joinMembership?: boolean }>()
const emit = defineEmits<{ close: []; authenticated: [token: string, member: boolean] }>()
const register = ref(false), mobile = ref(''), name = ref(''), password = ref(''), legacyPassword = ref('')
const busy = ref(false), error = ref(''), linking = ref(false)
const enteringCredentials=ref(props.addAccount||!account.value)
async function finish() {
  try { const session=props.tenantId ? await enterTenant(props.tenantId, linking.value ? legacyPassword.value : undefined, !!props.joinMembership) : null;emit('authenticated',session?.accessToken||'',session?.member||false) }
  catch (e) {
    if (e instanceof ApiError && e.status === 409) linking.value = true
    throw e
  } finally { legacyPassword.value = '' }
}
async function submit() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    if (enteringCredentials.value){await authenticateAccount(register.value, mobile.value, password.value, name.value.trim());enteringCredentials.value=false}
    password.value = ''
    await finish()
  } catch (e) { error.value = (e as Error).message } finally { busy.value = false }
}
function switchAccount() { enteringCredentials.value=true; linking.value = false; error.value = '' }
onMounted(() => { if (account.value&&!enteringCredentials.value) void submit() })
</script>
<template>
  <div class="market-overlay" @click.self="!busy&&emit('close')"><section class="market-location-sheet consumer-auth" role="dialog" aria-modal="true" aria-label="消费者登录">
    <div class="market-heading"><h2>{{ linking ? '关联已有商家会员' : register ? '注册统一消费者账号' : '登录邻里百货' }}</h2><button :disabled="busy" aria-label="关闭登录" @click="emit('close')">×</button></div>
    <p>一次登录，通逛各商家。订单、积分、余额和优惠券归各商家独立管理。</p>
    <form @submit.prevent="submit">
      <template v-if="enteringCredentials"><label>手机号<input v-model="mobile" type="tel" autocomplete="username" pattern="1[3-9][0-9]{9}" maxlength="11" required></label><label v-if="register">称呼<input v-model="name" autocomplete="name" maxlength="40" required></label><label>密码<input v-model="password" type="password" :autocomplete="register?'new-password':'current-password'" :minlength="register?10:1" maxlength="64" required></label><p v-if="register">手机号仅作账号标识；已有商家资产须验证原商家密码后关联。</p></template>
      <template v-else-if="linking"><p>请输入该手机号对应的原商家账号密码。没有原密码的历史会员请联系商家核实。</p><label>原商家密码<input v-model="legacyPassword" type="password" autocomplete="current-password" maxlength="64" required></label></template>
      <p v-if="error" class="market-error" role="alert">{{ error }}</p>
      <button class="market-locate-button" :disabled="busy">{{ busy?'请稍候…':linking?'验证并关联':!enteringCredentials?(joinMembership?'加入商家会员':'继续'):register?'注册并登录':'登录' }}</button>
      <button v-if="enteringCredentials" type="button" @click="register=!register;error=''">{{ register?'已有统一账号，去登录':'首次使用或原商家账号？注册统一账号' }}</button>
      <button v-else type="button" @click="switchAccount">切换统一账号</button>
    </form>
  </section></div>
</template>
<style scoped>
.consumer-auth { max-height: 90dvh; overflow-y: auto; }
.consumer-auth p { line-height: 1.7; color: #68726b; }
.consumer-auth label { display: grid; gap: 8px; margin: 14px 0; }
.consumer-auth input { width: 100%; box-sizing: border-box; border: 1px solid #d8dfda; border-radius: 10px; padding: 12px; font-size: 16px; }
.consumer-auth form>button { margin: 8px 0; }
</style>
