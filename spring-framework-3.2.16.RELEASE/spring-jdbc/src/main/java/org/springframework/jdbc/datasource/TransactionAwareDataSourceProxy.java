/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.jdbc.datasource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

/**
 * Proxy for a target JDBC {@link javax.sql.DataSource}, adding awareness of
 * Spring-managed transactions. Similar to a transactional JNDI DataSource
 * as provided by a J2EE server.
 *
 * <p>Data access code that should remain unaware of Spring's data access support
 * can work with this proxy to seamlessly participate in Spring-managed transactions.
 * Note that the transaction manager, for example {@link DataSourceTransactionManager},
 * still needs to work with the underlying DataSource, <i>not</i> with this proxy.
 *
 * <p><b>Make sure that TransactionAwareDataSourceProxy is the outermost DataSource
 * of a chain of DataSource proxies/adapters.</b> TransactionAwareDataSourceProxy
 * can delegate either directly to the target connection pool or to some
 * intermediary proxy/adapter like {@link LazyConnectionDataSourceProxy} or
 * {@link UserCredentialsDataSourceAdapter}.
 *
 * <p>Delegates to {@link DataSourceUtils} for automatically participating in
 * thread-bound transactions, for example managed by {@link DataSourceTransactionManager}.
 * {@code getConnection} calls and {@code close} calls on returned Connections
 * will behave properly within a transaction, i.e. always operate on the transactional
 * Connection. If not within a transaction, normal DataSource behavior applies.
 *
 * <p>This proxy allows data access code to work with the plain JDBC API and still
 * participate in Spring-managed transactions, similar to JDBC code in a J2EE/JTA
 * environment. However, if possible, use Spring's DataSourceUtils, JdbcTemplate or
 * JDBC operation objects to get transaction participation even without a proxy for
 * the target DataSource, avoiding the need to define such a proxy in the first place.
 *
 * <p>As a further effect, using a transaction-aware DataSource will apply remaining
 * transaction timeouts to all created JDBC (Prepared/Callable)Statement. This means
 * that all operations performed through standard JDBC will automatically participate
 * in Spring-managed transaction timeouts.
 *
 * <p><b>NOTE:</b> This DataSource proxy needs to return wrapped Connections
 * (which implement the {@link ConnectionProxy} interface) in order to handle
 * close calls properly. Therefore, the returned Connections cannot be cast
 * to a native JDBC Connection type such as OracleConnection or to a connection
 * pool implementation type. Use a corresponding
 * {@link org.springframework.jdbc.support.nativejdbc.NativeJdbcExtractor}
 * or JDBC 4's {@link Connection#unwrap} to retrieve the native JDBC Connection.
 *
 * 总结流程:
 * Spring 对DataSource进行事务管理的关键在于ConnectionHolder和TransactionSynchronizationManager。
 * 0.先从TransactionSynchronizationManager中尝试获取连接
 * 1.如果前一步失败则在每个线程上，对每个DataSouce只创建一个Connection
 * 2.这个Connection用ConnectionHolder包装起来，由TransactionSynchronizationManager管理
 * 3.再次请求同一个连接的时候，从TransactionSynchronizationManager返回已经创建的ConnectionHolder，然后调用ConnectionHolder的request将引用计数+1
 * 4.释放连接时要调用ConnectionHolder的released，将引用计数-1
 * 5.当事物完成后，将ConnectionHolder从TransactionSynchronizationManager中解除。当谁都不用，这个连接被close
 *
 * 以上所有都是可以调用DataSourceUtils化简代码，而JdbcTemplate又是调用DataSourceUtils的。所以在Spring文档中要求尽量首先使用JdbcTemplate，
 * 其次是用DataSourceUtils来获取和释放连接。至于TransactionAwareDataSourceProxy，那是下策的下策。不过可以将Spring事务管理和遗留代码无缝集成。
 *
 * 所以如某位朋友说要使用Spring的事务管理，但是又不想用JdbcTemplate，那么可以考虑TransactionAwareDataSourceProxy。这个类是原来DataSource的代理。
 *
 *
 * @author Juergen Hoeller
 * @since 1.1
 * @see javax.sql.DataSource#getConnection()
 * @see java.sql.Connection#close()
 * @see DataSourceUtils#doGetConnection
 * @see DataSourceUtils#applyTransactionTimeout
 * @see DataSourceUtils#doReleaseConnection
 */
public class TransactionAwareDataSourceProxy extends DelegatingDataSource {

	private boolean reobtainTransactionalConnections = false;


	/**
	 * Create a new TransactionAwareDataSourceProxy.
	 * @see #setTargetDataSource
	 */
	public TransactionAwareDataSourceProxy() {
	}

	/**
	 * Create a new TransactionAwareDataSourceProxy.
	 * @param targetDataSource the target DataSource
	 */
	public TransactionAwareDataSourceProxy(DataSource targetDataSource) {
		super(targetDataSource);
	}

	/**
	 * Specify whether to reobtain the target Connection for each operation
	 * performed within a transaction.
	 * <p>The default is "false". Specify "true" to reobtain transactional
	 * Connections for every call on the Connection proxy; this is advisable
	 * on JBoss if you hold on to a Connection handle across transaction boundaries.
	 * <p>The effect of this setting is similar to the
	 * "hibernate.connection.release_mode" value "after_statement".
	 */
	public void setReobtainTransactionalConnections(boolean reobtainTransactionalConnections) {
		this.reobtainTransactionalConnections = reobtainTransactionalConnections;
	}


