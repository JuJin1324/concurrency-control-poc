import http from 'k6/http';
import { check } from 'k6';

/**
 * Stress Test (시스템 한계 탐색)
 * 목적: 초당 요청 수(RPS)를 단계적으로 높이며 시스템이 붕괴되는 지점(Knee Point) 탐색
 * 실행: k6 run -e METHOD=lua-script -e TARGET_RPS=2000 k6-scripts/stress.js
 */

export const options = {
  scenarios: {
    stress_test: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 10000,
      stages: [
        { duration: '1m', target: __ENV.TARGET_RPS ? parseInt(__ENV.TARGET_RPS) / 4 : 500 },
        { duration: '2m', target: __ENV.TARGET_RPS ? parseInt(__ENV.TARGET_RPS) / 2 : 1000 },
        { duration: '1m', target: __ENV.TARGET_RPS ? parseInt(__ENV.TARGET_RPS) : 2000 },
        { duration: '30s', target: 0 },
      ],
    },
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const METHOD = __ENV.METHOD || 'pessimistic';

export default function () {
  const payload = JSON.stringify({ stockId: 1, amount: 1 });
  const params = { headers: { 'Content-Type': 'application/json' } };

  http.post(`${BASE_URL}/api/stock/decrease?method=${METHOD}`, payload, params);
}
