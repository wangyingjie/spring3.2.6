/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.web.servlet.mvc.method.annotation;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.OrderComparator;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.annotation.MapMethodProcessor;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.method.annotation.ModelMethodProcessor;
import org.springframework.web.method.support.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.handler.AbstractHandlerMethodExceptionResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Source;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@link AbstractHandlerMethodExceptionResolver} that resolves exceptions
 * through {@code @ExceptionHandler} methods.
 *
 * <p>Support for custom argument and return value types can be added via
 * {@link #setCustomArgumentResolvers} and {@link #setCustomReturnValueHandlers}.
 * Or alternatively to re-configure all argument and return value types use
 * {@link #setArgumentResolvers} and {@link #setReturnValueHandlers(List)}.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 *
 * 完成使用 @ExceptionHandler 注解的方法进行异常解析
 *
 * todo 注意：其实 ExceptionHandlerExceptionResolver 就是一个简化版的 RequestMappingHandlerAdapter
 */
public class ExceptionHandlerExceptionResolver extends AbstractHandlerMethodExceptionResolver
		implements ApplicationContextAware, InitializingBean {

	private List<HandlerMethodArgumentResolver> customArgumentResolvers;

	private HandlerMethodArgumentResolverComposite argumentResolvers;

	private List<HandlerMethodReturnValueHandler> customReturnValueHandlers;

	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;

	private List<HttpMessageConverter<?>> messageConverters;

	private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();

	private ApplicationContext applicationContext;

	private final Map<Class<?>, ExceptionHandlerMethodResolver> exceptionHandlerCache =
			new ConcurrentHashMap<Class<?>, ExceptionHandlerMethodResolver>(64);

	private final Map<ControllerAdviceBean, ExceptionHandlerMethodResolver> exceptionHandlerAdviceCache =
			new LinkedHashMap<ControllerAdviceBean, ExceptionHandlerMethodResolver>();


	public ExceptionHandlerExceptionResolver() {
		StringHttpMessageConverter stringHttpMessageConverter = new StringHttpMessageConverter();
		stringHttpMessageConverter.setWriteAcceptCharset(false); // See SPR-7316

		this.messageConverters = new ArrayList<HttpMessageConverter<?>>();
		this.messageConverters.add(new ByteArrayHttpMessageConverter());
		this.messageConverters.add(stringHttpMessageConverter);
		this.messageConverters.add(new SourceHttpMessageConverter<Source>());
		this.messageConverters.add(new AllEncompassingFormHttpMessageConverter());
	}


	/**
	 * Provide resolvers for custom argument types. Custom resolvers are ordered
	 * after built-in ones. To override the built-in support for argument
	 * resolution use {@link #setArgumentResolvers} instead.
	 */
	public void setCustomArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		this.customArgumentResolvers= argumentResolvers;
	}

	/**
	 * Return the custom argument resolvers, or {@code null}.
	 */
	public List<HandlerMethodArgumentResolver> getCustomArgumentResolvers() {
		return this.customArgumentResolvers;
	}

	/**
	 * Configure the complete list of supported argument types thus overriding
	 * the resolvers that would otherwise be configured by default.
	 */
	public void setArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.argumentResolvers = null;
		}
		else {
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.argumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the configured argument resolvers, or possibly {@code null} if
	 * not initialized yet via {@link #afterPropertiesSet()}.
	 */
	public HandlerMethodArgumentResolverComposite getArgumentResolvers() {
		return this.argumentResolvers;
	}

	/**
	 * Provide handlers for custom return value types. Custom handlers are
	 * ordered after built-in ones. To override the built-in support for
	 * return value handling use {@link #setReturnValueHandlers}.
	 */
	public void setCustomReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		this.customReturnValueHandlers = returnValueHandlers;
	}

	/**
	 * Return the custom return value handlers, or {@code null}.
	 */
	public List<HandlerMethodReturnValueHandler> getCustomReturnValueHandlers() {
		return this.customReturnValueHandlers;
	}

	/**
	 * Configure the complete list of supported return value types thus
	 * overriding handlers that would otherwise be configured by default.
	 */
	public void setReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		if (returnValueHandlers == null) {
			this.returnValueHandlers = null;
		}
		else {
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite();
			this.returnValueHandlers.addHandlers(returnValueHandlers);
		}
	}

	/**
	 * Return the configured handlers, or possibly {@code null} if not
	 * initialized yet via {@link #afterPropertiesSet()}.
	 */
	public HandlerMethodReturnValueHandlerComposite getReturnValueHandlers() {
		return this.returnValueHandlers;
	}

	/**
	 * Set the message body converters to use.
	 * <p>These converters are used to convert from and to HTTP requests and responses.
	 */
	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		this.messageConverters = messageConverters;
	}

	/**
	 * Return the configured message body converters.
	 */
	public List<HttpMessageConverter<?>> getMessageConverters() {
		return this.messageConverters;
	}

	/**
	 * Set the {@link ContentNegotiationManager} to use to determine requested media types.
	 * If not set, the default constructor is used.
	 */
	public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
		this.contentNegotiationManager = contentNegotiationManager;
	}

	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	public ApplicationContext getApplicationContext() {
		return this.applicationContext;
	}


	//初始化解析器
	public void afterPropertiesSet() {
		if (this.argumentResolvers == null) {
			List<HandlerMethodArgumentResolver> resolvers = getDefaultArgumentResolvers();
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}
		if (this.returnValueHandlers == null) {
			List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers();
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers);
		}
		initExceptionHandlerAdviceCache();
	}

	/**
	 * Return the list of argument resolvers to use including built-in resolvers
	 * and custom resolvers provided via {@link #setCustomArgumentResolvers}.
	 */
	protected List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<HandlerMethodArgumentResolver>();

		// Type-based argument resolution
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());

		// Custom arguments
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		return resolvers;
	}

	/**
	 * Return the list of return value handlers to use including built-in and
	 * custom handlers provided via {@link #setReturnValueHandlers}.
	 */
	protected List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
		List<HandlerMethodReturnValueHandler> handlers = new ArrayList<HandlerMethodReturnValueHandler>();

		// Single-purpose return value types
		handlers.add(new ModelAndViewMethodReturnValueHandler());
		handlers.add(new ModelMethodProcessor());
		handlers.add(new ViewMethodReturnValueHandler());
		handlers.add(new HttpEntityMethodProcessor(getMessageConverters(), this.contentNegotiationManager));

		// Annotation-based return value types
		handlers.add(new ModelAttributeMethodProcessor(false));
		handlers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(), this.contentNegotiationManager));

		// Multi-purpose return value types
		handlers.add(new ViewNameMethodReturnValueHandler());
		handlers.add(new MapMethodProcessor());

		// Custom return value types
		if (getCustomReturnValueHandlers() != null) {
			handlers.addAll(getCustomReturnValueHandlers());
		}

		// Catch-all
		handlers.add(new ModelAttributeMethodProcessor(true));

		return handlers;
	}

	private void initExceptionHandlerAdviceCache() {
		if (getApplicationContext() == null) {
			return;
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Looking for exception mappings: " + getApplicationContext());
		}

		List<ControllerAdviceBean> beans = ControllerAdviceBean.findAnnotatedBeans(getApplicationContext());
		Collections.sort(beans, new OrderComparator());

		for (ControllerAdviceBean bean : beans) {
			ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(bean.getBeanType());
			if (resolver.hasExceptionMappings()) {
				this.exceptionHandlerAdviceCache.put(bean, resolver);
				logger.info("Detected @ExceptionHandler methods in " + bean);
			}
		}
	}


	/**
	 * Find an {@code @ExceptionHandler} method and invoke it to handle the raised exception.
	 *
	 * 实现了父类的模板方法。解析 ServletInvocableHandlerMethod 抛出的异常
	 */
	@Override
	protected ModelAndView doResolveHandlerMethodException(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod, Exception exception) {

		// 获取 ServletInvocableHandlerMethod 方法
		ServletInvocableHandlerMethod exceptionHandlerMethod = getExceptionHandlerMethod(handlerMethod, exception);
		if (exceptionHandlerMethod == null) {
			return null;
		}

		// 由于是 ervletInvocableHandlerMethod 实例作为处理器，所有需要对方法的 参数、返回值做解析，如下设置了相关解析器（afterPropertiesSet 方法已经初始化）
		exceptionHandlerMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
		exceptionHandlerMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);

		ServletWebRequest webRequest = new ServletWebRequest(request, response);
		ModelAndViewContainer mavContainer = new ModelAndViewContainer();

		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Invoking @ExceptionHandler method: " + exceptionHandlerMethod);
			}

			// 异常处理方法执行，反射应用
			exceptionHandlerMethod.invokeAndHandle(webRequest, mavContainer, exception);
		}
		catch (Exception invocationEx) {
			if (logger.isErrorEnabled()) {
				logger.error("Failed to invoke @ExceptionHandler method: " + exceptionHandlerMethod, invocationEx);
			}
			return null;
		}

		if (mavContainer.isRequestHandled()) {//已完成处理
			return new ModelAndView();
		}
		else {
			ModelAndView mav = new ModelAndView().addAllObjects(mavContainer.getModel());

			//设置 viewName
			mav.setViewName(mavContainer.getViewName());
			if (!mavContainer.isViewReference()) {
				mav.setView((View) mavContainer.getView());
			}
			return mav;
		}
	}

	/**
	 * Find an {@code @ExceptionHandler} method for the given exception. The default
	 * implementation searches methods in the class hierarchy of the controller first
	 * and if not found, it continues searching for additional {@code @ExceptionHandler}
	 * methods assuming some {@linkplain ControllerAdvice @ControllerAdvice}
	 * Spring-managed beans were detected.
	 * @param handlerMethod the method where the exception was raised (may be {@code null})
	 * @param exception the raised exception
	 * @return a method to handle the exception, or {@code null}
	 *
	 * 找到所有注解了  @ControllerAdvice  @ExceptionHandler 的方法，然后在匹配异常
	 * 最终创建出一个  ServletInvocableHandlerMethod 实例
	 */
	protected ServletInvocableHandlerMethod getExceptionHandlerMethod(HandlerMethod handlerMethod, Exception exception) {
		if (handlerMethod != null) {
			Class<?> handlerType = handlerMethod.getBeanType();
			ExceptionHandlerMethodResolver resolver = this.exceptionHandlerCache.get(handlerType);
			if (resolver == null) {
				resolver = new ExceptionHandlerMethodResolver(handlerType);
				this.exceptionHandlerCache.put(handlerType, resolver);
			}
			Method method = resolver.resolveMethod(exception);
			if (method != null) {
				return new ServletInvocableHandlerMethod(handlerMethod.getBean(), method);
			}
		}

		// 遍历
		for (Entry<ControllerAdviceBean, ExceptionHandlerMethodResolver> entry : this.exceptionHandlerAdviceCache.entrySet()) {
			Method method = entry.getValue().resolveMethod(exception);
			if (method != null) {
				return new ServletInvocableHandlerMethod(entry.getKey().resolveBean(), method);
			}
		}

		return null;
	}

}
