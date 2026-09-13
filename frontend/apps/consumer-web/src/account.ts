import { computed, ref } from 'vue'
import { ApiError, request } from './api'
import { readSaved, save } from './domain'

export type Account = { accessToken: string; expiresIn: number; mobile: string; displayName: string; lastUsedAt: number }
export type Membership = { tenantId: string; memberId: string; memberName: string; status: string }
export type GlobalAddress = { id?: string; name: string; phone: string; province: string; city: string; district: string; detail: string; label: string; isDefault: boolean }
export type BrowseItem = { tenantId:string;storeId:string;skuId:string;merchantName:string;storeName:string;productName:string;skuName:string;imageUrl:string;priceCents:number;viewedAt:string }
const vaultKey='consumer.accounts.v2',activeKey='consumer.account.active'
const legacy=readSaved<{accessToken:string;expiresIn:number;mobile:string;displayName:string}|null>('consumer.account',null,sessionStorage)
export const accounts=ref<Account[]>(readSaved<Account[]>(vaultKey,legacy?[{...legacy,lastUsedAt:Date.now()}]:[],sessionStorage))
const activeMobile=ref(readSaved<string>(activeKey,legacy?.mobile||'',sessionStorage))
export const account=computed(()=>accounts.value.find(item=>item.mobile===activeMobile.value)??null)
export const REVERIFY_AFTER_MS=30*60*1000
function persist(){save(vaultKey,accounts.value,sessionStorage);save(activeKey,activeMobile.value,sessionStorage);save('consumer.account',null,sessionStorage)}
function clearLegacy(){try{for(const name of Object.keys(sessionStorage))if(name.startsWith('consumer.session:'))sessionStorage.removeItem(name)}catch{}}
export function accountStorageKey(key:string){return `consumer.owner:${account.value?.mobile||'guest'}:${key}`}
export function signOut(remove=false){const item=account.value;if(item?.accessToken)void request('/auth/account/logout',item.accessToken,{}).catch(()=>{});if(remove&&activeMobile.value)accounts.value=accounts.value.filter(item=>item.mobile!==activeMobile.value);else if(item){item.accessToken='';item.lastUsedAt=0}activeMobile.value='';clearLegacy();persist()}
export function needsReverification(item:Account){return !item.accessToken||Date.now()-item.lastUsedAt>REVERIFY_AFTER_MS}
export async function activateAccount(mobile:string){const item=accounts.value.find(value=>value.mobile===mobile);if(!item||needsReverification(item))return false;try{await request('/auth/account/activate',item.accessToken,{})}catch(e){if(e instanceof ApiError&&e.status===401){item.accessToken='';item.lastUsedAt=0;persist();return false}throw e}item.lastUsedAt=Date.now();activeMobile.value=mobile;persist();return true}
export function updateActiveAccount(displayName:string){const item=accounts.value.find(value=>value.mobile===activeMobile.value);if(item){item.displayName=displayName;persist()}}
export async function authenticateAccount(register: boolean, mobile: string, password: string, memberName: string) {
  const result=await request<Omit<Account,'lastUsedAt'>>(`/auth/account/${register?'register':'login'}`,'',{mobile,password,memberName})
  const next={...result,lastUsedAt:Date.now()};accounts.value=[next,...accounts.value.filter(item=>item.mobile!==next.mobile)].slice(0,8);activeMobile.value=next.mobile;clearLegacy();persist()
}
export async function reverifyAccount(mobile:string,password:string){await authenticateAccount(false,mobile,password,'')}
export async function accountRequest<T>(path: string, body?: unknown): Promise<T> {
  const owner = account.value?.accessToken
  if (!owner) throw new ApiError('请先登录统一消费者账号', 401)
  try {
    const result = await request<T>(`/auth/account${path}`, owner, body)
    if (account.value?.accessToken !== owner) throw new ApiError('账号已切换，请刷新后重试', 409)
    account.value.lastUsedAt=Date.now();persist()
    return result
  } catch (e) {
    // An incorrect legacy password must not sign out the global account.
    if (e instanceof ApiError && e.status === 401 && !(body as { legacyPassword?: string })?.legacyPassword && path!=='/password' && account.value?.accessToken === owner) signOut()
    throw e
  }
}
export async function enterTenant(tenantId: string, legacyPassword?: string, join=false) {
  return accountRequest<{ accessToken: string; member: boolean }>('/enter', { tenantId, legacyPassword, join })
}
export const accountAddresses=()=>accountRequest<GlobalAddress[]>('/addresses')
export const saveAccountAddress=(address:GlobalAddress)=>accountRequest<GlobalAddress[]>('/addresses',address)
export const deleteAccountAddress=(id:string)=>accountRequest<GlobalAddress[]>(`/addresses/${id}/delete`,{})
export const browseHistory=(search='',page=0,days=0)=>accountRequest<BrowseItem[]>(`/history?search=${encodeURIComponent(search)}&page=${page}&days=${days}`)
export const clearBrowseHistory=()=>accountRequest('/history/clear',{})
export const recordBrowse=(item:Omit<BrowseItem,'viewedAt'>)=>accountRequest('/history',item)
