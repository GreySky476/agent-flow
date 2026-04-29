import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1/admin',
  timeout: 10000
})

export function fetchModels() {
  return api.get('/models')
}

export function enableModel(name) {
  return api.post(`/models/${name}/enable`)
}

export function disableModel(name) {
  return api.post(`/models/${name}/disable`)
}

export function fetchCallLogs(page = 1, size = 20) {
  return api.get('/call-logs', { params: { page, size } })
}
