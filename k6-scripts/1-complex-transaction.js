import http from 'k6/http';
import { check } from 'k6';

/**
 * Scenario 1: Complex Transaction (Pessimistic Lock Best Fit)
 * 
 * 시나리오:
 * - 트랜잭션 하나가 약 50ms 소요 (외부 서비스 호출 시뮬레이션)
 * - 비관적 락이 50ms 동안 DB 로우를 점유
 * - 동시 요청이 올 경우 순차적으로 처리됨
 */

export const options = {
  scenarios: {
    complex_test: {
      executor: 'constant-vus',
      vus: 100, // 락 점유 시간이 길므로 VUs를 너무 높이지 않음
      duration: '30s',
    },
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const METHOD = __ENV.METHOD || 'pessimistic';

export default function () {
  // 특정 상품에 집중하여 경합 발생 (1-5번 상품)
  const randomStockId = Math.floor(Math.random() * 5) + 1;

  const payload = JSON.stringify({ stockId: randomStockId, amount: 1 });
  const params = { headers: { 'Content-Type': 'application/json' } };

  const res = http.post(`${BASE_URL}/api/bestfit/complex-transaction/decrease?method=${METHOD}`, payload, params);

  check(res, {
    'is success (200)': (r) => r.status === 200,
  });
}
