package org.springframework.jdbc.datasource.lookup.abstractds;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * Created by wangyingjie1 on 2016/2/21.
 */
public class DataSourceAspect {

    public void before(JoinPoint point) {
        Object target = point.getTarget();
        String method = point.getSignature().getName();

        Class<?>[] classz = target.getClass().getInterfaces();

        Class<?>[] parameterTypes = ((MethodSignature) point.getSignature())
                .getMethod().getParameterTypes();
        try {
            Method m = classz[0].getMethod(method, parameterTypes);
            if (m != null && m.isAnnotationPresent(DataSource.class)) {
                DataSource data = m.getAnnotation(DataSource.class);
                DynamicDataSourceHolder.putDataSource(data.value());
                System.out.println(data.value());
            }

        } catch (Exception e) {
            // TODO: handle exception
        }
    }
}
