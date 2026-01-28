import http from 'k6/http';
import { check } from 'k6';

/**
 * Recovery Test: 시스템 회복력 검증
 * 
 * 목적: 과부하 테스트 이후 리소스 누수 없이 정상 성능으로 복구되는지 확인
 * 규모: 재고 100개 / 100명 접속 (Shared Iterations)
 */

export const options = {
  scenarios: {
    recovery_test: {
      executor: 'shared-iterations',
      vus: 10,
      iterations: 100,
      maxDuration: '10s',
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<100'], // 회복 후엔 다시 매우 빨라야 함 (100ms 이내)
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const METHOD = __ENV.METHOD || 'pessimistic';

export default function () {
  const payload = JSON.stringify({ stockId: 1, amount: 1 });
  const params = { headers: { 'Content-Type': 'application/json' } };
  const res = http.post(`${BASE_URL}/api/stock/decrease?method=${METHOD}`, payload, params);

  check(res, {
    'is status 200 or 409': (r) => r.status === 200 || r.status === 409,
  });
}
