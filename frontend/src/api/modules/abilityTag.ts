﻿import { createCrudApi } from '../request'

export interface AbilityTag {
  id?: number
  tagName?: string
  tagCategory?: string
  tagDesc?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export const abilityTagApi = createCrudApi<AbilityTag>('/ability-tags')
