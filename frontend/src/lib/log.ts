/**
 * UI action logger. Every user interaction and API round-trip goes through here
 * so a pasted console log reconstructs exactly what the user did.
 * Payloads are flattened to single-line JSON - nothing to expand in the console.
 */
export function logAction(action: string, detail?: unknown) {
  const timestamp = new Date().toISOString()
  if (detail === undefined) {
    console.log(`[ui] ${timestamp} ${action}`)
  } else {
    console.log(`[ui] ${timestamp} ${action} ${JSON.stringify(detail)}`)
  }
}

export function logError(action: string, error: unknown) {
  const timestamp = new Date().toISOString()
  const message = error instanceof Error ? error.message : JSON.stringify(error)
  console.error(`[ui] ${timestamp} ${action} failed: ${message}`)
}
