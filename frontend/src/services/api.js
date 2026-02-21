import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' }
})

// Attach JWT token to every request
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Handle 401 - redirect to login
api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/'
    }
    return Promise.reject(err)
  }
)

export const authApi = {
  getGoogleUrl: () => api.get('/auth/oauth2/url/google'),
  getMicrosoftUrl: () => api.get('/auth/oauth2/url/microsoft'),
  handleGoogleCallback: (code) => api.post('/auth/oauth2/callback/google', { code }),
  handleMicrosoftCallback: (code) => api.post('/auth/oauth2/callback/microsoft', { code }),
  getMe: () => api.get('/auth/me'),
}

export const subscriptionApi = {
  getDashboard: () => api.get('/subscriptions/dashboard'),
  getByCategory: (category) => api.get(`/subscriptions/category/${category}`),
  scanGmail: () => api.post('/subscriptions/scan/gmail'),
  scanOutlook: () => api.post('/subscriptions/scan/outlook'),
  scanAll: () => api.post('/subscriptions/scan/all'),
  unsubscribe: (id) => api.post(`/subscriptions/${id}/unsubscribe`),
  updateCategory: (id, category) => api.patch(`/subscriptions/${id}/category`, { category }),
}

export default api
