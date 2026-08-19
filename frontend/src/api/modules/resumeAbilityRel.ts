﻿﻿﻿import { createCrudApi } from '../request'

export interface ResumeAbilityRel {
  id?: number
  resumeId?: number
  abilityTagId?: number
  confidence?: number
  source?: string
  createTime?: string
  updateTime?: string
}

export const resumeAbilityRelApi = createCrudApi<ResumeAbilityRel>('/resume-ability-rels')
