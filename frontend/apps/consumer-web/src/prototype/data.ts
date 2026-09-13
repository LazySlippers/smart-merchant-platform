export type Drink = { id: string; name: string; en: string; category: string; price: number; description: string; flavor: string; color: string; milk: string; label?: string; kcal: number }
export const drinks: Drink[] = [
  { id: 'matcha', name: '云顶抹茶拿铁', en: 'MATCHA CLOUD', category: '当季新品', price: 22, description: '细磨抹茶的清新，遇见轻盈绵密的奶云。茶香慢慢展开，每一口都是春日的呼吸。', flavor: '清新茶香 · 绵密奶云', color: '#728b3e', milk: '#f6f0d9', label: '当季限定', kcal: 186 },
  { id: 'pear', name: '青山雪梨茉莉', en: 'PEAR & JASMINE', category: '鲜果茗茶', price: 19, description: '清甜雪梨融入窨制茉莉绿茶，果香与花香层层交叠，清爽刚刚好。', flavor: '鲜切雪梨 · 茉莉清香', color: '#bbc76e', milk: '#ecedc3', label: '人气推荐', kcal: 126 },
  { id: 'oat', name: '桂花燕麦拿铁', en: 'OSMANTHUS OAT', category: '轻乳茶', price: 21, description: '馥郁桂花乌龙与香醇燕麦乳，柔和花香裹着谷物的暖意。', flavor: '桂花乌龙 · 植物燕麦乳', color: '#af8056', milk: '#e6d1aa', label: '植物基', kcal: 168 },
  { id: 'berry', name: '多肉莓莓', en: 'VERY BERRY', category: '鲜果茗茶', price: 23, description: '鲜切草莓果肉与清雅茉莉茶底，搭配轻盈芝士奶盖，酸甜鲜活。', flavor: '鲜切草莓 · 轻芝士奶盖', color: '#c7797d', milk: '#fae9da', label: '热卖', kcal: 212 },
  { id: 'oolong', name: '白桃乌龙轻乳', en: 'PEACH OOLONG', category: '轻乳茶', price: 18, description: '白桃的柔甜遇见乌龙的焙火茶香，甄选鲜乳，轻盈又满足。', flavor: '白桃乌龙 · 甄选鲜乳', color: '#d3a078', milk: '#f1ddbc', kcal: 156 },
  { id: 'coffee', name: '生椰丝绒拿铁', en: 'COCONUT LATTE', category: '日常咖啡', price: 20, description: '香醇浓缩咖啡，融入清甜椰乳。丝绒般的口感，唤醒今日好心情。', flavor: '香醇浓缩 · 清甜椰乳', color: '#82604a', milk: '#e7d7bc', kcal: 175 },
  { id: 'lemon', name: '手打香柠绿茶', en: 'FRESH LEMON TEA', category: '纯茶与清饮', price: 16, description: '新鲜香水柠檬手打释香，搭配清冽绿茶，酸香明亮，清爽解腻。', flavor: '香水柠檬 · 清冽绿茶', color: '#c4c664', milk: '#eff0c7', kcal: 98 },
  { id: 'tea', name: '山野栀香乌龙', en: 'GARDENIA OOLONG', category: '纯茶与清饮', price: 13, description: '来自山野的栀子花香与轻焙乌龙，慢慢品味茶叶本真的回甘。', flavor: '栀子花香 · 轻焙乌龙', color: '#b39445', milk: '#dbc776', kcal: 8 },
]
export const categories = ['全部', '当季新品', '鲜果茗茶', '轻乳茶', '日常咖啡', '纯茶与清饮']
export const stores = [
  { id: 'lakeside', name: '叶集 · 湖滨银泰店', address: '延安路 98 号湖滨银泰 in77 B 区 1F', hours: '09:00–22:00', distance: '350m', time: 8, tag: '最近门店', open: true },
  { id: 'west', name: '叶集 · 西湖文化广场店', address: '西湖文化广场 18 号 1F', hours: '09:30–22:00', distance: '1.2km', time: 12, tag: '座位充足', open: true },
  { id: 'city', name: '叶集 · 城西银泰店', address: '丰潭路 380 号城西银泰城 2F', hours: '10:00–21:30', distance: '3.8km', time: 15, tag: '商场门店', open: true },
  { id: 'park', name: '叶集 · 植物园店', address: '桃源岭 1 号植物园南门旁', hours: '10:00–18:00', distance: '5.1km', time: 0, tag: '今日已打烊', open: false },
]
export type Address = { id: string; name: string; phone: string; address: string; label: string }
export type Coupon = { id: string; name: string; discount: number; min: number; end: string; used?: boolean }
export const initialCoupons: Coupon[] = [{ id: 'welcome', name: '新朋友见面礼', discount: 6, min: 20, end: '2026.12.31' }, { id: 'afternoon', name: '午后好时光', discount: 3, min: 15, end: '2026.12.31' }]
export type CartLine = { key: string; drinkId: string; size: string; temp: string; sugar: string; toppings: string[]; unit: number; quantity: number }
export type DemoOrder = { id: string; code: string; date: string; status: 'ready' | 'done' | 'cancelled' | 'refunding' | 'refunded'; storeId: string; mode: 'pickup' | 'delivery'; lines: CartLine[]; subtotal: number; discount: number; deliveryFee: number; total: number; couponId: string; note: string; time: string; address: string }
export function read<T>(key: string, fallback: T): T { try { return JSON.parse(localStorage.getItem(`yeji.preview.${key}`) || 'null') ?? fallback } catch { return fallback } }
export function write(key: string, value: unknown) { try { localStorage.setItem(`yeji.preview.${key}`, JSON.stringify(value)) } catch { /* Preview remains usable without storage. */ } }
export const price = (n: number) => Number.isInteger(n) ? String(n) : n.toFixed(2)
