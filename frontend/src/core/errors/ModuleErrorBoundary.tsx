import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props {
  children: ReactNode
  moduleName?: string
}

interface State {
  hasError: boolean
  error: Error | null
}

/**
 * ModuleErrorBoundary — wraps each lazy-loaded module route.
 * Catches chunk load failures (network errors during lazy import)
 * and runtime errors within a module, without crashing the whole app.
 */
export class ModuleErrorBoundary extends Component<Props, State> {
  public override state: State = {
    hasError: false,
    error: null,
  }

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  public override componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    const mod = this.props.moduleName ?? 'unknown'
    console.error(`[ModuleErrorBoundary:${mod}] Caught error:`, error, errorInfo)
  }

  private handleRetry = () => {
    this.setState({ hasError: false, error: null })
  }

  public override render() {
    if (this.state.hasError) {
      const isChunkError =
        this.state.error?.message?.includes('Failed to fetch dynamically imported module') ||
        this.state.error?.name === 'ChunkLoadError'

      return (
        <div className="surface-panel page-stack" style={{ padding: '2rem', textAlign: 'center' }}>
          <p className="eyebrow">{this.props.moduleName ?? 'Module'}</p>
          <h2>{isChunkError ? 'Failed to load module' : 'Something went wrong'}</h2>
          <p className="muted-text" style={{ marginBottom: '1rem' }}>
            {isChunkError
              ? 'A network error occurred while loading this section. Check your connection and try again.'
              : this.state.error?.message ?? 'An unexpected error occurred.'}
          </p>
          <button
            className="btn btn-secondary"
            onClick={isChunkError ? () => window.location.reload() : this.handleRetry}
          >
            {isChunkError ? 'Reload page' : 'Try again'}
          </button>
        </div>
      )
    }

    return this.props.children
  }
}
