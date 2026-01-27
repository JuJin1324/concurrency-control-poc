package com.concurrency.poc.fixtures;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 동시성 테스트를 위한 공통 지원 클래스
 *
 * 목적: 동시성 테스트에서 반복적으로 사용되는 ExecutorService, CountDownLatch,
 *      AtomicInteger 관리 로직을 중앙화하여 코드 중복을 제거한다.
 *
 * 사용 예시:
 * <pre>
 * // 기본 설정으로 동시성 테스트 실행
 * ConcurrencyTestResult result = ConcurrencyTestSupport.executeConcurrentRequests(
 *     () -> stockService.decreaseStock(stockId, 1)
 * );
 *
 * // 커스텀 설정으로 실행
 * ConcurrencyTestConfig config = ConcurrencyTestConfig.withThreadCount(50);
 * ConcurrencyTestResult result = ConcurrencyTestSupport.executeConcurrentRequests(
 *     () -> stockService.decreaseStock(stockId, 1),
 *     config
 * );
 *
 * // 결과 검증
 * assertThat(result.getSuccessCount()).isEqualTo(100);
 * assertThat(result.getFailCount()).isZero();
 * </pre>
 */
public class ConcurrencyTestSupport {

    // 기본 설정값
    public static final int DEFAULT_THREAD_POOL_SIZE = 32;
    public static final int DEFAULT_THREAD_COUNT = 100;
    public static final int DEFAULT_DECREASE_AMOUNT = 1;

    /**
     * 동시성 테스트 설정
     *
     * @param threadCount 동시 실행할 스레드 수
     * @param threadPoolSize 스레드 풀 크기
     */
    public record ConcurrencyTestConfig(int threadCount, int threadPoolSize) {

        /**
         * 기본 설정 생성 (threadCount: 100, poolSize: 32)
         */
        public static ConcurrencyTestConfig withDefaults() {
            return new ConcurrencyTestConfig(DEFAULT_THREAD_COUNT, DEFAULT_THREAD_POOL_SIZE);
        }

        /**
         * 스레드 개수만 지정 (poolSize는 기본값 32)
         */
        public static ConcurrencyTestConfig withThreadCount(int threadCount) {
            return new ConcurrencyTestConfig(threadCount, DEFAULT_THREAD_POOL_SIZE);
        }
    }

    /**
     * 동시성 테스트 결과
     *
     * @param successCount 성공한 요청 수
     * @param failCount 실패한 요청 수
     * @param executionTimeMs 실행 시간 (밀리초)
     */
    public record ConcurrencyTestResult(int successCount, int failCount, long executionTimeMs) {

        /**
         * 전체 요청 수 계산
         */
        public int getTotalCount() {
            return successCount + failCount;
        }

        /**
         * 성공률 계산 (0.0 ~ 100.0)
         */
        public double getSuccessRate() {
            if (getTotalCount() == 0) {
                return 0.0;
            }
            return (successCount * 100.0) / getTotalCount();
        }

        /**
         * 테스트 결과를 콘솔에 출력
         */
        public void printResult(String testName) {
            System.out.printf("%n=== %s 테스트 결과 ===%n", testName);
            System.out.printf("성공: %d, 실패: %d%n", successCount, failCount);
            System.out.printf("Success Rate: %.1f%%%n", getSuccessRate());
            System.out.printf("실행 시간: %dms%n", executionTimeMs);
        }
    }

    /**
     * 동시성 테스트 실행
     *
     * @param task 각 스레드에서 실행할 작업 (예: () -> stockService.decreaseStock(id, 1))
     * @param config 동시성 테스트 설정
     * @return 테스트 결과 (성공 수, 실패 수, 실행 시간)
     * @throws InterruptedException 스레드 대기 중 인터럽트 발생 시
     */
    public static ConcurrencyTestResult executeConcurrentRequests(
            Runnable task,
            ConcurrencyTestConfig config) throws InterruptedException {

        ExecutorService executorService = Executors.newFixedThreadPool(config.threadPoolSize());
        CountDownLatch latch = new CountDownLatch(config.threadCount());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < config.threadCount(); i++) {
            executorService.submit(() -> {
                try {
                    task.run();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        long endTime = System.currentTimeMillis();

        return new ConcurrencyTestResult(
            successCount.get(),
            failCount.get(),
            endTime - startTime
        );
    }

    /**
     * 기본 설정으로 동시성 테스트 실행
     *
     * @param task 각 스레드에서 실행할 작업
     * @return 테스트 결과
     * @throws InterruptedException 스레드 대기 중 인터럽트 발생 시
     */
    public static ConcurrencyTestResult executeConcurrentRequests(Runnable task)
            throws InterruptedException {
        return executeConcurrentRequests(task, ConcurrencyTestConfig.withDefaults());
    }
}
