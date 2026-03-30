import { type FormEvent } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RequireAuth } from '@app/guards/RequireAuth'
import { useAuthStore } from '@core/auth/auth.store'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'

const { loginMock, logoutMock } = vi.hoisted(() => ({
  loginMock: vi.fn(),
  logoutMock: vi.fn(),
}))

vi.mock('@core/auth/auth.service', () => ({
  login: loginMock,
  logout: logoutMock,
}))

function HomeScreen() {
  return <h1>Home</h1>
}

function LoginHarness() {
  const navigate = useNavigate()
  const location = useLocation()
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)

  if (isAuthenticated) {
    return <Navigate replace to="/home" />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    await loginMock({ username: 'bootstrap-admin', password: 'Admin123!' })
    const nextPath = (location.state as { from?: string } | null)?.from ?? '/home'
    navigate(nextPath, { replace: true })
  }

  return (
    <form onSubmit={handleSubmit}>
      <h1>Đăng nhập</h1>
      <button type="submit">Sign in</button>
    </form>
  )
}

function renderAuthRoutes(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/login" element={<LoginHarness />} />
        <Route path="/unauthorized" element={<h1>Không có quyền truy cập</h1>} />
        <Route path="/session-expired" element={<h1>Session expired</h1>} />
        <Route element={<RequireAuth />}>
          <Route path="/home" element={<HomeScreen />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('auth and protected routing', () => {
  beforeEach(() => {
    loginMock.mockReset()
    logoutMock.mockReset()
    clearTestStorage()
    resetTestStores()
  })

  it('redirects unauthenticated users to login and returns them to home after login', async () => {
    loginMock.mockImplementation(async () => {
      const { session } = setAuthenticatedSession()
      return session
    })

    const user = userEvent.setup()
    renderAuthRoutes('/home')

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(loginMock).toHaveBeenCalledWith({
      username: 'bootstrap-admin',
      password: 'Admin123!',
    })
    expect(await screen.findByRole('heading', { name: 'Home' })).toBeInTheDocument()
  })

  it('redirects authenticated users away from login to home', async () => {
    setAuthenticatedSession()

    renderAuthRoutes('/login')

    expect(await screen.findByRole('heading', { name: 'Home' })).toBeInTheDocument()
  })

  it('renders unauthorized and session-expired routes', async () => {
    const firstRender = render(
      <MemoryRouter initialEntries={['/unauthorized']}>
        <Routes>
          <Route path="/unauthorized" element={<h1>Không có quyền truy cập</h1>} />
          <Route path="/session-expired" element={<h1>Session expired</h1>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeInTheDocument()

    firstRender.unmount()

    render(
      <MemoryRouter initialEntries={['/session-expired']}>
        <Routes>
          <Route path="/unauthorized" element={<h1>Không có quyền truy cập</h1>} />
          <Route path="/session-expired" element={<h1>Session expired</h1>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Session expired' })).toBeInTheDocument()
  })
})
