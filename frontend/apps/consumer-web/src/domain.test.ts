import { describe, expect, it } from 'vitest'
import { parseJson } from './api'
import { reconcile, validId, resolveTenant, type Product } from './domain'

describe('consumer cart and API boundary', () => {
  it('uses the configured mall for empty and placeholder links, with a bundled fallback', () => {
    for (const entry of [null, '', '<brand-id>', 'undefined', 'invalid', '0']) {
      expect(resolveTenant(entry, '1788875508788001', '41')).toBe('1788875508788001')
    }
    expect(resolveTenant(null, undefined, '1788875508788001')).toBe('1788875508788001')
    expect(resolveTenant('', '', '1788875508788001')).toBe('1788875508788001')
    expect(resolveTenant('42', '41', '43')).toBe('42')
    expect(resolveTenant(null, undefined, '')).toBe('')
  })
  it('preserves long IDs without rewriting numbers inside descriptions', () => {
    expect(parseJson('{"id":9223372036854775806,"storeId":21,"quantity":3,"description":"订单 9223372036854775806"}')).toEqual({ id: '9223372036854775806', storeId: '21', quantity: 3, description: '订单 9223372036854775806' })
  })
  it('removes unavailable goods and invalid quantities, caps surviving quantities at actual stock', () => {
    const products = [{ skuId: '1', selectable: true, availableQuantity: 2 }, { skuId: '2', selectable: false, availableQuantity: 9 }, { skuId: '3', selectable: true, availableQuantity: 9 }] as Product[]
    expect(reconcile({ '1': 10, '2': 3, '3': -2, 'missing': 1 }, products)).toEqual({ '1': 2 })
    expect(reconcile({ '1': NaN, '3': 1.2 }, products)).toEqual({})
  })
  it('rejects malformed tenant entries and values outside the backend long range', () => {
    expect(validId('9223372036854775807')).toBe(true)
    for (const value of ['0', '-1', '1e3', '01', '9223372036854775808', '<script>', null]) expect(validId(value)).toBe(false)
  })
})
