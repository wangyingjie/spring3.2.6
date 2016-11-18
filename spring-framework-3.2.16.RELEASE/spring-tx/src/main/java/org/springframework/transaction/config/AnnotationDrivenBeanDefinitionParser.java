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

package org.springframework.transaction.config;

import org.w3c.dom.Element;

import org.springframework.aop.config.AopNamespaceUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * {@link org.springframework.beans.factory.xml.BeanDefinitionParser
 * BeanDefinitionParser} implementation that allows users to easily configure
 * all the infrastructure beans required to enable annotation-driven transaction
 * demarcation.
 *
 * <p>By default, all proxies are created as JDK proxies. This may cause some
 * problems if you are injecting objects as concrete classes rather than
 * interfaces. To overcome this restriction you can set the
 * '{@code proxy-target-class}' attribute to '{@code true}', which
 * will result in class-based proxies being created.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Chris Beams
 * @since 2.0
 *
 * 注解驱动解析器
 */
class AnnotationDrivenBeanDefinitionParser implements BeanDefinitionParser {

	/**
	 * The bean name of the internally managed transaction advisor (mode="proxy").
	 * @deprecated as of Spring 3.1 in favor of
	 * {@link TransactionManagementConfigUtils#TRANSACTION_ADVISOR_BEAN_NAME}
	 */
	@Deprecated
	public static final String TRANSACTION_ADVISOR_BEAN_NAME =
			TransactionManagementConfigUtils.TRANSACTION_ADVISOR_BEAN_NAME;

	/**
	 * The bean name of the internally managed transaction aspect (mode="aspectj").
	 * @deprecated as of Spring 3.1 in favor of
	 * {@link TransactionManagementConfigUtils#TRANSACTION_ASPECT_BEAN_NAME}
	 */
	@Deprecated
	public static final String TRANSACTION_ASPECT_BEAN_NAME =
			TransactionManagementConfigUtils.TRANSACTION_ASPECT_BEAN_NAME;


	/**
	 * Parses the {@code <tx:annotation-driven/>} tag. Will
	 * {@link AopNamespaceUtils#registerAutoProxyCreatorIfNecessary register an AutoProxyCreator}
	 * with the container as necessary.
	 */
	public BeanDefinition parse(Element element, ParserContext parserContext) {
		String mode = element.getAttribute("mode");
		if ("aspectj".equals(mode)) {
			// mode="aspectj"
			registerTransactionAspect(element, parserContext);
		}
		else {
			// mode="proxy"  代理配置 属于默认配置
			AopAutoProxyConfigurer.configureAutoProxyCreator(element, parserContext);
		}
		return null;
	}

	private void registerTransactionAspect(Element element, ParserContext parserContext) {
		String txAspectBeanName = TransactionManagementConfigUtils.TRANSACTION_ASPECT_BEAN_NAME;
		String txAspectClassName = TransactionManagementConfigUtils.TRANSACTION_ASPECT_CLASS_NAME;
		if (!parserContext.getRegistry().containsBeanDefinition(txAspectBeanName)) {
			RootBeanDefinition def = new RootBeanDefinition();
			def.setBeanClassName(txAspectClassName);
			def.setFactoryMethodName("aspectOf");
			registerTransactionManager(element, def);
			parserContext.registerBeanComponent(new BeanComponentDefinition(def, txAspectBeanName));
		}
	}

	private static void registerTransactionManager(Element element, BeanDefinition def) {
		def.getPropertyValues().add("transactionManagerBeanName",
				TxNamespaceHandler.getTransactionManagerName(element));
	}


	/**
	 * 	 内部类只是介绍AOP框架依赖其实在代理模式。
	 * Inner class to just introduce an AOP framework dependency when actually in proxy mode.
	 */
	private static class AopAutoProxyConfigurer {

		public static void configureAutoProxyCreator(Element element, ParserContext parserContext) {

			//注册SpringAop
			AopNamespaceUtils.registerAutoProxyCreatorIfNecessary(parserContext, element);

			//事务顾问的注册BeanName   TRANSACTION_ADVISOR_BEAN_NAME = "org.springframework.transaction.config.internalTransactionAdvisor"
			String txAdvisorBeanName = TransactionManagementConfigUtils.TRANSACTION_ADVISOR_BEAN_NAME;

			if (!parserContext.getRegistry().containsBeanDefinition(txAdvisorBeanName)) {
				// 元素源
				Object eleSource = parserContext.extractSource(element);

				// Create the TransactionAttributeSource definition.   创建TransactionAttributeSource 的Bean
				RootBeanDefinition sourceDef = new RootBeanDefinition(
						"org.springframework.transaction.annotation.AnnotationTransactionAttributeSource");
				sourceDef.setSource(eleSource);
				sourceDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
				// 注册Bean 并使用spring中的规则生成 beanName
				String sourceName = parserContext.getReaderContext().registerWithGeneratedName(sourceDef);

				// Create the TransactionInterceptor definition.   创建TransactionInterceptor 的Bean
				RootBeanDefinition interceptorDef = new RootBeanDefinition(TransactionInterceptor.class);
				interceptorDef.setSource(eleSource);
				interceptorDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
				//将 transactionManager Bean注册到 interceptorDef 中
				registerTransactionManager(element, interceptorDef);
				interceptorDef.getPropertyValues().add("transactionAttributeSource", new RuntimeBeanReference(sourceName));
				// 注册Bean 并使用spring中的规则生成 beanName
				String interceptorName = parserContext.getReaderContext().registerWithGeneratedName(interceptorDef);

				// Create the TransactionAttributeSourceAdvisor definition. 创建TransactionAttributeSourceAdvisor 的Bean
				// advisorDef 是BeanFactoryTransactionAttributeSourceAdvisor的一个实例 通知类的实现，实现了接口 PointCutAdvisor#getPointcut / Advisor  接口
				RootBeanDefinition advisorDef = new RootBeanDefinition(BeanFactoryTransactionAttributeSourceAdvisor.class);
				advisorDef.setSource(eleSource);
				advisorDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
				//将sourceName的Bean注入到 advisorDef 的transactionAttributeSource属性中
				advisorDef.getPropertyValues().add("transactionAttributeSource", new RuntimeBeanReference(sourceName));
				//将interceptorName的Bean注入到 advisorDef 的adviceBeanName属性中
				advisorDef.getPropertyValues().add("adviceBeanName", interceptorName);
				if (element.hasAttribute("order")) {//配置了order属性则加入到Bean中
					advisorDef.getPropertyValues().add("order", element.getAttribute("order"));
				}

				//将事务通知拦截器注册
				parserContext.getRegistry().registerBeanDefinition(txAdvisorBeanName, advisorDef);

				// 创建 CompositeComponentDefinition
				CompositeComponentDefinition compositeDef = new CompositeComponentDefinition(element.getTagName(), eleSource);
				// 注册BeanDefinition
				compositeDef.addNestedComponent(new BeanComponentDefinition(sourceDef, sourceName));
				compositeDef.addNestedComponent(new BeanComponentDefinition(interceptorDef, interceptorName));
				compositeDef.addNestedComponent(new BeanComponentDefinition(advisorDef, txAdvisorBeanName));

				//
				parserContext.registerComponent(compositeDef);
			}
		}
	}

}
