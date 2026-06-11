import axios from 'axios';

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
});

export const trafficAPI = {
  getStats: ()                   => api.get('/api/traffic/stats'),
  getTopEndpoints: (n = 10)      => api.get(`/api/traffic/endpoints?n=${n}`),
  getRegions: ()                 => api.get('/api/traffic/regions'),
  getErrors: ()                  => api.get('/api/traffic/errors'),
  getBypassTypes: ()             => api.get('/api/traffic/bypass'),
  getAlerts: (n = 50)            => api.get(`/api/traffic/alerts?n=${n}`),
  triggerSpike: (active = true)  => api.post(`/api/traffic/spike/trigger?active=${active}`),
  blockIP: (ip, ttl = 3600)      => api.post(`/api/traffic/ip/block?ip=${ip}&ttlSeconds=${ttl}`),
  getSession: (id)               => api.get(`/api/traffic/sessions/${id}`),
};

export const analyticsAPI = {
  getTimeSeries: (minutes = 60)  => api.get(`/api/analytics/timeseries?minutesBack=${minutes}`),
  getAnomalies: (limit = 20)     => api.get(`/api/analytics/anomalies?limit=${limit}`),
  getTopErrors: (limit = 10)     => api.get(`/api/analytics/top-errors?limit=${limit}`),
};

export default api;
