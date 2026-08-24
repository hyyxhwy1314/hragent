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

/** 简历AI分析结果 */
export interface ResumeAiAnalysisVO {
  success: boolean
  filename?: string
  resumeText?: string
  evaluation?: string
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
  /** AI分析简历 */
  aiAnalyze(id: number): Promise<ResumeAiAnalysisVO> {
    return httpPost<ResumeAiAnalysisVO>(`/resumes/${id}/ai-analyze`)
  }
}
