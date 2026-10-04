package com.zifang.z.surl.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * 测试用 JDK 动态代理工厂 —— 本仓单测的 house style.
 * <p>
 * <b>为什么不用 Mockito</b>（007 步骤 8 踩坑 #17）：mock mybatis-plus {@code BaseMapper} 时
 * 链式调用 {@code .selectCount(...).eq(...).last(...)} 容易因 matcher 错位返回 null，
 * 业务断言 NPE 翻车。按方法名 dispatch 到对应 handler，每条方法单独控制返回值，
 * 可读性远高于嵌套 stub，而且组织规范明确要求 0 Mockito。
 * <p>
 * 本仓的 {@code SurlServiceMySqlImpl} 用**构造器注入**，所以测试直接 new 即可，
 * 不需要反射注入 —— 也就没有 {@code Proxies.inject}。若将来某个实现类改成
 * 无参构造 + 字段注入，再把反射注入那一格加回来。
 */
public final class Proxies {

    private Proxies() {
    }

    /**
     * 为接口造一个代理：{@code handlers} 按**方法名**分派；未登记的方法返回类型默认值
     * （引用类型 null、原始类型零值），不会因为漏 stub 而 NPE。
     */
    @SuppressWarnings("unchecked")
    public static <T> T of(Class<T> iface, Map<String, InvocationHandler> handlers) {
        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                (proxy, method, args) -> {
                    InvocationHandler handler = handlers.get(method.getName());
                    if (handler != null) {
                        return handler.invoke(proxy, method, args);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        return 0d;
    }
}
