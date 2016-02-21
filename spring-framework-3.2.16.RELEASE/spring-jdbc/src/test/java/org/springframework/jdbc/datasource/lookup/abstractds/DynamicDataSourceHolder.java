package org.springframework.jdbc.datasource.lookup.abstractds;

/**
 * Created by wangyingjie1 on 2016/2/21.
 */
public class DynamicDataSourceHolder  {

    public static final String DATA_SOURCE_A = "dataSource";
    public static final String DATA_SOURCE_B = "dataSource2";
    public static final ThreadLocal<String> holder = new ThreadLocal<String>();

    //下面是我们实现的核心部分，也就是AOP部分，DataSourceAspect定义如下:
    public static void putDataSource(String name) {
        holder.set(name);
    }

    public static String getDataSouce() {
        return holder.get();
    }

    public static void clearHolder() {
        holder.remove();
    }
}