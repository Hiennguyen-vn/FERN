import { handleLoadSummary, loadScenario, setupContext } from '../lib/core.js';
import { runPaymentBurst, runStatefulPos } from '../lib/flows.js';

const scenario = loadScenario();

export const options = scenario.k6.options;

export function setup() {
  return setupContext(scenario);
}

export default function (context) {
  if (scenario.behavior === 'payment_burst') {
    runPaymentBurst(context);
    return;
  }
  runStatefulPos(context);
}

export function handleSummary(data) {
  return handleLoadSummary(data, scenario);
}
