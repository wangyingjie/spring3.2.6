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

package org.springframework.beans.support;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.util.comparator.CompoundComparator;

/**
 * Unit tests for {@link PropertyComparator}
 *
 * @see org.springframework.util.comparator.ComparatorTests
 *
 * @author Keith Donald
 * @author Chris Beams
 */
public class PropertyComparatorTests {

	@Test
	public void testPropertyComparator() {
		Dog dog = new Dog();
		dog.setNickName("mace");

		Dog dog2 = new Dog();
		dog2.setNickName("biscy");

		// 用于对象字符串的大小比较
		PropertyComparator c = new PropertyComparator("nickName", false, true);
		assertTrue(c.compare(dog, dog2) > 0);
		assertTrue(c.compare(dog, dog) == 0);
		assertTrue(c.compare(dog2, dog) < 0);
	}

	@Test
	public void testPropertyComparatorNulls() {
		Dog dog = new Dog();
		Dog dog2 = new Dog();
		PropertyComparator c = new PropertyComparator("nickName", false, true);
		assertTrue(c.compare(dog, dog2) == 0);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCompoundComparator() {//可以添加多属性的比较器

		//Compound  vt. 合成；混合；和解妥协；搀合
		CompoundComparator<Dog> c = new CompoundComparator<Dog>();
		c.addComparator(new PropertyComparator("lastName", false, true));

		Dog dog1 = new Dog();
		dog1.setFirstName("macy");
		dog1.setLastName("grayspots");

		Dog dog2 = new Dog();
		dog2.setFirstName("biscuit");
		dog2.setLastName("grayspots");

		assertTrue(c.compare(dog1, dog2) == 0);

		// 在添加一个属性的比较器
		c.addComparator(new PropertyComparator("firstName", false, true));
		assertTrue(c.compare(dog1, dog2) > 0);

		dog2.setLastName("konikk dog");
		assertTrue(c.compare(dog2, dog1) > 0);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCompoundComparatorInvert() {
		CompoundComparator<Dog> c = new CompoundComparator<Dog>();

		// 添加了两个比较器
		c.addComparator(new PropertyComparator("lastName", false, true));
		c.addComparator(new PropertyComparator("firstName", false, true));
		Dog dog1 = new Dog();
		dog1.setFirstName("macy");
		dog1.setLastName("grayspots");

		Dog dog2 = new Dog();
		dog2.setFirstName("biscuit");
		dog2.setLastName("grayspots");

		assertTrue(c.compare(dog1, dog2) > 0);

		//invert : 使…反转；使…前后倒置
		c.invertOrder();
		assertTrue(c.compare(dog1, dog2) < 0);
	}


	@SuppressWarnings("unused")
	private static class Dog implements Comparable<Object> {

		private String nickName;

		private String firstName;

		private String lastName;

		@Override
		public int compareTo(Object o) {
			return nickName.compareTo(((Dog)o).nickName);
		}

		public String getNickName() {
			return nickName;
		}

		public void setNickName(String nickName) {
			this.nickName = nickName;
		}

		public String getFirstName() {
			return firstName;
		}

		public void setFirstName(String firstName) {
			this.firstName = firstName;
		}

		public String getLastName() {
			return lastName;
		}

		public void setLastName(String lastName) {
			this.lastName = lastName;
		}
	}

}
