package com.shelfmap.classprocessor;

import com.shelfmap.classprocessor.impl.DefaultInterfaceFilter;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * An implementation of {@link InterfaceFilter}, which ignores all super interfaces.
 * @author Tsutomu YANO
 */
public class IgnoreSuperInterfaceFilter extends DefaultInterfaceFilter {

    public IgnoreSuperInterfaceFilter(Elements elementUtil, Types typeUtil) {
        super(elementUtil, typeUtil);
    }

    @Override
    public boolean canHandle(TypeMirror type) {
        return false;
    }
}
