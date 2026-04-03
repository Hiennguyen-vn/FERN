import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'

// Load global styles
import '@styles/globals.css'
import '@styles/utilities.css'
import '@styles/stitch-foundation.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
