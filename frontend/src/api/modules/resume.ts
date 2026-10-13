import { createCrudApi, httpGet, httpPut, httpUpload, httpDownload, httpPost } from '../request'

export interface Resume {
  id?: number
  resumeName?: string
  gender?: number
  birthDate?: string
  phone?: string
  email?: string
  idCard?: string
  expectPosition?: string
  expectSalaryMin?: number | string
  expectSalaryMax?: number | string
  expectCity?: string
  workYears?: number
  education?: number
  school?: string
  major?: string
  resumeContent?: string
  resumeStructJson?: string
  resumeFileId?: number
  resumeStatus?: number
  deliverySource?: number
  targetJobId?: number
  matchScore?: number | string
  screeningOpinion?: string
  ownerEmpId?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 上传文件返回VO */
export interface FileVO {
  id: number
  objectKey: string
  originalName: string
  fileType: string
  fileSize: number
  storageType: string
  previewUrl: string
}

/** 简历解析出的结构化字段 */
export interface ResumeParsedData {
  resumeName?: string
  phone?: string
  email?: string
  school?: string
  major?: string
  expectPosition?: string
  expectCity?: string
  workYears?: number
  education?: number
  rawText?: string
}

/** 简历上传结果：文件信息 + 解析字段 */
export interface ResumeUploadVO {
  fileId: number
  objectKey: string
  originalName: string
  previewUrl: string
  parsed?: ResumeParsedData
}

/** 简历 AI 深度分析 - 分类技能标签匹配明细 */
export interface ResumeTagMatch {
  category?: string
  score?: number
  matched?: string[]
  missing?: string[]
}

/** 简历 AI 深度分析结果 */
export interface ResumeDeepAnalysisVO {
  success: boolean
  summary?: string
  strengths?: string[]
  weaknesses?: string[]
  matchAnalysis?: string
  suggestions?: string[]
  /** 综合评分（0-100） */
  score?: number
  /** 各分类技能标签匹配明细 */
  tagMatch?: ResumeTagMatch[]
}

export const resumeApi = {
  ...createCrudApi<Resume>('/resumes'),
  /** 上传简历附件，返回文件ID、预览URL与解析字段 */
  upload(file: File | Blob): Promise<ResumeUploadVO> {
    return httpUpload<ResumeUploadVO>('/resumes/upload', file)
  },
  /** 归档简历 */
  archive(id: number): Promise<boolean> {
    return httpPut<boolean>(`/resumes/${id}/archive`)
  },
  /** 获取附件预览URL */
  previewFile(id: number): Promise<{ previewUrl: string }> {
    return httpGet(`/resumes/${id}/file/preview`)
  },
  /** 下载简历附件 */
  downloadFile(id: number): Promise<void> {
    return httpDownload(`/resumes/${id}/file/download`, `resume-${id}`)
  },
  /** AI深度分析简历 */
  deepAnalyze(id: number): Promise<ResumeDeepAnalysisVO> {
    return httpPost<ResumeDeepAnalysisVO>(`/resumes/${id}/deep-analysis`)
  },
  /** 查询最近一次深度分析/评分结果（不重新计算） */
  getScore(id: number): Promise<ResumeDeepAnalysisVO> {
    return httpGet<ResumeDeepAnalysisVO>(`/resumes/${id}/score`)
  }
}
