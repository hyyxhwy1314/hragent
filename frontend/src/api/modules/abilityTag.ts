﻿﻿﻿import { createCrudApi } from '../request'

export interface AbilityTag {
  id?: number
  tagCode?: string
  tagName?: string
  tagCategory?: string
  sort?: number
  status?: number
  createTime?: string
  updateTime?: string
}

export const abilityTagApi = createCrudApi<AbilityTag>('/ability-tags')