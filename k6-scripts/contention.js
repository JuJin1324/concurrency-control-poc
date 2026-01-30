import http from 'k6/http';
import { check } from 'k6';

/**
 * Contention Test (경합/안정성 측정)
 * 목적: 고정된 다수의 동시 접속자(VUs) 상황에서 시스템의 안정성 및 지연 시간 측정
 * 실행: k6 run -e METHOD=lua-script -e VUS=5000 -e DURATION=30s k6-scripts/contention.js
 */

export const options = {
  scenarios: {
    contention_test: {
      executor: 'constant-vus',
      vus: __ENV.VUS ? parseInt(__ENV.VUS) : 1000,
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.05'], // 에러율 5% 미만 권장
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const METHOD = __ENV.METHOD || 'pessimistic';

export default function () {
  const payload = JSON.stringify({ stockId: 1, amount: 1 });
  const params = { headers: { 'Content-Type': 'application/json' } };

  const res = http.post(`${BASE_URL}/api/stock/decrease?method=${METHOD}`, payload, params);

  check(res, {
    'is handled (not 5xx)': (r) => r.status < 500,
  });
}
