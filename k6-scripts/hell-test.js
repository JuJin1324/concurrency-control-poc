import http from 'k6/http';
import { check } from 'k6';

/**
 * Hell Test: 선착순 이벤트 시나리오
 * 
 * 목표: 재고 100개 vs 유저 10,000명 (Extreme Contention)
 * 상황: 10,000명이 동시에 접속하여 구매 버튼을 클릭하는 상황 재현
 * 
 * 실행 방법:
 * k6 run -e METHOD=pessimistic k6-scripts/hell-test.js
 */

export const options = {
  scenarios: {
    hell_test: {
      executor: 'shared-iterations',
      vus: __ENV.VUS ? parseInt(__ENV.VUS) : 10000,
      iterations: __ENV.ITERATIONS ? parseInt(__ENV.ITERATIONS) : 10000,
      maxDuration: '30s',
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<1000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const METHOD = __ENV.METHOD || 'pessimistic';

export default function () {
  const payload = JSON.stringify({
    stockId: 1,
    amount: 1,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    // 타임아웃 설정 (서버가 터지지 않는지 확인하기 위함)
    timeout: '10s', 
  };

  const res = http.post(`${BASE_URL}/api/stock/decrease?method=${METHOD}`, payload, params);

  check(res, {
    'status is 200 (Success) or 409 (Sold Out)': (r) => r.status === 200 || r.status === 409,
    'handled gracefully': (r) => r.status !== 500 && r.status !== 504, // 500 에러나 타임아웃은 없어야 함
  });
}
