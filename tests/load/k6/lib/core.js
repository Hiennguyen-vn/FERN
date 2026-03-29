import exec from 'k6/execution';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const authDuration = new Trend('fern_auth_login_duration', true);
const businessFailures = new Counter('fern_business_failures_total');
const businessCheckFailed = new Rate('fern_business_check_failed');

const actorState = new Map();

function envOrDefault(name, fallback) {
  return __ENV[name] || fallback;
}

export function loadScenario() {
  return JSON.parse(open(__ENV.LOAD_SCENARIO_FILE));
}

export function loadEnvironment() {
  return JSON.parse(open(envOrDefault('LOAD_ENV_FILE', 'tests/load/config/environments/staging.json')));
}

export function loadSeed() {
  return JSON.parse(open(envOrDefault('LOAD_SEED_FILE', 'tests/load/config/seeds/default.json')));
}

export function setupContext(scenario) {
  const environment = loadEnvironment();
  const seed = loadSeed();
  if (!seed.outlets || seed.outlets.length === 0) {
    throw new Error('Seed file has no outlets. Run tests/load/scripts/seed-staging.sh or provide LOAD_SEED_FILE.');
  }
  return {
    scenario,
    environment,
    seed,
    runId: envOrDefault('LOAD_RUN_ID', `manual-${Date.now()}`)
  };
}

export function handleLoadSummary(data, scenario) {
  const outputDir = __ENV.LOAD_OUTPUT_DIR || '.tmp/load/manual';
  return {
    [`${outputDir}/k6-summary.json`]: JSON.stringify(
      {
        scenario: scenario.name,
        runId: __ENV.LOAD_RUN_ID || 'manual',
        metrics: data.metrics,
        root_group: data.root_group
      },
      null,
      2
    )
  };
}

export function login(baseUrl, username, password) {
  const response = http.post(
    `${baseUrl}/auth/login`,
    JSON.stringify({ username, password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { kind: 'login' } }
  );
  authDuration.add(response.timings.duration);
  check(response, {
    'login succeeded': (res) => res.status === 200 && !!res.json('accessToken')
  }) || recordBusinessFailure('login');
  return response.json('accessToken');
}

export function requestJson(method, url, token, body, tags, headers) {
  const requestHeaders = {
    Authorization: `Bearer ${token}`,
    Accept: 'application/json',
    ...(headers || {})
  };
  if (body !== undefined && body !== null) {
    requestHeaders['Content-Type'] = 'application/json';
  }
  const params = { headers: requestHeaders, tags: tags || {} };
  const payload = body === undefined || body === null ? null : JSON.stringify(body);
  const response = http.request(method, url, payload, params);
  return response;
}

export function actorForScenario(seed, scenario, scenarioName) {
  const scenarioKey = `${scenario.name}:${scenarioName}:${exec.vu.idInTest}`;
  if (actorState.has(scenarioKey)) {
    return actorState.get(scenarioKey);
  }

  const scenarioOutlets = scenario.workload_model?.outlets || seed.outlets.length;
  const selectedOutlets = seed.outlets.slice(0, Math.min(seed.outlets.length, scenarioOutlets));
  const vuZeroBased = exec.vu.idInTest - 1;
  const outlet = selectedOutlets[vuZeroBased % selectedOutlets.length];
  const terminalCapacity = scenario.workload_model?.terminals_per_outlet || outlet.terminals?.length || 1;
  const terminalIndex = Math.floor(vuZeroBased / selectedOutlets.length) % terminalCapacity;
  const users = outlet.users && outlet.users.length > 0 ? outlet.users : [];
  if (users.length === 0) {
    throw new Error(`Outlet ${outlet.outlet_id} has no users in seed file.`);
  }

  const actor = {
    outlet,
    terminalIndex,
    terminalId: `${scenario.name}-terminal-${terminalIndex + 1}`,
    user: users[terminalIndex % users.length],
    token: null,
    sessionId: null,
    orderId: null,
    orderTotal: Number(seed.catalog.expected_total_amount || 60500),
    remainingAmount: 0,
    lastPayment: null,
    procurementState: null,
    inventoryState: null
  };
  actorState.set(scenarioKey, actor);
  return actor;
}

export function ensureActorToken(context, actor) {
  if (!actor.token) {
    actor.token = login(resolveIamBaseUrl(context.environment), actor.user.username, actor.user.password);
  }
  return actor.token;
}

export function resolveGatewayBaseUrl(environment) {
  return __ENV.LOAD_BASE_URL || environment.base_urls.gateway;
}

export function resolveIamBaseUrl(environment) {
  return __ENV.LOAD_IAM_BASE_URL || environment.base_urls.iam;
}

export function weightedChoice(weightMap, validKeys) {
  const entries = Object.entries(weightMap).filter(([key]) => validKeys.includes(key));
  const total = entries.reduce((sum, [, weight]) => sum + weight, 0);
  if (total <= 0) {
    return validKeys[0];
  }
  let remaining = Math.random() * total;
  for (const [key, weight] of entries) {
    remaining -= weight;
    if (remaining <= 0) {
      return key;
    }
  }
  return entries[entries.length - 1][0];
}

export function paceRequests(perMinute) {
  if (!perMinute || perMinute <= 0) {
    return;
  }
  sleep(60 / perMinute);
}

export function randomId(prefix) {
  return `${prefix}-${exec.vu.idInTest}-${exec.vu.iterationInScenario}-${Date.now()}`;
}

export function recordBusinessFailure(reason) {
  businessFailures.add(1, { reason });
  businessCheckFailed.add(1);
}

export function recordBusinessSuccess() {
  businessCheckFailed.add(0);
}
