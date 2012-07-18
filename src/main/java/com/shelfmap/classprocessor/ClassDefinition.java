/*
 * Copyright 2011 Tsutomu YANO.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shelfmap.classprocessor;

import java.util.Collection;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 *
 * @author Tsutomu YANO
 */
public interface ClassDefinition {
    ElementType getElementType();
    void setElementType(ElementType elementType);
            
    String getPackage();
    void setPackage(String pkg);

    String getClassName();
    void setClassName(String interfaceName);

    Collection<Field> getFields();
    void addFields(Types typeUtil, Field... fields);
    Field findField(String name, TypeMirror type, Types types);

    Collection<Property> getProperties();
    void addProperties(Types typeUtil, Property... props);
    Property findProperty(String name, TypeMirror type, Types types);
    
    /**
     * @return all {@link Field}s which don't have any getter/setter.
     */
    Collection<Field> getInnerFields(Types typeUtil);
    
    boolean isHavingIgnoredProperty();

    Collection<ExecutableElement> getMethods();
    void addMethods(ExecutableElement... methods);

    Collection<TypeParameterElement> getTypeParameterElements();
    void addTypeParameters(TypeParameterElement... parameters);
}
