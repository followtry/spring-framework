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

package org.springframework.beans.factory.aot;

import java.util.Map;

import javax.lang.model.element.Modifier;

import org.springframework.aot.generate.GeneratedClass;
import org.springframework.aot.generate.GeneratedMethod;
import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.MethodReference;
import org.springframework.aot.generate.MethodReference.ArgumentCodeGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.MethodSpec;

/**
 * <pre>
 *     用于注册BeanDefinition信息和别名。该类是给应用入口类使用的，用于生成注册信息和BeanDefinitions信息以及应用上下文初始化信息
 * </pre>
 * AOT contribution from a {@link BeanRegistrationsAotProcessor} used to
 * register bean definitions and aliases.
 *
 * @author Phillip Webb
 * @author Sebastien Deleuze
 * @author Stephane Nicoll
 * @since 6.0
 * @see BeanRegistrationsAotProcessor
 */
class BeanRegistrationsAotContribution
		implements BeanFactoryInitializationAotContribution {

	private static final String BEAN_FACTORY_PARAMETER_NAME = "beanFactory";

	//持有Bean方法
	private final Map<String, Registration> registrations;

	BeanRegistrationsAotContribution(Map<String, Registration> registrations) {
		this.registrations = registrations;
	}


	@Override
	public void applyTo(GenerationContext generationContext,
			BeanFactoryInitializationCode beanFactoryInitializationCode) {

		//取到入口类并生成BeanFactoryRegistrations信息
		GeneratedClass generatedClass = generationContext.getGeneratedClasses()
				.addForFeature("BeanFactoryRegistrations", type -> {
					type.addJavadoc("Register bean definitions for the bean factory.");
					type.addModifiers(Modifier.PUBLIC);
				});
		//初始化类代码生成器
		BeanRegistrationsCodeGenerator codeGenerator = new BeanRegistrationsCodeGenerator(generatedClass);
		//生成类的registerBeanDefinitions方法及方法体内各个bean的注册信息
		GeneratedMethod generatedBeanDefinitionsMethod = codeGenerator.getMethods().add("registerBeanDefinitions", method ->
				generateRegisterBeanDefinitionsMethod(method, generationContext, codeGenerator));
		beanFactoryInitializationCode.addInitializer(generatedBeanDefinitionsMethod.toMethodReference());

		//生成类的registerAliases方法及方法体内各个bean的注册信息
		GeneratedMethod generatedAliasesMethod = codeGenerator.getMethods().add("registerAliases",
				this::generateRegisterAliasesMethod);
		beanFactoryInitializationCode.addInitializer(generatedAliasesMethod.toMethodReference());
	}

	//生成BeanDefinition方法的代码片段
	private void generateRegisterBeanDefinitionsMethod(MethodSpec.Builder method,
			GenerationContext generationContext,
			BeanRegistrationsCode beanRegistrationsCode) {

		method.addJavadoc("Register the bean definitions.");
		method.addModifiers(Modifier.PUBLIC);
		method.addParameter(DefaultListableBeanFactory.class,
				BEAN_FACTORY_PARAMETER_NAME);
		CodeBlock.Builder code = CodeBlock.builder();
		//遍历所有的registrations信息，并将其注册信息生成代码片段
		this.registrations.forEach((beanName, registration) -> {
			MethodReference beanDefinitionMethod = registration.methodGenerator
					.generateBeanDefinitionMethod(generationContext,
							beanRegistrationsCode);
			CodeBlock methodInvocation = beanDefinitionMethod.toInvokeCodeBlock(
					ArgumentCodeGenerator.none(), beanRegistrationsCode.getClassName());
			//将各registration的BeanDefinition信息都在入口类的BeanFactoryRegistrations中注册
			code.addStatement("$L.registerBeanDefinition($S, $L)",
					BEAN_FACTORY_PARAMETER_NAME, beanName,
					methodInvocation);
		});
		method.addCode(code.build());
	}

	private void generateRegisterAliasesMethod(MethodSpec.Builder method) {
		method.addJavadoc("Register the aliases.");
		method.addModifiers(Modifier.PUBLIC);
		method.addParameter(DefaultListableBeanFactory.class,
				BEAN_FACTORY_PARAMETER_NAME);
		CodeBlock.Builder code = CodeBlock.builder();
		this.registrations.forEach((beanName, registration) -> {
			for (String alias : registration.aliases) {
				code.addStatement("$L.registerAlias($S, $S)",
						BEAN_FACTORY_PARAMETER_NAME, beanName, alias);
			}
		});
		method.addCode(code.build());
	}

	/**
	 * Gather the necessary information to register a particular bean.
	 * @param methodGenerator the {@link BeanDefinitionMethodGenerator} to use
	 * @param aliases the bean aliases, if any
	 */
	record Registration(BeanDefinitionMethodGenerator methodGenerator, String[] aliases) {}


	/**
	 * {@link BeanRegistrationsCode} with generation support.
	 */
	static class BeanRegistrationsCodeGenerator implements BeanRegistrationsCode {

		private final GeneratedClass generatedClass;

		public BeanRegistrationsCodeGenerator(GeneratedClass generatedClass) {
			this.generatedClass = generatedClass;
		}


		@Override
		public ClassName getClassName() {
			return this.generatedClass.getName();
		}

		@Override
		public GeneratedMethods getMethods() {
			return this.generatedClass.getMethods();
		}

	}

}
