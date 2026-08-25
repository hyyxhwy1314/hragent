import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'

export interface R<T = unknown> {
  code: number
  msg: string
  data: T
}

export interface PageVO<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface PageParams {
  pageNum?: number
  pageSize?: number
  [key: string]: any
}

const TOKEN_KEY = 'hragent_token'

/** 读取 token（登录后写入 localStorage） */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 120000 // 2分钟超时，适应AI分析等耗时操作
})

// 请求拦截器：自动带 Authorization 头
instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (res) => {
    // 文件流下载：返回完整响应，便于读取 Content-Disposition 文件名
    if (res.config.responseType === 'blob') {
      return res
    }
    const body = res.data as R
    if (body.code === 200) {
      // 直接返回业务数据 R.data，供业务层使用
      return body.data
    }
    message.error(body.msg || `请求失败 (code=${body.code})`)
    return Promise.reject(new Error(body.msg || `code=${body.code}`))
  },
  (err) => {
    const status = err?.response?.status
    // 401：token 缺失/过期，清 token 跳登录页
    if (status === 401) {
      clearToken()
      // 用 hash 路由，跳登录页
      if (location.hash !== '#/login') {
        location.hash = '#/login'
      }
      message.warning('登录已过期，请重新登录')
      return Promise.reject(err)
    }
    if (status === 404) message.error('接口不存在 (404)')
    else if (status === 500) message.error('服务器错误 (500)')
    else message.error(err?.message || '网络异常')
    return Promise.reject(err)
  }
)

export function httpGet<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance.get(url, config) as unknown as Promise<T>
}

export function httpPost<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
  return instance.post(url, data, config) as unknown as Promise<T>
}

export function httpPut<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
  return instance.put(url, data, config) as unknown as Promise<T>
}

export function httpDelete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance.delete(url, config) as unknown as Promise<T>
}

/** 上传文件（multipart/form-data） */
export function httpUpload<T = any>(url: string, file: File | Blob, fieldName = 'file', config?: AxiosRequestConfig): Promise<T> {
  const form = new FormData()
  form.append(fieldName, file)
  return instance.post(url, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    ...config
  }) as unknown as Promise<T>
}

/** 下载文件流，并触发浏览器保存 */
export async function httpDownload(url: string, defaultName = 'download'): Promise<void> {
  const res = await instance.get(url, { responseType: 'blob' }) as unknown as AxiosResponse<Blob>
  const blob = res.data as Blob
  let filename = defaultName
  const disp = res.headers?.['content-disposition'] || ''
  const m = /filename="?([^"]+)"?/i.exec(disp)
  if (m && m[1]) {
    try { filename = decodeURIComponent(m[1]) } catch { filename = m[1] }
  }
  const objUrl = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = objUrl
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(objUrl)
}


export function createCrudApi<T, S = Partial<T>, U = Partial<T>>(basePath: string) {
  return {
    page: (params: PageParams): Promise<PageVO<T>> =>
      httpGet(`${basePath}/page`, { params }),
    list: (): Promise<T[]> =>
      httpGet(`${basePath}/list`),
    getById: (id: number): Promise<T> =>
      httpGet(`${basePath}/${id}`),
    save: (data: S): Promise<T> =>
      httpPost(basePath, data),
    update: (id: number, data: U): Promise<T> =>
      httpPut(`${basePath}/${id}`, data),
    remove: (id: number): Promise<boolean> =>
      httpDelete(`${basePath}/${id}`)
  }
}
