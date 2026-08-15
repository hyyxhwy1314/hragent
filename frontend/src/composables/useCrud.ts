import { ref } from 'vue'
import type { PageParams, PageVO } from '@/api/request'

export interface CrudApi<T, S = Partial<T>, U = Partial<T>> {
  page: (params: PageParams) => Promise<PageVO<T>>
  list: () => Promise<T[]>
  getById: (id: number) => Promise<T>
  save: (data: S) => Promise<T>
  update: (id: number, data: U) => Promise<T>
  remove: (id: number) => Promise<boolean>
}

export function useCrud<T, S = Partial<T>, U = Partial<T>>(api: CrudApi<T, S, U>) {
  const dataSource = ref<T[]>([])
  const loading = ref(false)
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)

  async function fetch(params: Record<string, any> = {}) {
    loading.value = true
    try {
      const vo = await api.page({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...params
      })
      dataSource.value = Array.isArray(vo?.records) ? vo.records : []
      total.value = vo?.total ?? 0
    } catch (e) {
      console.error('[useCrud.fetch] ERROR:', e)
      dataSource.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  async function reload(params?: Record<string, any>) {
    pageNum.value = 1
    await fetch(params)
  }

  async function handleTableChange(pag: any) {
    pageNum.value = pag.current
    pageSize.value = pag.pageSize
    await fetch()
  }

  async function save(data: S) {
    await api.save(data)
    await reload()
  }

  async function update(id: number, data: U) {
    await api.update(id, data)
    await fetch()
  }

  async function remove(id: number) {
    await api.remove(id)
    if (dataSource.value.length === 1 && pageNum.value > 1) {
      pageNum.value--
    }
    await fetch()
  }

  return {
    dataSource,
    loading,
    total,
    pageNum,
    pageSize,
    fetch,
    reload,
    handleTableChange,
    save,
    update,
    remove
  }
}
