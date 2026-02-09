import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * [Scenario 4] Redis as Primary Storage (Lua Script)
 * - 목적: DB 의존도를 제거한 Lua Script와 타 방식의 순수 성능(No Delay) 비교
 * - 환경: 100ms 지연 없음, DB Pool 10, 500 VUs
 */

export const options = {
    scenarios: {
        extreme_performance: {
            executor: 'constant-vus',
            vus: 500,
            duration: '20s',
        },
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const METHOD = __ENV.METHOD || 'lua';

export default function () {
    const url = `${BASE_URL}/api/extreme-performance/decrease?method=${METHOD}`;
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
        'is success (200 or 409)': (r) => r.status === 200 || r.status === 409,
    });
}
