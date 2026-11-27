package com.habitcrew.support;

import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * 테스트용: 실제로 DB에 보낸 SQL 문장 수를 센다 (JPA·JdbcTemplate 모두).
 * DataSource를 감싸서 Connection의 prepareStatement·createStatement·prepareCall 호출을 센다.
 * 현재 스레드에서 count(...)로 감싼 구간만 센다 (MockMvc 요청은 같은 스레드에서 처리된다).
 */
public class QueryCounter implements BeanPostProcessor {

    private static final Set<String> STATEMENT_METHODS = Set.of("prepareStatement", "createStatement", "prepareCall");
    private static final ThreadLocal<int[]> COUNTER = new ThreadLocal<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && !Proxy.isProxyClass(bean.getClass())) {
            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DataSource.class},
                    forward(dataSource, (proxy, method, args, result) ->
                            "getConnection".equals(method.getName()) ? wrap((Connection) result) : result));
        }
        return bean;
    }

    public static <T> Result<T> count(Callable<T> work) throws Exception {
        COUNTER.set(new int[1]);
        try {
            T value = work.call();
            return new Result<>(value, COUNTER.get()[0]);
        } finally {
            COUNTER.remove();
        }
    }

    public record Result<T>(T value, int queries) {
    }

    private static Connection wrap(Connection connection) {
        return (Connection) Proxy.newProxyInstance(QueryCounter.class.getClassLoader(), new Class<?>[]{Connection.class},
                forward(connection, (proxy, method, args, result) -> {
                    int[] counter = COUNTER.get();
                    if (counter != null && STATEMENT_METHODS.contains(method.getName())) {
                        counter[0]++;
                    }
                    return result;
                }));
    }

    private interface After {
        Object apply(Object proxy, java.lang.reflect.Method method, Object[] args, Object result) throws Exception;
    }

    private static InvocationHandler forward(Object target, After after) {
        return (proxy, method, args) -> {
            if ("unwrap".equals(method.getName()) || "isWrapperFor".equals(method.getName())) {
                return method.invoke(target, args);
            }
            try {
                return after.apply(proxy, method, args, method.invoke(target, args));
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
    }
}
