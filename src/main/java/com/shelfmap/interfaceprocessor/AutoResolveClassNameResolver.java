package com.shelfmap.interfaceprocessor;

import com.shelfmap.interfaceprocessor.annotation.GenerateClass;

/**
 * A marker implementation of ClassNameResolver interface.<br>
 * {@link InterfaceProcessor} or subclasses of it should use a default implementation of
 * ClassNameResolver.
 * <p>
 * {@link InterfaceProcessor} use {@link DefaultClassNameResolver} as the default implementation.
 * if the value of 'classNameResolver()' of {@link GenerateClass} annotation is {@code AutoResolveClassNameResolver}.
 * <p>
 * Programmers can change the default implementation by subclassing {@link InterfaceProcessor}.
 * @author Tsutomu YANO
 */
public class AutoResolveClassNameResolver {

}
