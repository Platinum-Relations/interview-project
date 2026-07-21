import { logAction, logError } from '../lib/log'
import type {
  BreakItem,
  Classification,
  ImportResponse,
  MerchantRollup,
  QuarantineRow,
  RunSummary,
} from './types'

async function getJson<T>(url: string): Promise<T | null> {
  logAction('api.request', { method: 'GET', url })
  const response = await fetch(url)
  if (response.status === 204) {
    logAction('api.response', { url, status: 204 })
    return null
  }
  if (!response.ok) {
    const body = await response.text()
    logError('api.request', { url, status: response.status, body })
    throw new Error(`GET ${url} failed with ${response.status}`)
  }
  const data = (await response.json()) as T
  logAction('api.response', { url, status: response.status })
  return data
}

export async function importFiles(internal: File, settlement: File): Promise<ImportResponse> {
  const form = new FormData()
  form.append('internal', internal)
  form.append('settlement', settlement)

  logAction('api.request', {
    method: 'POST',
    url: '/api/imports',
    internalFile: internal.name,
    settlementFile: settlement.name,
  })
  const response = await fetch('/api/imports', { method: 'POST', body: form })
  const body = await response.json()
  if (!response.ok) {
    logError('api.import', { status: response.status, body: JSON.stringify(body) })
    throw new Error(body.error ?? `Import failed with ${response.status}`)
  }
  logAction('api.response', { url: '/api/imports', status: response.status, body: JSON.stringify(body) })
  return body as ImportResponse
}

export function fetchLatestSummary(): Promise<RunSummary | null> {
  return getJson<RunSummary>('/api/runs/latest/summary')
}

export function fetchBreaks(runId: number, category: Classification | null): Promise<BreakItem[] | null> {
  const query = category ? `?category=${category}` : ''
  return getJson<BreakItem[]>(`/api/runs/${runId}/breaks${query}`)
}

export function fetchMerchants(runId: number): Promise<MerchantRollup[] | null> {
  return getJson<MerchantRollup[]>(`/api/runs/${runId}/merchants`)
}

export function fetchQuarantine(runId: number): Promise<QuarantineRow[] | null> {
  return getJson<QuarantineRow[]>(`/api/runs/${runId}/quarantine`)
}
