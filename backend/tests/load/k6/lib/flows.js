import exec from 'k6/execution';
import { check } from 'k6';
import {
  actorForScenario,
  ensureActorToken,
  paceRequests,
  randomId,
  recordBusinessFailure,
  recordBusinessSuccess,
  requestJson,
  resolveGatewayBaseUrl,
  weightedChoice
} from './core.js';

function choosePaymentAmount(actor, scenario) {
  const remaining = actor.remainingAmount > 0 ? actor.remainingAmount : actor.orderTotal;
  const partialRatio = scenario.payment_mix?.partial_first_payment_ratio || 0;
  if (actor.remainingAmount === actor.orderTotal && Math.random() < partialRatio) {
    return Math.round((actor.orderTotal * 0.5) * 100) / 100;
  }
  return remaining;
}

function choosePaymentMethod(scenario) {
  const cashRatio = scenario.payment_mix?.cash_ratio || 0;
  return Math.random() < cashRatio ? 'CASH' : 'CARD';
}

function validPosActions(actor) {
  const actions = ['open_or_list_session'];
  if (!actor.orderId) {
    actions.push('create_or_update_order');
    return actions;
  }
  if (actor.remainingAmount > 0) {
    actions.push('create_or_update_order', 'add_payment');
    return actions;
  }
  actions.push('complete_order', 'read_order_or_session', 'read_stock_or_side_effect');
  return actions;
}

