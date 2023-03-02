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

import java.util.function.Consumer;

import org.springframework.aot.generate.GeneratedFiles;
import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint.Builder;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.support.ClassHintUtils;
import org.springframework.cglib.core.ReflectUtils;
import org.springframework.core.io.ByteArrayResource;

/**
 * <pre>
 *     处理CGLIB生成的类，将其加入到GenerationContext并注册必要的提示（hint）来使其可以被实例化
 * </pre>
 *
 * Handle CGLIB classes by adding them to a {@link GenerationContext},
 * and register the necessary hints so that they can be instantiated.
 *
 * @author Stephane Nicoll
 * @since 6.0
 * @see ReflectUtils#setGeneratedClassHandler
 * @see ReflectUtils#setLoadedClassHandler
 * @see ClassHintUtils#registerProxyIfNecessary
 */
class CglibClassHandler {

	private static final Consumer<Builder> instantiateCglibProxy = hint ->
			hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);

	private final RuntimeHints runtimeHints;

	private final GeneratedFiles generatedFiles;


	CglibClassHandler(GenerationContext generationContext) {
		this.runtimeHints = generationContext.getRuntimeHints();
		this.generatedFiles = generationContext.getGeneratedFiles();
	}


	/**
	 * <pre>
	 *     处理生成的类和cglib类名
	 *     1. 将其加入到RuntimeHint的反射配置中
	 *     2. 将cglib的类名转换为路径名
	 *     3. 将类内容缓存在内存中或者直接保存在文件系统的指定路径上
	 * </pre>
	 * Handle the specified generated CGLIB class.
	 * @param cglibClassName the name of the generated class
	 * @param content the bytecode of the generated class
	 */
	public void handleGeneratedClass(String cglibClassName, byte[] content) {
		registerHints(TypeReference.of(cglibClassName));
		String path = cglibClassName.replace(".", "/") + ".class";
		this.generatedFiles.addFile(Kind.CLASS, path, new ByteArrayResource(content));
	}

	/**
	 * <pre>
	 *     只需要将指定的已加载的cglib类信息注入进RuntimeHint的反射配置中
	 * </pre>
	 * Handle the specified loaded CGLIB class.
	 * @param cglibClass a cglib class that has been loaded
	 */
	public void handleLoadedClass(Class<?> cglibClass) {
		registerHints(TypeReference.of(cglibClass));
	}

	private void registerHints(TypeReference cglibTypeReference) {
		this.runtimeHints.reflection().registerType(cglibTypeReference, instantiateCglibProxy);
	}

}
