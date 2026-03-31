import { createServer as createHttpServer } from 'node:http'
import { createServer as createViteServer } from 'vite'

const host = '127.0.0.1'
const port = 3000
const gatewayTarget = 'http://127.0.0.1:8080'
const gatewayProxyPrefixes = [
  '/actuator',
  '/auth',
  '/attendance-approvals',
  '/attendance-events',
  '/audit',
  '/employee-assignments',
  '/employee-contracts',
  '/employees',
  '/finance-config',
  '/goods-receipts',
  '/ingredient-categories',
  '/ingredients',
  '/inventory-transactions',
  '/permissions',
  '/pos-sessions',
  '/payroll-periods',
  '/payroll-runs',
  '/product-categories',
  '/product-availability',
  '/product-prices',
  '/products',
  '/purchase-orders',
  '/regions',
  '/recipe-versions',
  '/recipes',
  '/reports/exports',
  '/reports/payroll/summary',
  '/reports/payroll/runs',
  '/roles',
  '/sale-orders',
  '/shift-assignments',
  '/shift-schedules',
  '/stock-balances',
  '/stock-adjustments',
  '/suppliers',
  '/supplier-invoices',
  '/supplier-payments',
  '/tax-rates',
  '/units-of-measure',
  '/uom-conversions',
  '/users',
  '/waste-records',
  '/stock-count-sessions',
  '/outlets',
  '/catalog/promotions',
]

const hopByHopRequestHeaders = new Set([
  'connection',
  'content-length',
  'host',
])

const hopByHopResponseHeaders = new Set([
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
])

function normalizeUrl(url) {
  try {
    const parsedUrl = new URL(url)
    return `${parsedUrl.pathname}${parsedUrl.search}`
  } catch {
    return url
  }
}

function matchesGatewayPrefix(pathname) {
  return gatewayProxyPrefixes.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`))
}

function resolveGatewayPath(url) {
  const normalizedUrl = normalizeUrl(url)
  const [pathname, search = ''] = normalizedUrl.split('?')
  const normalizedPath =
    pathname === '/api'
      ? '/'
      : pathname.startsWith('/api/')
        ? pathname.slice(4)
        : pathname

  if (!matchesGatewayPrefix(normalizedPath)) {
    return null
  }

  return search ? `${normalizedPath}?${search}` : normalizedPath
}

function collectRequestBody(request) {
  return new Promise((resolveBody, rejectBody) => {
    const chunks = []

    request.on('data', (chunk) => {
      chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk))
    })
    request.on('end', () => {
      resolveBody(Buffer.concat(chunks))
    })
    request.on('error', (error) => {
      rejectBody(error)
    })
  })
}

function buildProxyHeaders(request) {
  const headers = new Headers()

  for (const [headerName, headerValue] of Object.entries(request.headers)) {
    if (headerValue === undefined || hopByHopRequestHeaders.has(headerName)) {
      continue
    }

    if (Array.isArray(headerValue)) {
      headerValue.forEach((value) => headers.append(headerName, value))
      continue
    }

    headers.set(headerName, headerValue)
  }

  return headers
}

async function proxyGatewayRequest(request, response, gatewayPath) {
  const method = request.method ?? 'GET'
  const init = {
    method,
    headers: buildProxyHeaders(request),
    redirect: 'manual',
  }

  if (!['GET', 'HEAD'].includes(method.toUpperCase())) {
    const body = await collectRequestBody(request)
    if (body.length > 0) {
      init.body = body
      init.duplex = 'half'
    }
  }

  const upstreamResponse = await fetch(new URL(gatewayPath, gatewayTarget), init)
  const responseBody = Buffer.from(await upstreamResponse.arrayBuffer())

  response.statusCode = upstreamResponse.status
  upstreamResponse.headers.forEach((headerValue, headerName) => {
    if (hopByHopResponseHeaders.has(headerName)) {
      return
    }

    response.setHeader(headerName, headerValue)
  })
  response.setHeader('content-length', String(responseBody.length))
  response.end(responseBody)
}

const vite = await createViteServer({
  clearScreen: false,
  appType: 'spa',
  server: {
    middlewareMode: true,
    hmr: false,
  },
})

const server = createHttpServer(async (request, response) => {
  try {
    const gatewayPath = resolveGatewayPath(request.url ?? '')
    if (gatewayPath) {
      await proxyGatewayRequest(request, response, gatewayPath)
      return
    }

    vite.middlewares(request, response, (error) => {
      if (error) {
        vite.ssrFixStacktrace(error)
        response.statusCode = 500
        response.end(error.stack ?? String(error))
        return
      }

      response.statusCode = 404
      response.end('Not found')
    })
  } catch (error) {
    const message = error instanceof Error ? error.stack ?? error.message : String(error)
    response.statusCode = 502
    response.end(message)
  }
})

server.listen(port, host, () => {
  console.log(`FERN dev server ready at http://${host}:${port}/`)
})

const shutdown = async () => {
  server.close()
  await vite.close()
  process.exit(0)
}

process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)
