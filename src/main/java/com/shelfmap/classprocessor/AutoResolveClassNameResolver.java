package com.shelfmap.classprocessor;

import com.shelfmap.classprocessor.annotation.GenerateClass;

/**
 * A marker implementation for ClassNameResolver interface.<br>
 * {@link InterfaceProcessor} or subclasses of it should use a default implementation of
 * ClassNameResolver, if the value of 'classNameResolver()' of {@link GenerateClass} annotation is 
 * {@code AutoResolveClassNameResolver}.
 * <p>
 * {@link InterfaceProcessor} uses {@link DefaultClassNameResolver} as the default implementation.
 * <p>
 * Programmers can change the default implementation by subclassing {@link InterfaceProcessor}.
 * @author Tsutomu YANO
 */
public class AutoResolveClassNameResolver {

}
