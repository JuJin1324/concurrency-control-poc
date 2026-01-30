import http from 'k6/http';

/**
 * Warm-up (예열)
 * 목적: JVM JIT 컴파일 및 Connection Pool 초기화를 위해 짧은 부하 발생
 * 실행: k6 run -e METHOD=lua-script k6-scripts/warmup.js
 */

export const options = {
  scenarios: {
    warmup: {
      executor: 'shared-iterations',
      vus: 10,
      iterations: 200,
      maxDuration: '30s',
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
