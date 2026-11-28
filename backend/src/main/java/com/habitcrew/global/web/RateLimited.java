package com.habitcrew.global.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API를 한 사람(로그인 전이면 IP)이 정해진 시간 안에 몇 번까지 부를 수 있는지.
 * 예) @RateLimited(name = "comment", limit = 20, windowSeconds = 60): 1분에 댓글 20개까지
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    String name();

    int limit();

    int windowSeconds() default 60;
}
