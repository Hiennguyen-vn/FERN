import type { PropsWithChildren } from 'react'
import clsx from 'clsx'

interface CardProps extends PropsWithChildren {
  className?: string
  title?: string
}

export function Card({ children, className, title }: CardProps) {
  return (
    <section className={clsx('surface-panel', className)}>
      {title ? (
        <div className="card-header">
          <h2 className="card-title">{title}</h2>
        </div>
      ) : null}
      <div className="page-stack">{children}</div>
    </section>
  )
}
