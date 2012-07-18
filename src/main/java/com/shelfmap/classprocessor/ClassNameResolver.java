package com.shelfmap.classprocessor;

/**
 * An interface for the classes which generate a simple class name of an interface.
 * @author Tsutomu YANO
 */
public interface ClassNameResolver {
    String classNameFor(String interfaceName);
    String abstractClassNameFor(String interfaceName);
}
