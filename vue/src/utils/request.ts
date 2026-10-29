import { createAlova } from 'alova'
import adapterFetch from 'alova/fetch'
import VueHook from 'alova/vue'

export const alovaInstance = createAlova({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  statesHook: VueHook,
  requestAdapter: adapterFetch(),
  timeout: 10000,
  responded: {
    onSuccess: async (response) => {
      if (!response.ok)
        throw new Error(`[${response.status}] ${response.statusText}`)

      return await response.json()
    },
    onError: (error) => {
      console.error('[alova] request error:', error)
      throw error
    },
  },
})
