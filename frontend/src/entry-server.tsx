import React from 'react'
import { renderToString } from 'react-dom/server'
import { QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import { createQueryClient } from './lib/query-client'

export function render() {
  const queryClient = createQueryClient()
  return renderToString(
    <React.StrictMode>
      <QueryClientProvider client={queryClient}><App /></QueryClientProvider>
    </React.StrictMode>,
  )
}
