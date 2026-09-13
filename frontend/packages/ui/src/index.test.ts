import { describe, expect, it } from 'vitest'
import { appLabels } from './index'

describe('application labels', () => {
  it('defines all four independently deployed applications', () => {
    expect(Object.keys(appLabels)).toEqual(['platform', 'merchant', 'store'])
  })
})
