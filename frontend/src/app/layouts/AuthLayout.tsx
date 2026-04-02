import type { PropsWithChildren } from 'react'
import { AppIcon } from '@app/components/AppIcon'

export function AuthLayout({ children }: PropsWithChildren) {
  return (
    <div className="auth-shell screen-center">
      <div className="auth-container">
        {/* Brand */}
        <div className="auth-brand-stack">
          <div className="auth-brand-mark">
            <AppIcon filled name="restaurant_menu" size="lg" />
          </div>
          <div className="auth-brand-copy">
            <h1>FERN ERP F&amp;B</h1>
            <p>Enterprise Management System</p>
          </div>
        </div>

        {/* Card */}
        <div className="auth-card">
          <div className="auth-card-accent" aria-hidden="true" />
          {children}
        </div>

        <div className="auth-divider" role="separator">
          <span>Credential Control</span>
        </div>

        <div className="auth-security-note">
          <AppIcon className="auth-security-icon" name="security" size="sm" />
          <div>
            <strong>Security Notice</strong>
            <p>Authorized users only. This environment is monitored for security and compliance.</p>
          </div>
        </div>

        <div className="auth-footer-links">
          <span>Security Policy</span>
          <span>Terms of Service</span>
          <span>System Status</span>
        </div>
      </div>

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
