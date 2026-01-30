import http from 'k6/http';
import { check } from 'k6';

/**
 * Capacity Test (처리량 측정)
 * 목적: 특정 작업량(Iterations)을 얼마나 빨리 처리하는지(TPS) 측정
 * 실행: k6 run -e METHOD=lua-script -e VUS=100 -e ITERATIONS=1000 k6-scripts/capacity.js
 */

export const options = {
  scenarios: {
    capacity_test: {
      executor: 'shared-iterations',
      vus: __ENV.VUS ? parseInt(__ENV.VUS) : 100,
      iterations: __ENV.ITERATIONS ? parseInt(__ENV.ITERATIONS) : 1000,
      maxDuration: '10m',
    },
  },
  thresholds: {
    'http_req_duration{expected_response:true}': ['p(95)<500'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const METHOD = __ENV.METHOD || 'pessimistic';

export default function () {
  const payload = JSON.stringify({ stockId: 1, amount: 1 });
  const params = { headers: { 'Content-Type': 'application/json' } };

  const res = http.post(`${BASE_URL}/api/stock/decrease?method=${METHOD}`, payload, params);

  const success = check(res, {
    'is success (200/409)': (r) => r.status === 200 || r.status === 409,
  });

  if (!success) {
    console.log(`Failed: Status=${res.status}, Body=${res.body}`);
  }
}
