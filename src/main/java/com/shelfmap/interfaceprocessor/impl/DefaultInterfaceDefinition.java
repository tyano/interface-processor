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
package com.shelfmap.interfaceprocessor.impl;

import com.shelfmap.interfaceprocessor.InterfaceDefinition;
import com.shelfmap.interfaceprocessor.Property;
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
public class DefaultInterfaceDefinition implements InterfaceDefinition {
    private String pkg;
    private String interfaceName;
    private final List<Property> properties = new ArrayList<Property>();
    private final List<ExecutableElement> methods = new ArrayList<ExecutableElement>();
    private final List<TypeParameterElement> typeParameters = new ArrayList<TypeParameterElement>();

    public DefaultInterfaceDefinition() {
        super();
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
    public String getInterfaceName() {
        return this.interfaceName;
    }

    @Override
    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    @Override
    public Collection<Property> getProperties() {
        return new ArrayList<Property>(properties);
    }

    @Override
    public void addProperties(Property... props) {
        if(props == null) return;
        this.properties.addAll(Arrays.asList(props));
    }

    @Override
    public Property findProperty(String name, TypeMirror type, Types types) {
        for (Property prop : properties) {
            if(prop.getName().equals(name) && types.isSameType(prop.getType(), type)) {
                return prop;
            }
        }
        return null;
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
