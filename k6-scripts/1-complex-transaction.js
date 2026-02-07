import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * [Best Fit Scenario 1] Complex Transaction
 * - 목표: 복잡한 트랜잭션(Stock + Point + OrderHistory) 상황에서 비관적 락의 안정성 증명
 * - 설정: 50명의 가상 사용자가 동시에 1개 상품/유저에 대해 요청 (트랜잭션당 100ms 소요)
 */

export const options = {
    scenarios: {
        complex_transaction: {
            executor: 'constant-vus',
            vus: 50,
            duration: '15s',
        },
    },
    thresholds: {
        http_req_failed: ['rate < 1.0'], // 에러율 관찰
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const METHOD = __ENV.METHOD || 'pessimistic'; // 'pessimistic' or 'optimistic'

export default function () {
    const url = `${BASE_URL}/api/bestfit/complex-transaction/decrease?method=${METHOD}`;
    const payload = JSON.stringify({
        stockId: 1,
        amount: 1,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const res = http.post(url, payload, params);

    check(res, {
        'is status 200': (r) => r.status === 200,
    });

    // 너무 빠른 요청 방지를 위해 약간의 휴식 (VUs 간의 경합 조절)
    sleep(0.1);
}
