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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.FileSystemGeneratedFiles;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;
import org.springframework.util.CollectionUtils;

/**
 * <pre>
 *     基于文件系统的提前（AOT）处理基础实现。具体实现通常用于启动构建工具中应用程序的优化。
 * </pre>
 *
 * Filesystem-based ahead-of-time (AOT) processing base implementation.
 *
 * <p>Concrete implementations are typically used to kick off optimization of an
 * application in a build tool.
 *
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 6.0
 * @see org.springframework.test.context.aot.TestAotProcessor
 */
public abstract class ContextAotProcessor extends AbstractAotProcessor<ClassName> {

	private final Class<?> applicationClass;


	/**
	 * Create a new processor for the specified application entry point and
	 * common settings.
	 * @param applicationClass the application entry point (class with a {@code main()} method)
	 * @param settings the settings to apply
	 */
	protected ContextAotProcessor(Class<?> applicationClass, Settings settings) {
		super(settings);
		this.applicationClass = applicationClass;
	}


	/**
	 * <pre>
	 *     获取用户自己的Spring应用的入口，一般是应用的启动类
	 * </pre>
	 * Get the application entry point (typically a class with a {@code main()} method).
	 */
	protected Class<?> getApplicationClass() {
		return this.applicationClass;
	}


	/**
	 * Invoke the processing by clearing output directories first, followed by
	 * {@link #performAotProcessing(GenericApplicationContext)}.
	 * @return the {@code ClassName} of the {@code ApplicationContextInitializer}
	 * entry point
	 */
	@Override
	protected ClassName doProcess() {
		//删除已经存在的输出目录
		deleteExistingOutput();
		//获取到Spring的上下文
		GenericApplicationContext applicationContext = prepareApplicationContext(getApplicationClass());
		//将Spring的上下文作为参数传入并执行AOT处理
		return performAotProcessing(applicationContext);
	}

	/**
	 * <pre>
	 *     为要针对ApplicationContextAotGenerator使用的指定应用程序入口点准备GenericApplicationContext。
	 * </pre>
	 * Prepare the {@link GenericApplicationContext} for the specified
	 * application entry point to be used against an {@link ApplicationContextAotGenerator}.
	 * @return a non-refreshed {@link GenericApplicationContext}
	 */
	protected abstract GenericApplicationContext prepareApplicationContext(Class<?> applicationClass);

	/**
	 * <pre>
	 *     执行AOT的处理。代码、资源和生成的类都存储在配置的输出目录中。此外，还为应用程序及其入口点注册运行时提示。
	 * </pre>
	 * Perform ahead-of-time processing of the specified context.
	 * <p>Code, resources, and generated classes are stored in the configured
	 * output directories. In addition, run-time hints are registered for the
	 * application and its entry point.
	 * @param applicationContext the context to process
	 */
	protected ClassName performAotProcessing(GenericApplicationContext applicationContext) {
		//文件系统生成文件对象实例，还未操作文件
		FileSystemGeneratedFiles generatedFiles = createFileSystemGeneratedFiles();
		//用于AOT处理的上下文，持有序列生成器，生成类管理器，类生成器，文件生成器和运行时提示
		DefaultGenerationContext generationContext = new DefaultGenerationContext(createClassNameGenerator(), generatedFiles);
		//处理ApplicationContext及其BeanFactory以生成表示bean工厂状态的代码，以及可以在运行时在受限环境中使用的必要提示。
		ApplicationContextAotGenerator generator = new ApplicationContextAotGenerator();
		ClassName generatedInitializerClassName = generator.processAheadOfTime(applicationContext, generationContext);
		//注册入口的提示信息
		registerEntryPointHint(generationContext, generatedInitializerClassName);
		//将所有生成的类信息都写入到文件中
		generationContext.writeGeneratedContent();
		//将所有的hint提示都写入文件中
		writeHints(generationContext.getRuntimeHints());
		//写入镜像文件配置
		writeNativeImageProperties(getDefaultNativeImageArguments(getApplicationClass().getName()));
		return generatedInitializerClassName;
	}

	/**
	 * Callback to customize the {@link ClassNameGenerator}.
	 * <p>By default, a standard {@link ClassNameGenerator} using the configured
	 * {@linkplain #getApplicationClass() application entry point} as the default
	 * target is used.
	 * @return the class name generator
	 */
	protected ClassNameGenerator createClassNameGenerator() {
		return new ClassNameGenerator(ClassName.get(getApplicationClass()));
	}

	/**
	 * Return the native image arguments to use.
	 * <p>By default, the main class to use, as well as standard application flags
	 * are added.
	 * <p>If the returned list is empty, no {@code native-image.properties} is
	 * contributed.
	 * @param applicationClassName the fully qualified class name of the application
	 * entry point
	 * @return the native image options to contribute
	 */
	protected List<String> getDefaultNativeImageArguments(String applicationClassName) {
		List<String> args = new ArrayList<>();
		args.add("-H:Class=" + applicationClassName);
		args.add("--report-unsupported-elements-at-runtime");
		args.add("--no-fallback");
		args.add("--install-exit-handlers");
		return args;
	}

	private void registerEntryPointHint(DefaultGenerationContext generationContext,
			ClassName generatedInitializerClassName) {

		TypeReference generatedType = TypeReference.of(generatedInitializerClassName.canonicalName());
		TypeReference applicationType = TypeReference.of(getApplicationClass());
		ReflectionHints reflection = generationContext.getRuntimeHints().reflection();
		reflection.registerType(applicationType);
		reflection.registerType(generatedType, typeHint -> typeHint.onReachableType(applicationType)
				.withConstructor(Collections.emptyList(), ExecutableMode.INVOKE));
	}

	private void writeNativeImageProperties(List<String> args) {
		if (CollectionUtils.isEmpty(args)) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("Args = ");
		sb.append(String.join(String.format(" \\%n"), args));
		Path file = getSettings().getResourceOutput().resolve("META-INF/native-image/" +
				getSettings().getGroupId() + "/" + getSettings().getArtifactId() + "/native-image.properties");
		try {
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				Files.createFile(file);
			}
			Files.writeString(file, sb.toString());
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write native-image.properties", ex);
		}
	}

}
