import { useEffect } from 'react'
import { appConfig } from '@core/config/appConfig'

export function usePageTitle(title: string) {
  useEffect(() => {
    document.title = `${title} | ${appConfig.appName}`
  }, [title])
}
