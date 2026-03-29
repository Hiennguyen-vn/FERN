import { handleLoadSummary, loadScenario, setupContext } from '../lib/core.js';
import { runInventoryEventStorm } from '../lib/flows.js';

const scenario = loadScenario();

export const options = scenario.k6.options;

export function setup() {
  return setupContext(scenario);
}

export default function (context) {
  runInventoryEventStorm(context);
}

export function handleSummary(data) {
  return handleLoadSummary(data, scenario);
}