export function runStatefulPos(context) {
  const scenarioName = exec.scenario.name;
  const scenario = context.scenario;
  const gatewayBaseUrl = resolveGatewayBaseUrl(context.environment);
  const actor = actorForScenario(context.seed, scenario, scenarioName);
  const token = ensureActorToken(context, actor);
  const requestMix = scenario.request_mix;
  const action = weightedChoice(requestMix, validPosActions(actor));

  if (action === 'open_or_list_session') {
    if (!actor.sessionId) {
      const response = requestJson(
        'POST',
        `${gatewayBaseUrl}/pos-sessions`,
        token,
        {
          regionId: actor.outlet.region_id,
          outletId: actor.outlet.outlet_id,
          currencyCode: actor.outlet.currency_code || 'VND',
          terminalId: actor.terminalId,
          businessDate: scenario.timing.business_date
        },
        { kind: 'open_session', outlet: String(actor.outlet.outlet_id) }
      );
      check(response, {
        'session open succeeded': (res) => res.status === 200 && !!res.json('id')
      }) || recordBusinessFailure('open_session');
      if (response.status === 200) {
        actor.sessionId = response.json('id');
        recordBusinessSuccess();
      }
    } else {
      const response = requestJson(
        'GET',
        `${gatewayBaseUrl}/pos-sessions/${actor.sessionId}`,
        token,
        null,
        { kind: 'get_session', outlet: String(actor.outlet.outlet_id) }
      );
      check(response, { 'get session succeeded': (res) => res.status === 200 }) || recordBusinessFailure('get_session');
      if (response.status === 200) {
        recordBusinessSuccess();
      }
    }
    paceRequests(scenario.workload_model.requests_per_minute_per_terminal);
    return;
  }

  if (action === 'create_or_update_order') {
    if (!actor.orderId) {
      const response = requestJson(
        'POST',
        `${gatewayBaseUrl}/sale-orders`,
        token,
        {
          posSessionId: actor.sessionId,
          orderType: 'DINE_IN',
          note: `load-order-${randomId('note')}`,
          lines: [{ productId: context.seed.catalog.product_id, qty: 1.0 }]
        },
        { kind: 'create_order', outlet: String(actor.outlet.outlet_id) }
      );
      check(response, {
        'create order succeeded': (res) => res.status === 200 && !!res.json('id')
      }) || recordBusinessFailure('create_order');
      if (response.status === 200) {
        actor.orderId = response.json('id');
        actor.remainingAmount = actor.orderTotal;
        actor.lastPayment = null;
        recordBusinessSuccess();
      }
    } else {
      const response = requestJson(
        'PATCH',
        `${gatewayBaseUrl}/sale-orders/${actor.orderId}`,
        token,
        {
          note: `load-update-${randomId('note')}`,
          lines: [{ productId: context.seed.catalog.product_id, qty: 1.0 }]
        },
        { kind: 'update_order', outlet: String(actor.outlet.outlet_id) }
      );
      check(response, { 'update order succeeded': (res) => res.status === 200 }) || recordBusinessFailure('update_order');
      if (response.status === 200) {
        recordBusinessSuccess();
      }
    }
    paceRequests(scenario.workload_model.requests_per_minute_per_terminal);
    return;
  }

  if (action === 'add_payment') {
    if (!actor.orderId) {
      paceRequests(scenario.workload_model.requests_per_minute_per_terminal);
      return;
    }
    const shouldReplay = actor.lastPayment
      && Math.random() < (scenario.payment_mix?.retry_same_key_ratio || 0);
    const idempotencyKey = shouldReplay ? actor.lastPayment.idempotencyKey : randomId('payment');
    const amount = shouldReplay ? actor.lastPayment.amount : choosePaymentAmount(actor, scenario);
    const payload = shouldReplay
      ? actor.lastPayment.payload
      : {
          paymentMethod: choosePaymentMethod(scenario),
          amount,
          transactionRef: randomId('txn')
        };
    const response = requestJson(
      'POST',
      `${gatewayBaseUrl}/sale-orders/${actor.orderId}/payments`,
      token,
      payload,
      { kind: 'add_payment', outlet: String(actor.outlet.outlet_id) },
      { 'Idempotency-Key': idempotencyKey }
    );
    check(response, { 'add payment succeeded': (res) => res.status === 200 }) || recordBusinessFailure('add_payment');
    if (response.status === 200) {
      if (!shouldReplay) {
        actor.remainingAmount = Math.max(0, actor.remainingAmount - amount);
        actor.lastPayment = { idempotencyKey, payload, amount };
      }
      recordBusinessSuccess();
    }
    paceRequests(scenario.workload_model.requests_per_minute_per_terminal);
    return;
  }

  if (action === 'complete_order') {
    if (!actor.orderId || actor.remainingAmount > 0) {
      paceRequests(scenario.workload_model.requests_per_minute_per_terminal);
      return;
    }
    const response = requestJson(
      'POST',
      `${gatewayBaseUrl}/sale-orders/${actor.orderId}/complete`,
      token,
      null,
      { kind: 'complete_order', outlet: String(actor.outlet.outlet_id) }
    );
    check(response, {
      'complete order succeeded': (res) => res.status === 200 && res.json('status') === 'COMPLETED'
    }) || recordBusinessFailure('complete_order');
    if (response.status === 200) {
      actor.orderId = null;
      actor.remainingAmount = 0;
      actor.lastPayment = null;
      recordBusinessSuccess();
    }
    paceRequests(scenario.workload_model.requests_per_minute_per_terminal);
    return;
  }

  if (action === 'read_stock_or_side_effect') {
    const response = requestJson(
      'GET',
      `${gatewayBaseUrl}/stock-balances?outletId=${actor.outlet.outlet_id}&ingredientId=${context.seed.catalog.ingredient_id}`,
      token,
      null,
      { kind: 'stock_balance', outlet: String(actor.outlet.outlet_id) }
    );
    check(response, { 'stock balance query succeeded': (res) => res.status === 200 }) || recordBusinessFailure('stock_balance');
    if (response.status === 200) {
      recordBusinessSuccess();
    }
    paceRequests(scenario.workload_model.requests_per_minute_per_terminal);
    return;
  }

  const readResponse = requestJson(
    'GET',
    `${gatewayBaseUrl}/sale-orders/${actor.orderId}`,
    token,
    null,
    { kind: 'get_order', outlet: String(actor.outlet.outlet_id) }
  );
  check(readResponse, { 'get order succeeded': (res) => res.status === 200 }) || recordBusinessFailure('get_order');
  if (readResponse.status === 200) {
    recordBusinessSuccess();
  }
  paceRequests(scenario.workload_model.requests_per_minute_per_terminal);
}

export function runPaymentBurst(context) {
  runStatefulPos(context);
}

