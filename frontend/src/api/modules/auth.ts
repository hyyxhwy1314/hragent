import { httpGet, httpPost } from '../request'

export interface LoginDTO {
  account: string
  password: string
}

export interface LoginVO {
  token: string
  empId: number
  empName: string
  role: string
}

export function login(data: LoginDTO): Promise<LoginVO> {
  return httpPost('/auth/login', data)
}

export function getCurrentUser(): Promise<LoginVO> {
  return httpGet('/auth/me')
}

export function logout(): Promise<boolean> {
  return httpPost('/auth/logout')
}
