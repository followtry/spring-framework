/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.aot;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.springframework.aot.generate.GenerationContext;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.cglib.core.ReflectUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;

/**
 * Process an {@link ApplicationContext} and its {@link BeanFactory} to generate
 * code that represents the state of the bean factory, as well as the necessary
 * hints that can be used at runtime in a constrained environment.
 *
 * @author Stephane Nicoll
 * @author Phillip Webb
 * @since 6.0
 */
public class ApplicationContextAotGenerator {

	/**
	 * 用新生成的Spring上下文来生成class信息。和启动应用的上下文非同一个
	 * Process the specified {@link GenericApplicationContext} ahead-of-time using
	 * the specified {@link GenerationContext}.
	 * <p>Return the {@link ClassName} of the {@link ApplicationContextInitializer}
	 * to use to restore an optimized state of the application context.
	 * @param applicationContext the non-refreshed application context to process  用于AOT探测类路径使用的Spring上下文
	 * @param generationContext the generation context to use  持有生成类信息的上下文
	 * @return the {@code ClassName} of the {@code ApplicationContextInitializer}
	 * entry point
	 */
	public ClassName processAheadOfTime(GenericApplicationContext applicationContext,
			GenerationContext generationContext) {
		return withCglibClassHandler(new CglibClassHandler(generationContext), () -> {
			//开始执行AOT的处理，当前步骤主要完成BeanDefinition信息的解析和注入，包括代理类的生成
			applicationContext.refreshForAotProcessing(generationContext.getRuntimeHints());
			DefaultListableBeanFactory beanFactory = applicationContext.getDefaultListableBeanFactory();
			ApplicationContextInitializationCodeGenerator codeGenerator =
					new ApplicationContextInitializationCodeGenerator(generationContext);
			/**
			 * BeanFactoryInitializationAotContribution的集合，执行AOT处理工作。
			 * 在构造方法里就已经将所有需要执行的BeanFactoryInitializationAotProcessor给执行了，
			 * 执行完后会生成BeanFactoryInitializationAotContribution。
			 * 而applyTo就是遍历所有的BeanFactoryInitializationAotContribution根据其内收集的信息生成对应的资源文件
			 */
			new BeanFactoryInitializationAotContributions(beanFactory).applyTo(generationContext, codeGenerator);
			return codeGenerator.getGeneratedClass().getName();
		});
	}

	//只有在当前类中使用
	private <T> T withCglibClassHandler(CglibClassHandler cglibClassHandler, Supplier<T> task) {
		try {
			//为反射工具生成两个处理器。一个是加载类，一个是生成类文件到指定目录
			ReflectUtils.setLoadedClassHandler(cglibClassHandler::handleLoadedClass);
			ReflectUtils.setGeneratedClassHandler(cglibClassHandler::handleGeneratedClass);
			return task.get();
		}
		finally {
			ReflectUtils.setLoadedClassHandler(null);
			ReflectUtils.setGeneratedClassHandler(null);
		}
	}

}