export function runInventoryEventStorm(context) {
  const gatewayBaseUrl = resolveGatewayBaseUrl(context.environment);
  const scenario = context.scenario;
  const actor = actorForScenario(context.seed, scenario, exec.scenario.name);
  const token = ensureActorToken(context, actor);

  if (exec.scenario.name === 'sale_terminals') {
    runStatefulPos(context);
    return;
  }

  if (exec.scenario.name === 'procurement_buyers') {
    const state = actor.procurementState || {};
    if (!state.purchaseOrderId) {
      const createPo = requestJson(
        'POST',
        `${gatewayBaseUrl}/purchase-orders`,
        token,
        {
          regionId: actor.outlet.region_id,
          outletId: actor.outlet.outlet_id,
          supplierId: context.seed.catalog.supplier_id,
          orderDate: scenario.timing.business_date,
          expectedDeliveryDate: scenario.timing.business_date,
          lines: [
            {
              ingredientId: context.seed.catalog.ingredient_id,
              uomCode: context.seed.catalog.base_uom_code,
              qtyOrdered: 5.0,
              expectedUnitPrice: 12500.0,
              taxPercent: 10.0
            }
          ]
        },
        { kind: 'create_po', outlet: String(actor.outlet.outlet_id) }
      );
      if (createPo.status === 200) {
        actor.procurementState = {
          purchaseOrderId: createPo.json('id'),
          purchaseOrderLineId: createPo.json('lines.0.id')
        };
        recordBusinessSuccess();
      } else {
        recordBusinessFailure('create_po');
      }
      paceRequests(10);
      return;
    }

    if (!state.issued) {
      requestJson('POST', `${gatewayBaseUrl}/purchase-orders/${state.purchaseOrderId}/submit`, token, null, { kind: 'submit_po' });
      requestJson('POST', `${gatewayBaseUrl}/purchase-orders/${state.purchaseOrderId}/approve`, token, null, { kind: 'approve_po' });
      const issued = requestJson('POST', `${gatewayBaseUrl}/purchase-orders/${state.purchaseOrderId}/issue`, token, null, { kind: 'issue_po' });
      if (issued.status === 200) {
        actor.procurementState.issued = true;
        actor.procurementState.purchaseOrderLineId = issued.json('lines.0.id');
        recordBusinessSuccess();
      } else {
        recordBusinessFailure('issue_po');
      }
      paceRequests(10);
      return;
    }

    if (!state.goodsReceiptId) {
      const createGr = requestJson(
        'POST',
        `${gatewayBaseUrl}/goods-receipts`,
        token,
        {
          purchaseOrderId: state.purchaseOrderId,
          receiptTime: `${scenario.timing.business_date}T10:00:00Z`,
          businessDate: scenario.timing.business_date,
          supplierLotNumber: randomId('lot'),
          lines: [
            {
              purchaseOrderLineId: state.purchaseOrderLineId,
              ingredientId: context.seed.catalog.ingredient_id,
              uomCode: context.seed.catalog.base_uom_code,
              qtyReceived: 3.0,
              unitCost: 12500.0
            }
          ]
        },
        { kind: 'create_gr', outlet: String(actor.outlet.outlet_id) }
      );
      if (createGr.status === 200) {
        actor.procurementState.goodsReceiptId = createGr.json('id');
        recordBusinessSuccess();
      } else {
        recordBusinessFailure('create_gr');
      }
      paceRequests(10);
      return;
    }

    if (!state.posted) {
      requestJson('POST', `${gatewayBaseUrl}/goods-receipts/${state.goodsReceiptId}/receive`, token, null, { kind: 'receive_gr' });
      const posted = requestJson(
        'POST',
        `${gatewayBaseUrl}/goods-receipts/${state.goodsReceiptId}/post`,
        token,
        null,
        { kind: 'post_gr', outlet: String(actor.outlet.outlet_id) },
        { 'Idempotency-Key': randomId('gr-post') }
      );
      if (posted.status === 200) {
        actor.procurementState = null;
        recordBusinessSuccess();
      } else {
        recordBusinessFailure('post_gr');
      }
      paceRequests(10);
      return;
    }
  }

  const adjustment = actor.inventoryState || {};
  if (!adjustment.adjustmentId) {
    const createAdjustment = requestJson(
      'POST',
      `${gatewayBaseUrl}/stock-adjustments`,
      token,
      {
        regionId: actor.outlet.region_id,
        outletId: actor.outlet.outlet_id,
        ingredientId: context.seed.catalog.ingredient_id,
        adjustmentDirection: 'IN',
        qty: 1.0,
        businessDate: scenario.timing.business_date,
        reason: 'LOAD_TEST'
      },
      { kind: 'create_stock_adjustment', outlet: String(actor.outlet.outlet_id) }
    );
    if (createAdjustment.status === 200) {
      actor.inventoryState = { adjustmentId: createAdjustment.json('id') };
      recordBusinessSuccess();
    } else {
      recordBusinessFailure('create_stock_adjustment');
    }
    paceRequests(10);
    return;
  }

  const postAdjustment = requestJson(
    'POST',
    `${gatewayBaseUrl}/stock-adjustments/${adjustment.adjustmentId}/post`,
    token,
    null,
    { kind: 'post_stock_adjustment', outlet: String(actor.outlet.outlet_id) },
    { 'Idempotency-Key': randomId('adjustment-post') }
  );
  if (postAdjustment.status === 200) {
    actor.inventoryState = null;
    recordBusinessSuccess();
  } else {
    recordBusinessFailure('post_stock_adjustment');
  }
  paceRequests(10);
}
