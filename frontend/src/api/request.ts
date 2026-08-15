import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
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

const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000
})

instance.interceptors.response.use(
  (res) => {
    const body = res.data as R
    if (body.code === 200) {
      return body.data
    }
    message.error(body.msg || `请求失败 (code=${body.code})`)
    return Promise.reject(new Error(body.msg || `code=${body.code}`))
  },
  (err) => {
    const status = err?.response?.status
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
