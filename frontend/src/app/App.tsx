import { AppProviders } from './providers/AppProviders'
import { AppRouterProvider } from './providers/RouterProvider'

export default function App() {
  return (
    <AppProviders>
      <AppRouterProvider />
    </AppProviders>
  )
}
