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
package com.shelfmap.classprocessor.impl;

import com.shelfmap.classprocessor.ClassDefinition;
import com.shelfmap.classprocessor.ElementType;
import com.shelfmap.classprocessor.Field;
import com.shelfmap.classprocessor.Property;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 *
 * @author Tsutomu YANO
 */
public class DefautClassDefinition implements ClassDefinition {
    private ElementType elementType;
    private String pkg;
    private String interfaceName;
    private final List<Field> fields = new ArrayList<Field>();
    private final List<Property> properties = new ArrayList<Property>();
    private final List<ExecutableElement> methods = new ArrayList<ExecutableElement>();
    private final List<TypeParameterElement> typeParameters = new ArrayList<TypeParameterElement>();

    public DefautClassDefinition() {
        super();
    }

    @Override
    public ElementType getElementType() {
        return elementType;
    }

    @Override
    public void setElementType(ElementType elementType) {
        this.elementType = elementType;
    }
    
    @Override
    public String getPackage() {
        return pkg;
    }

    @Override
    public void setPackage(String pkg) {
        this.pkg = pkg;
    }

    @Override
    public String getClassName() {
        return this.interfaceName;
    }

    @Override
    public void setClassName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    @Override
    public Collection<Field> getFields() {
        return new ArrayList<Field>(this.fields);
    }

    @Override
    public void addFields(Types typeUtil, Field... fields) {
        if(fields == null) return;
        this.fields.addAll(Arrays.asList(fields));
        
        for (Field field : fields) {
            Property prop = findProperty(field.getName(), field.getType(), typeUtil);
            if(prop != null) {
                System.out.println("name = " + field.getName());
                prop.setFieldDefined(true);
                field.setPropertyDefined(true);
            }
        }
    }

    @Override
    public Field findField(String name, TypeMirror type, Types typeUtil) {
        for (Field field : this.fields) {
            if(field.getName().equals(name) && typeUtil.isSameType(field.getType(), type)) {
                return field;
            }
        }
        return null;
    }

    @Override
    public Collection<Property> getProperties() {
        return new ArrayList<Property>(properties);
    }

    @Override
    public void addProperties(Types typeUtil, Property... props) {
        if(props == null) return;
        this.properties.addAll(Arrays.asList(props));
        
        for (Property prop : props) {
            Field field = findField(prop.getName(), prop.getType(), typeUtil);
            if(field != null) {
                prop.setFieldDefined(true);
                field.setPropertyDefined(true);
            }
        }
    }

    @Override
    public Property findProperty(String name, TypeMirror type, Types typeUtil) {
        for (Property prop : properties) {
            if(prop.getName().equals(name) && typeUtil.isSameType(prop.getType(), type)) {
                return prop;
            }
        }
        return null;
    }
    
    @Override
    public Collection<Field> getInnerFields(Types typeUtil) {
        List<Field> innerFields = new ArrayList<Field>();
        for (Field field : fields) {
            Property prop = findProperty(field.getName(), field.getType(), typeUtil);
            if(prop == null) {
                innerFields.add(field);
            }
        }
        return innerFields;
    }

    @Override
    public Collection<ExecutableElement> getMethods() {
        return new ArrayList<ExecutableElement>(methods);
    }

    @Override
    public void addMethods(ExecutableElement... methods) {
        if(methods == null) return;
        this.methods.addAll(Arrays.asList(methods));
    }

    @Override
    public Collection<TypeParameterElement> getTypeParameterElements() {
        return new ArrayList<TypeParameterElement>(this.typeParameters);
    }

    @Override
    public void addTypeParameters(TypeParameterElement... parameters) {
        if(parameters == null) return;
        this.typeParameters.addAll(Arrays.asList(parameters));
    }

    public boolean isHavingIgnoredProperty() {
        for (Property property : getProperties()) {
            if(property.isIgnored()) return true;
        }
        return false;
    }
}
