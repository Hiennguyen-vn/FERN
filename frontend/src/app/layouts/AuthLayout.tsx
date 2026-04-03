import type { PropsWithChildren } from 'react'
import { AppIcon } from '@app/components/AppIcon'

export function AuthLayout({ children }: PropsWithChildren) {
  return (
    <div className="auth-shell">
      <main className="auth-main">
        <div className="auth-stage">
          <div className="auth-brand-stack">
            <div className="auth-brand-mark" aria-hidden="true">
              <AppIcon filled name="restaurant_menu" size="lg" />
            </div>
            <div className="auth-brand-copy">
              <h1>FERN ERP F&amp;B</h1>
              <p>Enterprise Management System</p>
            </div>
          </div>

          <div className="auth-card">
            <div className="auth-card-accent" aria-hidden="true" />
            <div className="auth-card-body">{children}</div>
          </div>

          <div className="auth-security-note">
            <AppIcon className="auth-security-icon" name="security" size="sm" />
            <p>
              <strong>Security Notice</strong>
              Authorized users only. This system is monitored for security and compliance purposes.
            </p>
          </div>

          <div className="auth-footer-links">
            <a href="#security-policy">Security Policy</a>
            <a href="#terms">Terms of Service</a>
            <a href="#status">System Status</a>
          </div>
        </div>
      </main>

      <footer className="auth-global-footer">
        <span>© 2024 FERN ERP Kitchen OS. All rights reserved.</span>
        <div className="auth-global-footer-actions">
          <AppIcon name="language" size="sm" />
          <AppIcon name="help" size="sm" />
        </div>
      </footer>
    </div>
  )
}
