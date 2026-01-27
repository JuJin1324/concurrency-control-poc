import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    spike_test: {
      executor: 'constant-arrival-rate',
      rate: 100, // 초당 100개 요청
      timeUnit: '1s',
      duration: '10s', // 10초 동안 총 1000개 요청
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'], // DB Lock 대기로 인해 지연 시간이 길어질 수 있음
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

  const res = http.post(`${BASE_URL}/api/stock/decrease?method=pessimistic`, payload, params);

  check(res, {
    'is status 200': (r) => r.status === 200,
    'success is true': (r) => r.json().success === true,
  });
}
