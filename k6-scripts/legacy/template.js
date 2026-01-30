import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * k6 부하 테스트 템플릿
 * 
 * 주요 설정:
 * - stages: 부하를 점진적으로 증가시키거나 유지
 * - thresholds: 테스트 합격/불합격 기준 (성공률, 응답 시간)
 */
export const options = {
  stages: [
    { duration: '30s', target: 20 }, // 30초 동안 VU를 0에서 20까지 증가 (Ramp-up)
    { duration: '1m', target: 20 },  // 1분 동안 20 VU 유지
    { duration: '30s', target: 0 },  // 30초 동안 0으로 감소 (Ramp-down)
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],   // 에러율 1% 미만
    http_req_duration: ['p(95)<500'], // 95% 응답 시간 500ms 미만
  },
};

// 테스트 대상 서버 URL (환경 변수 또는 기본값)
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

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

  // 재고 차감 API 호출 (Pessimistic Lock 예시)
  const res = http.post(`${BASE_URL}/api/stock/decrease?method=pessimistic`, payload, params);

  // 결과 검증
  check(res, {
    'is status 200': (r) => r.status === 200,
    'has remaining quantity': (r) => r.json().remainingQuantity !== undefined,
  });

  sleep(0.1); // 100ms 대기 (현실적인 요청 간격)
}
