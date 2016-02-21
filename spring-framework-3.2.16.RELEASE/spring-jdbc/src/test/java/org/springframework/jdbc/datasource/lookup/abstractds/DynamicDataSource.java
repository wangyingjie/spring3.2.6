package org.springframework.jdbc.datasource.lookup.abstractds;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Created by wangyingjie1 on 2016/2/21.
 *
 *   从DynamicDataSource 的定义看出，他返回的是DynamicDataSourceHolder.getDataSouce()值，
 *   我们需要在程序运行时调用DynamicDataSourceHolder.putDataSource()方法，对其赋值。
 *
 *
 */
public class DynamicDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        // TODO Auto-generated method stub
        return DynamicDataSourceHolder.getDataSouce();
    }

}

