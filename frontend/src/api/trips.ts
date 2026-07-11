import { api, apiPage } from './client'; import type { CreateTripInput, Trip, TripSummary, TripVersion, TripVersionSummary } from './types'
const enc = encodeURIComponent
export const tripApi = {
  list: (cursor?: string) => apiPage<TripSummary[]>(`/trips?limit=20${cursor ? `&cursor=${enc(cursor)}` : ''}`),
  get: (id: string) => api<Trip>(`/trips/${enc(id)}`),
  create: (input: CreateTripInput, key: string) => api<Trip>('/trips', { method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify(input) }),
  update: (id: string, input: CreateTripInput) => api<Trip>(`/trips/${enc(id)}`, { method: 'PATCH', body: JSON.stringify(input) }),
  remove: (id: string) => api<void>(`/trips/${enc(id)}`, { method: 'DELETE' }),
  adjust: (id: string, instruction: string) => api<Trip>(`/trips/${enc(id)}/adjustments`, { method: 'POST', body: JSON.stringify({ instruction }) }),
  versions: (id: string) => api<TripVersionSummary[]>(`/trips/${enc(id)}/versions`),
  version: (id: string, version: number) => api<TripVersion>(`/trips/${enc(id)}/versions/${version}`),
  restore: (id: string, version: number) => api<Trip>(`/trips/${enc(id)}/versions/${version}:restore`, { method: 'POST' }),
}
