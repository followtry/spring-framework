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

package org.springframework.aot.generate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.TypeSpec;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * <pre>
 *     用于管理生成类的集合，该类是有状态的
 * </pre>
 *
 * A managed collection of generated classes.
 *
 * <p>This class is stateful, so the same instance should be used for all class
 * generation.
 *
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @since 6.0
 * @see GeneratedClass
 */
public class GeneratedClasses {

	//类名生成器
	private final ClassNameGenerator classNameGenerator;

	//生成的类信息
	private final List<GeneratedClass> classes;

	private final Map<Owner, GeneratedClass> classesByOwner;


	/**
	 * <pre>
	 *     创建新实例
	 * </pre>
	 *
	 * Create a new instance using the specified naming conventions.
	 * @param classNameGenerator the class name generator to use
	 */
	GeneratedClasses(ClassNameGenerator classNameGenerator) {
		this(classNameGenerator, new ArrayList<>(), new ConcurrentHashMap<>());
	}

	private GeneratedClasses(ClassNameGenerator classNameGenerator,
			List<GeneratedClass> classes, Map<Owner, GeneratedClass> classesByOwner) {
		Assert.notNull(classNameGenerator, "'classNameGenerator' must not be null");
		this.classNameGenerator = classNameGenerator;
		this.classes = classes;
		this.classesByOwner = classesByOwner;
	}


	/**
	 * Get or add a generated class for the specified {@code featureName} and no
	 * particular component. If this method has previously been called with the
	 * given {@code featureName} the existing class will be returned, otherwise
	 * a new class will be generated.
	 * @param featureName the name of the feature to associate with the
	 * generated class
	 * @param type a {@link Consumer} used to build the type
	 * @return an existing or newly generated class
	 */
	public GeneratedClass getOrAddForFeature(String featureName,
			Consumer<TypeSpec.Builder> type) {

		Assert.hasLength(featureName, "'featureName' must not be empty");
		Assert.notNull(type, "'type' must not be null");
		Owner owner = new Owner(this.classNameGenerator.getFeatureNamePrefix(), featureName, null);
		GeneratedClass generatedClass = this.classesByOwner.computeIfAbsent(owner, key -> createAndAddGeneratedClass(featureName, null, type));
		generatedClass.assertSameType(type);
		return generatedClass;
	}

	/**
	 * <pre>
	 *     用给定的特性名在没游标组件上生成新的类并将其添加到已生成类的集合中。
	 * </pre>
	 *
	 * Get or add a generated class for the specified {@code featureName}
	 * targeting the specified {@code component}. If this method has previously
	 * been called with the given {@code featureName}/{@code target} the
	 * existing class will be returned, otherwise a new class will be generated,
	 * otherwise a new class will be generated.
	 * @param featureName the name of the feature to associate with the
	 * generated class
	 * @param targetComponent the target component
	 * @param type a {@link Consumer} used to build the type
	 * @return an existing or newly generated class
	 */
	public GeneratedClass getOrAddForFeatureComponent(String featureName,
			ClassName targetComponent, Consumer<TypeSpec.Builder> type) {

		Assert.hasLength(featureName, "'featureName' must not be empty");
		Assert.notNull(targetComponent, "'targetComponent' must not be null");
		Assert.notNull(type, "'type' must not be null");
		Owner owner = new Owner(this.classNameGenerator.getFeatureNamePrefix(), featureName, targetComponent);
		//创建并生成类，且将该类添加到Owner的Map中
		GeneratedClass generatedClass = this.classesByOwner.computeIfAbsent(owner, key ->
				createAndAddGeneratedClass(featureName, targetComponent, type));
		generatedClass.assertSameType(type);
		return generatedClass;
	}

