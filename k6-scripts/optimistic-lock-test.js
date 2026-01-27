import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    spike_test: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '10s',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    // Optimistic Lock은 충돌 시 실패할 수 있으므로 성공률 기준을 다르게 가져갈 수 있음
    // 하지만 우리 PoC의 목적은 정합성이므로 실패율을 모니터링함
    http_req_failed: ['rate<0.5'], // 낙관적 락은 충돌이 많을 것으로 예상
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const payload = JSON.stringify({
    stockId: 1,
    amount: 1,
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post(`${BASE_URL}/api/stock/decrease?method=optimistic`, payload, params);

  check(res, {
    'is status 200': (r) => r.status === 200,
  });
}
