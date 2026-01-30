import http from 'k6/http';
import { sleep, check } from 'k6';

export const options = {
  vus: 1, // 1 가상 사용자
  duration: '5s', // 5초 동안 실행
};

export default function () {
  const res = http.get('https://test.k6.io');
  
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
  
  sleep(1);
}
