import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * [Best Fit Scenario 3] Resource Protection (Redis Lock + Optimistic Lock)
 * - 목표: 고부하(500 VUs) 상황에서 DB Connection Pool(10) 고갈 방지 및 안정성 증명
 * - 비교: 
 *   1. Pessimistic: DB Connection을 잡은 채로 Lock을 대기하여 Pool 고갈 유발
 *   2. Redis-Optimistic: Redis에서 대기하며 꼭 필요한 순간에만 Connection 점유
 */

export const options = {
    scenarios: {
        resource_protection: {
            executor: 'constant-vus',
            vus: 500,
            duration: '20s',
        },
    },
    thresholds: {
        http_req_failed: ['rate < 1.0'], // 에러율 관찰
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const METHOD = __ENV.METHOD || 'pessimistic'; // 'pessimistic' or 'redis-optimistic'

export default function () {
    const url = `${BASE_URL}/api/resource-protection/decrease?method=${METHOD}`;
    
    // 5개의 서로 다른 상품에 대해 요청을 분산시켜 Connection 경합 유도
    const stockId = Math.floor(Math.random() * 5) + 1;

    const payload = JSON.stringify({
        stockId: stockId,
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
        'is status 409 (Stock Out)': (r) => r.status === 409,
        'is status 500 (Technical Error)': (r) => r.status === 500,
    });

    // 너무 빠른 요청 방지를 위해 약간의 휴식
    sleep(0.1);
}
