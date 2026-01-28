import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 대규모 트래픽 Stress Test 스크립트
 * 
 * 실행 방법:
 * k6 run -e METHOD=pessimistic k6-scripts/stress-test.js
 * k6 run -e METHOD=optimistic k6-scripts/stress-test.js
 * ...
 */

export const options = {
  scenarios: {
    stress_test: {
      executor: 'ramping-arrival-rate',
      startRate: 10,       // 초당 10개 요청으로 시작
      timeUnit: '1s',
      preAllocatedVUs: 100, // 초기 할당 VU
      maxVUs: __ENV.VUS ? parseInt(__ENV.VUS) : 5000,         // 최대 동시 접속자
      stages: [
        { duration: '1m', target: 500 },  // 1분 동안 초당 500개 요청까지 증가
        { duration: '2m', target: 1000 }, // 2분 동안 초당 1000개 요청까지 증가
        { duration: '1m', target: 2000 }, // 1분 동안 초당 2000개 요청까지 증가 (시스템 한계 도전)
        { duration: '30s', target: 0 },   // 쿨다운
      ],
    },
  },
  thresholds: {
    // 스트레스 테스트이므로 실패율보다는 성능 지표 모니터링에 집중
    'http_req_duration{expected_response:true}': ['p(95)<500'], 
    'http_req_failed': ['rate<0.95'], // 대규모 테스트 시 재고가 금방 소진되므로 실패율이 높을 수밖에 없음
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
  };

  const res = http.post(`${BASE_URL}/api/stock/decrease?method=${METHOD}`, payload, params);

  check(res, {
    'is status 200 or 409': (r) => r.status === 200 || r.status === 409,
    'is success field present': (r) => r.json().success !== undefined,
  });
}
