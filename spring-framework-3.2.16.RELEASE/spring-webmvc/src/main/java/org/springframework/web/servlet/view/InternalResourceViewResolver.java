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

package org.springframework.web.servlet.view;

import org.springframework.util.ClassUtils;

/**
 * Convenient subclass of {@link UrlBasedViewResolver} that supports
 * {@link InternalResourceView} (i.e. Servlets and JSPs) and subclasses
 * such as {@link JstlView}.
 *
 * <p>The view class for all views generated by this resolver can be specified
 * via {@link #setViewClass}. See {@link UrlBasedViewResolver}'s javadoc for details.
 * The default is {@link InternalResourceView}, or {@link JstlView} if the
 * JSTL API is present.
 *
 * <p>BTW, it's good practice to put JSP files that just serve as views under
 * WEB-INF, to hide them from direct access (e.g. via a manually entered URL).
 * Only controllers will be able to access them then.
 *
 * <p><b>Note:</b> When chaining ViewResolvers, an InternalResourceViewResolver
 * always needs to be last, as it will attempt to resolve any view name,
 * no matter whether the underlying resource actually exists.
 *
 * @author Juergen Hoeller
 * @since 17.02.2003
 * @see #setViewClass
 * @see #setPrefix
 * @see #setSuffix
 * @see #setRequestContextAttribute
 * @see InternalResourceView
 * @see JstlView
 *
 * 常用的 jsp 视图解析器
 */
public class InternalResourceViewResolver extends UrlBasedViewResolver {

	private static final boolean jstlPresent = ClassUtils.isPresent(
			"javax.servlet.jsp.jstl.core.Config", InternalResourceViewResolver.class.getClassLoader());

	private Boolean alwaysInclude;

	private Boolean exposeContextBeansAsAttributes;

	private String[] exposedContextBeanNames;


	/**
	 * Sets the default {@link #setViewClass view class} to {@link #requiredViewClass}:
	 * by default {@link InternalResourceView}, or {@link JstlView} if the JSTL API
	 * is present.
	 */
	public InternalResourceViewResolver() {

		Class<?> viewClass = requiredViewClass();
		if (viewClass.equals(InternalResourceView.class) && jstlPresent) {
			viewClass = JstlView.class;
		}

		// 构造方法中直接设置 viewClass
		setViewClass(viewClass);
	}


	/**
	 * This resolver requires {@link InternalResourceView}.
	 */
	@Override
	protected Class<?> requiredViewClass() {
		return InternalResourceView.class;
	}

	/**
	 * Specify whether to always include the view rather than forward to it.
	 * <p>Default is "false". Switch this flag on to enforce the use of a
	 * Servlet include, even if a forward would be possible.
	 * @see InternalResourceView#setAlwaysInclude
	 */
	public void setAlwaysInclude(boolean alwaysInclude) {
		this.alwaysInclude = alwaysInclude;
	}

	/**
	 * Set whether to make all Spring beans in the application context accessible
	 * as request attributes, through lazy checking once an attribute gets accessed.
	 * <p>This will make all such beans accessible in plain {@code ${...}}
	 * expressions in a JSP 2.0 page, as well as in JSTL's {@code c:out}
	 * value expressions.
	 * <p>Default is "false".
	 * @see InternalResourceView#setExposeContextBeansAsAttributes
	 */
	public void setExposeContextBeansAsAttributes(boolean exposeContextBeansAsAttributes) {
		this.exposeContextBeansAsAttributes = exposeContextBeansAsAttributes;
	}

	/**
	 * Specify the names of beans in the context which are supposed to be exposed.
	 * If this is non-null, only the specified beans are eligible for exposure as
	 * attributes.
	 * @see InternalResourceView#setExposedContextBeanNames
	 */
	public void setExposedContextBeanNames(String... exposedContextBeanNames) {
		this.exposedContextBeanNames = exposedContextBeanNames;
	}


	@Override
	protected AbstractUrlBasedView buildView(String viewName) throws Exception {

		// 调用不累方法 buildView
		InternalResourceView view = (InternalResourceView) super.buildView(viewName);

		// 设置 view 特有的属性
		if (this.alwaysInclude != null) {
			view.setAlwaysInclude(this.alwaysInclude);
		}
		if (this.exposeContextBeansAsAttributes != null) {
			view.setExposeContextBeansAsAttributes(this.exposeContextBeansAsAttributes);
		}
		if (this.exposedContextBeanNames != null) {
			view.setExposedContextBeanNames(this.exposedContextBeanNames);
		}

		// 阻止循环调用
		view.setPreventDispatchLoop(true);
		return view;
	}

}
