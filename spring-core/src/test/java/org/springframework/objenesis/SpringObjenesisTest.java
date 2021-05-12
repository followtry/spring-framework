package org.springframework.objenesis;

import org.junit.jupiter.api.Test;
import org.springframework.objenesis.instantiator.ObjectInstantiator;
import org.springframework.objenesis.instantiator.basic.ObjectInputStreamInstantiator;
import org.springframework.objenesis.instantiator.util.UnsafeUtils;
import sun.misc.Unsafe;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author followtry
 * @since 2021/5/10 8:49 下午
 */
public class SpringObjenesisTest {


	@Test
	void newInstanceOneArg(){
		SpringObjenesis objenesis = new SpringObjenesis();
		ObjectInstantiator<Me> instantiatorOf = objenesis.getInstantiatorOf(Me.class);
		Me me = instantiatorOf.newInstance();
		System.out.println(me);
		assertThat(me).isNotNull();
	}

	@Test
	void newInstanceUnsafe() throws InstantiationException {
		Unsafe unsafe = UnsafeUtils.getUnsafe();
		Object o = unsafe.allocateInstance(Me.class);
		Me me = Me.class.cast(o);
		System.out.println(me);
		assertThat(me).isNotNull();
	}

	@Test
	void newInstanceObjectInputStream(){
		ObjectInputStreamInstantiator<Me> instantiator = new ObjectInputStreamInstantiator<>(Me.class);
		Me me = instantiator.newInstance();
		System.out.println(me);
		assertThat(me).isNotNull();


	}

	class Me implements Serializable {

		private static final long serialVersionUID = 7100714597678207546L;

		public Me(Integer age, String name) {
			this.age = age;
			this.name = name;
		}

		/**
		 * 年龄
		 */
		private Integer age;

		/**
		 * 名称
		 */
		private String name;

		@Override
		public String toString() {
			return "Me{" +
					"age=" + age +
					", name='" + name + '\'' +
					'}';
		}
	}
}
