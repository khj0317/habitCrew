package com.habitcrew.support;

import java.util.function.BooleanSupplier;

/** 비동기로 처리되는 일(메일·알림)이 끝날 때까지 잠깐씩 기다린다 */
public final class Await {

    private Await() {
    }

    public static void until(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("5초 안에 조건이 만족되지 않았습니다.");
            }
            Thread.sleep(50);
        }
    }

    /** 비동기 작업이 "일어나지 않았음"을 확인하기 전에 충분히 기다린다 */
    public static void settle() throws InterruptedException {
        Thread.sleep(500);
    }
}
