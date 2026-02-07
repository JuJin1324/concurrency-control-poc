import http from 'k6/http';
import { check } from 'k6';

/**
 * Scenario 2: Low Contention Update (Optimistic Lock Best Fit)
 * 
 * 시나리오:
 * - 트랜잭션 하나가 약 50ms 소요되지만, 락을 잡지 않음
 * - 많은 요청이 동시에 50ms 동안 비즈니스 로직을 수행 가능 (병렬성 극대화)
 * - 마지막 업데이트 순간에만 버전 체크
 */

export const options = {
  scenarios: {
    low_contention_test: {
      executor: 'constant-vus',
      vus: __ENV.VUS ? parseInt(__ENV.VUS) : 100,
      duration: '30s',
    },
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const METHOD = __ENV.METHOD || 'optimistic';
const PRODUCT_COUNT = __ENV.PRODUCT_COUNT ? parseInt(__ENV.PRODUCT_COUNT) : 100;

export default function () {
  // 환경 변수로 조절되는 상품 수에 따라 분산
  const randomStockId = Math.floor(Math.random() * PRODUCT_COUNT) + 1;

  const payload = JSON.stringify({ stockId: randomStockId, amount: 1 });
  const params = { headers: { 'Content-Type': 'application/json' } };

  const res = http.post(`${BASE_URL}/api/low-contention/decrease?method=${METHOD}`, payload, params);

  check(res, {
    'is success (200)': (r) => r.status === 200,
  });
}