	/**
	 * Delegates to DataSourceUtils for automatically participating in Spring-managed
	 * transactions. Throws the original SQLException, if any.
	 * <p>The returned Connection handle implements the ConnectionProxy interface,
	 * allowing to retrieve the underlying target Connection.
	 * @return a transactional Connection if any, a new one else
	 * @see DataSourceUtils#doGetConnection
	 * @see ConnectionProxy#getTargetConnection
	 *
	 * 获取数据源链接，万变不离其宗，最终都是从  DataSourceUtils#doGetConnection  获取
	 */
	@Override
	public Connection getConnection() throws SQLException {
		DataSource ds = getTargetDataSource();
		Assert.state(ds != null, "'targetDataSource' is required");
		//获取代理链接
		return getTransactionAwareConnectionProxy(ds);
	}

	/**
	 * Wraps the given Connection with a proxy that delegates every method call to it
	 * but delegates {@code close()} calls to DataSourceUtils.
	 * @param targetDataSource DataSource that the Connection came from
	 * @return the wrapped Connection
	 * @see java.sql.Connection#close()
	 * @see DataSourceUtils#doReleaseConnection
	 */
	protected Connection getTransactionAwareConnectionProxy(DataSource targetDataSource) {
		//返回的是jdk的动态代理
		return (Connection) Proxy.newProxyInstance(
				ConnectionProxy.class.getClassLoader(),
				new Class[] {ConnectionProxy.class},
				new TransactionAwareInvocationHandler(targetDataSource));
	}

	/**
	 * Determine whether to obtain a fixed target Connection for the proxy
	 * or to reobtain the target Connection for each operation.
	 * <p>The default implementation returns {@code true} for all
	 * standard cases. This can be overridden through the
	 * {@link #setReobtainTransactionalConnections "reobtainTransactionalConnections"}
	 * flag, which enforces a non-fixed target Connection within an active transaction.
	 * Note that non-transactional access will always use a fixed Connection.
	 * @param targetDataSource the target DataSource
	 */
	protected boolean shouldObtainFixedConnection(DataSource targetDataSource) {
		return (!TransactionSynchronizationManager.isSynchronizationActive() ||
				!this.reobtainTransactionalConnections);
	}


	/**
	 * Invocation handler that delegates close calls on JDBC Connections
	 * to DataSourceUtils for being aware of thread-bound transactions.
	 */
	private class TransactionAwareInvocationHandler implements InvocationHandler {

		private final DataSource targetDataSource;

		private Connection target;

		private boolean closed = false;

		public TransactionAwareInvocationHandler(DataSource targetDataSource) {
			this.targetDataSource = targetDataSource;
		}

		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			// Invocation on ConnectionProxy interface coming in...

			if (method.getName().equals("equals")) {
				// Only considered as equal when proxies are identical.
				return (proxy == args[0]);
			}
			else if (method.getName().equals("hashCode")) {
				// Use hashCode of Connection proxy.
				return System.identityHashCode(proxy);
			}
			else if (method.getName().equals("toString")) {
				// Allow for differentiating between the proxy and the raw Connection.
				StringBuilder sb = new StringBuilder("Transaction-aware proxy for target Connection ");
				if (this.target != null) {
					sb.append("[").append(this.target.toString()).append("]");
				}
				else {
					sb.append(" from DataSource [").append(this.targetDataSource).append("]");
				}
				return sb.toString();
			}
			else if (method.getName().equals("unwrap")) {
				if (((Class) args[0]).isInstance(proxy)) {
					return proxy;
				}
			}
			else if (method.getName().equals("isWrapperFor")) {
				if (((Class) args[0]).isInstance(proxy)) {
					return true;
				}
			}
			else if (method.getName().equals("close")) {
				// Handle close method: only close if not within a transaction.
				DataSourceUtils.doReleaseConnection(this.target, this.targetDataSource);
				this.closed = true;
				return null;
			}
			else if (method.getName().equals("isClosed")) {
				return this.closed;
			}

			if (this.target == null) {
				if (this.closed) {
					throw new SQLException("Connection handle already closed");
				}
				if (shouldObtainFixedConnection(this.targetDataSource)) {
					// 数据源链接获取工具
					this.target = DataSourceUtils.doGetConnection(this.targetDataSource);
				}
			}
			Connection actualTarget = this.target;
			if (actualTarget == null) {
				// 数据源链接获取工具
				actualTarget = DataSourceUtils.doGetConnection(this.targetDataSource);
			}

			if (method.getName().equals("getTargetConnection")) {
				// underlying 潜在的链接
				// Handle getTargetConnection method: return underlying Connection.
				return actualTarget;
			}

			// Invoke method on target Connection.
			try {
				Object retVal = method.invoke(actualTarget, args);

				// If return value is a Statement, apply transaction timeout.
				// Applies to createStatement, prepareStatement, prepareCall.
				if (retVal instanceof Statement) {
					DataSourceUtils.applyTransactionTimeout((Statement) retVal, this.targetDataSource);
				}

				return retVal;
			}
			catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
			finally {
				if (actualTarget != this.target) {
					DataSourceUtils.doReleaseConnection(actualTarget, this.targetDataSource);
				}
			}
		}
	}

}
