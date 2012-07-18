package com.shelfmap.classprocessor.impl;

import com.shelfmap.classprocessor.InterfaceFilter;
import com.shelfmap.classprocessor.PropertyChangeEventAware;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A default implementation of {@code InterfaceFilter}.<br>
 * In default, {@code PropertyChangeEventAware} is ignored.
 * @author Tsutomu YANO
 */
public class DefaultInterfaceFilter implements InterfaceFilter {

    private final Elements elementUtil;
    private final Types typeUtil;
    private final TypeMirror propertyEventAwareType;
    
    public DefaultInterfaceFilter(Elements elementUtil, Types typeUtil) {
        this.elementUtil = elementUtil;
        this.typeUtil = typeUtil;
        this.propertyEventAwareType = elementUtil.getTypeElement(PropertyChangeEventAware.class.getName()).asType();
    }
    
    public boolean canHandle(TypeMirror type) {
        if(typeUtil.isSubtype(type, propertyEventAwareType)) {
            return false;
        }
        return true;
    }
}
