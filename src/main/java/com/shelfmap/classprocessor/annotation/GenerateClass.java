/*
 * Copyright 2011 Tsutomu YANO.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.shelfmap.classprocessor.annotation;

import com.shelfmap.classprocessor.AutoResolveClassNameResolver;
import com.shelfmap.classprocessor.Modifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author Tsutomu YANO
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface GenerateClass {
    boolean autoGenerate() default true;
    boolean isAbstract() default false;
    boolean isThreadSafe() default true;
    String packageName() default "";
    boolean isPackageNameRelative() default false;
    String className() default "";
    Class<?> superClass() default Void.class;
    String superClassName() default "";
    boolean isSerializable() default true;
    long serialVersion() default 1L;
    boolean isCloneable() default true;
    Modifier fieldModifier() default Modifier.PRIVATE;
    Class<?> classNameResolver() default AutoResolveClassNameResolver.class;
    boolean ignoreSuperInterface() default false;
}
