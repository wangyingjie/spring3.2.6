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

package org.springframework.web.method.annotation;

import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestScope;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.ServletException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for resolving method arguments from a named value.
 * Request parameters, request headers, and path variables are examples of named
 * values. Each may have a name, a required flag, and a default value.
 * <p>Subclasses define how to do the following:
 * <ul>
 * <li>Obtain named value information for a method parameter
 * <li>Resolve names into argument values
 * <li>Handle missing argument values when argument values are required
 * <li>Optionally handle a resolved value
 * </ul>
 * <p>A default value string can contain ${...} placeholders and Spring Expression
 * Language #{...} expressions. For this to work a
 * {@link ConfigurableBeanFactory} must be supplied to the class constructor.
 * <p>A {@link WebDataBinder} is created to apply type conversion to the resolved
 * argument value if it doesn't match the method parameter type.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 *
 *  解析 NameValue类型的参数，如：cookie、requestParam、requestHeader 类型
 */
public abstract class AbstractNamedValueMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private final ConfigurableBeanFactory configurableBeanFactory;

	private final BeanExpressionContext expressionContext;

	private Map<MethodParameter, NamedValueInfo> namedValueInfoCache =
			new ConcurrentHashMap<MethodParameter, NamedValueInfo>(256);

	/**
	 * @param beanFactory a bean factory to use for resolving ${...} placeholder
	 * and #{...} SpEL expressions in default values, or {@code null} if default
	 * values are not expected to contain expressions
	 */
	public AbstractNamedValueMethodArgumentResolver(ConfigurableBeanFactory beanFactory) {
		this.configurableBeanFactory = beanFactory;
		this.expressionContext = (beanFactory != null) ? new BeanExpressionContext(beanFactory, new RequestScope()) : null;
	}

	@Override
	public final Object resolveArgument(
			MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory)
			throws Exception {

		Class<?> paramType = parameter.getParameterType();

		// 获取NameValueInfo
		NamedValueInfo namedValueInfo = getNamedValueInfo(parameter);

		// 解析参数
		Object arg = resolveName(namedValueInfo.name, parameter, webRequest);

		// 如果没有解析到参数
		if (arg == null) {
			if (namedValueInfo.defaultValue != null) {

				//使用默认解析的值0
				arg = resolveDefaultValue(namedValueInfo.defaultValue);
			} else {
				if (namedValueInfo.required) {
					handleMissingValue(namedValueInfo.name, parameter);
				}
			}
			arg = handleNullValue(namedValueInfo.name, arg, paramType);
		}
		else if ("".equals(arg) && (namedValueInfo.defaultValue != null)) {
			arg = resolveDefaultValue(namedValueInfo.defaultValue);
		}

		// 从 binderFactory 中解析
		if (binderFactory != null) {
			WebDataBinder binder = binderFactory.createBinder(webRequest, null, namedValueInfo.name);

			//做必要的转换
			arg = binder.convertIfNecessary(arg, paramType, parameter);
		}

		//对解析的参数进行后置处理，扩展的模板方法
		handleResolvedValue(arg, namedValueInfo.name, parameter, mavContainer, webRequest);

		return arg;
	}

	/**
	 * Obtain the named value for the given method parameter.
	 */
	private NamedValueInfo getNamedValueInfo(MethodParameter parameter) {
		NamedValueInfo namedValueInfo = this.namedValueInfoCache.get(parameter);

		//cache 未找到
		if (namedValueInfo == null) {

			// 子类都使用了继承父类的 NamedValueInfo 重写调用父类构造方法的实现创建了 NamedValueInfo 对象
			// 以上的代码设计风格可以借鉴

			//创建之
			namedValueInfo = createNamedValueInfo(parameter);

			//更新之
			namedValueInfo = updateNamedValueInfo(parameter, namedValueInfo);

			//放缓存
			this.namedValueInfoCache.put(parameter, namedValueInfo);
		}
		return namedValueInfo;
	}

	/**
	 * Create the {@link NamedValueInfo} object for the given method parameter. Implementations typically
	 * retrieve the method annotation by means of {@link MethodParameter#getParameterAnnotation(Class)}.
	 * @param parameter the method parameter
	 * @return the named value information
	 */
	protected abstract NamedValueInfo createNamedValueInfo(MethodParameter parameter);

	/**
	 * Create a new NamedValueInfo based on the given NamedValueInfo with sanitized values.
	 */
	private NamedValueInfo updateNamedValueInfo(MethodParameter parameter, NamedValueInfo info) {
		String name = info.name;
		if (info.name.length() == 0) {
			name = parameter.getParameterName();
			Assert.notNull(name, "Name for argument type [" + parameter.getParameterType().getName()
						+ "] not available, and parameter name information not found in class file either.");
		}
		String defaultValue = (ValueConstants.DEFAULT_NONE.equals(info.defaultValue) ? null : info.defaultValue);
		return new NamedValueInfo(name, info.required, defaultValue);
	}

	/**
	 * Resolves the given parameter type and value name into an argument value.
	 * @param name the name of the value being resolved
	 * @param parameter the method parameter to resolve to an argument value
	 * @param request the current request
	 * @return the resolved argument. May be {@code null}
	 * @throws Exception in case of errors
	 */
	protected abstract Object resolveName(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception;

	/**
	 * Resolves the given default value into an argument value.
	 */
	private Object resolveDefaultValue(String defaultValue) {
		if (this.configurableBeanFactory == null) {
			return defaultValue;
		}

		// 默认值中有 占位符，则要替换之
		String placeholdersResolved = this.configurableBeanFactory.resolveEmbeddedValue(defaultValue);

		//
		BeanExpressionResolver exprResolver = this.configurableBeanFactory.getBeanExpressionResolver();
		if (exprResolver == null) {
			return defaultValue;
		}
		return exprResolver.evaluate(placeholdersResolved, this.expressionContext);
	}

	/**
	 * Invoked when a named value is required, but {@link #resolveName(String, MethodParameter, NativeWebRequest)}
	 * returned {@code null} and there is no default value. Subclasses typically throw an exception in this case.
	 * @param name the name for the value
	 * @param parameter the method parameter
	 *
	 * 模板方法
	 */
	protected abstract void handleMissingValue(String name, MethodParameter parameter) throws ServletException;

	/**
	 * A {@code null} results in a {@code false} value for {@code boolean}s or an exception for other primitives.
	 */
	private Object handleNullValue(String name, Object value, Class<?> paramType) {

		// 即没有返回值也没有默认值
		if (value == null) {

			// 如果类型为  boolean  则直接返回 false  否则抛异常
			if (Boolean.TYPE.equals(paramType)) {
				return Boolean.FALSE;
			} else if (paramType.isPrimitive()) {
				throw new IllegalStateException("Optional " + paramType + " parameter '" + name +
						"' is present but cannot be translated into a null value due to being declared as a " +
						"primitive type. Consider declaring it as object wrapper for the corresponding primitive type.");
			}
		}
		return value;
	}

	/**
	 * Invoked after a value is resolved.
	 * @param arg the resolved argument value
	 * @param name the argument name
	 * @param parameter the argument parameter type
	 * @param mavContainer the {@link ModelAndViewContainer}, which may be {@code null}
	 * @param webRequest the current request
	 *
	 * 子类扩展
	 */
	protected void handleResolvedValue(Object arg, String name, MethodParameter parameter,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) {
	}


	/**
	 * Represents the information about a named value, including name, whether it's required and a default value.
	 */
	protected static class NamedValueInfo {

		private final String name;

		private final boolean required;

		private final String defaultValue;

		protected NamedValueInfo(String name, boolean required, String defaultValue) {
			this.name = name;
			this.required = required;
			this.defaultValue = defaultValue;
		}
	}

}