	/**
	 * Get or add a generated class for the specified {@code featureName}
	 * targeting the specified {@code component}. If this method has previously
	 * been called with the given {@code featureName}/{@code target} the
	 * existing class will be returned, otherwise a new class will be generated,
	 * otherwise a new class will be generated.
	 * @param featureName the name of the feature to associate with the
	 * generated class
	 * @param targetComponent the target component
	 * @param type a {@link Consumer} used to build the type
	 * @return an existing or newly generated class
	 */
	public GeneratedClass getOrAddForFeatureComponent(String featureName,
			Class<?> targetComponent, Consumer<TypeSpec.Builder> type) {

		return getOrAddForFeatureComponent(featureName, ClassName.get(targetComponent), type);
	}

	/**
	 * Add a new generated class for the specified {@code featureName} and no
	 * particular component.
	 * @param featureName the name of the feature to associate with the
	 * generated class
	 * @param type a {@link Consumer} used to build the type
	 * @return the newly generated class
	 */
	public GeneratedClass addForFeature(String featureName, Consumer<TypeSpec.Builder> type) {
		Assert.hasLength(featureName, "'featureName' must not be empty");
		Assert.notNull(type, "'type' must not be null");
		return createAndAddGeneratedClass(featureName, null, type);
	}

	/**
	 * Add a new generated class for the specified {@code featureName} targeting
	 * the specified {@code component}.
	 * @param featureName the name of the feature to associate with the
	 * generated class
	 * @param targetComponent the target component
	 * @param type a {@link Consumer} used to build the type
	 * @return the newly generated class
	 */
	public GeneratedClass addForFeatureComponent(String featureName,
			ClassName targetComponent, Consumer<TypeSpec.Builder> type) {

		Assert.hasLength(featureName, "'featureName' must not be empty");
		Assert.notNull(targetComponent, "'targetComponent' must not be null");
		Assert.notNull(type, "'type' must not be null");
		return createAndAddGeneratedClass(featureName, targetComponent, type);
	}

	/**
	 * Add a new generated class for the specified {@code featureName} targeting
	 * the specified {@code component}.
	 * @param featureName the name of the feature to associate with the
	 * generated class
	 * @param targetComponent the target component
	 * @param type a {@link Consumer} used to build the type
	 * @return the newly generated class
	 */
	public GeneratedClass addForFeatureComponent(String featureName,
			Class<?> targetComponent, Consumer<TypeSpec.Builder> type) {

		return addForFeatureComponent(featureName, ClassName.get(targetComponent), type);
	}

	//创建并将生成的类添加到List中
	private GeneratedClass createAndAddGeneratedClass(String featureName,
			@Nullable ClassName targetComponent, Consumer<TypeSpec.Builder> type) {

		//生成新的类名。类名规则为className+"__" + featureNamePrefix + featureName + seqNum
		ClassName className = this.classNameGenerator.generateClassName(featureName, targetComponent);
		//将类名，包名信息和构造的生成类内容的信息都打包进GeneratedClass中生成新的实例，该实例就可以用来生成一个class文件了
		GeneratedClass generatedClass = new GeneratedClass(className, type);
		//将待生成文件的class信息统一搜集
		this.classes.add(generatedClass);
		return generatedClass;
	}

	/**
	 * <pre>
	 *     将生成的java类信息写入到指定的文件中
	 * </pre>
	 *
	 * Write the {@link GeneratedClass generated classes} using the given
	 * {@link GeneratedFiles} instance.
	 * @param generatedFiles where to write the generated classes
	 */
	void writeTo(GeneratedFiles generatedFiles) {
		Assert.notNull(generatedFiles, "'generatedFiles' must not be null");
		List<GeneratedClass> generatedClasses = new ArrayList<>(this.classes);
		generatedClasses.sort(Comparator.comparing(GeneratedClass::getName));
		for (GeneratedClass generatedClass : generatedClasses) {
			generatedFiles.addSourceFile(generatedClass.generateJavaFile());
		}
	}

	GeneratedClasses withFeatureNamePrefix(String name) {
		return new GeneratedClasses(this.classNameGenerator.withFeatureNamePrefix(name),
				this.classes, this.classesByOwner);
	}

	private record Owner(String featureNamePrefix, String featureName, @Nullable ClassName target) {
	}

}
