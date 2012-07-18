/*
 * Copyright 2012 Tsutomu YANO.
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
package com.shelfmap.classprocessor.impl;

import com.shelfmap.classprocessor.Field;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tsutomu YANO
 */
public class DefaultField implements Field {

    private String name;
    private TypeMirror type;
    private boolean propertyDefined = false;
    private String retainType = "HOLD";
    private TypeMirror realType;
    private boolean ignore;
    private boolean readOnly;
    private String modifier = "PUBLIC";

    public DefaultField(String name, TypeMirror type) {
        this.name = name;
        this.type = type;
    }
    
    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public TypeMirror getType() {
        return this.type;
    }

    @Override
    public boolean isPropertyDefined() {
        return propertyDefined;
    }

    @Override
    public void setPropertyDefined(boolean propertyDefined) {
        this.propertyDefined = propertyDefined;
    }

    @Override
    public String getRetainType() {
        return this.retainType;
    }

    @Override
    public void setRetainType(String type) {
        this.retainType = type;
    }

    @Override
    public boolean isIgnored() {
        return this.ignore;
    }

    @Override
    public void setIgnored(boolean ignore) {
        this.ignore = ignore;
    }

    @Override
    public TypeMirror getRealType() {
        return this.realType;
    }

    @Override
    public void setRealType(TypeMirror type) {
        this.realType = type;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public String getMethodModifier() {
        return this.modifier;
    }

    @Override
    public void setMethodModifier(String modifier) {
        this.modifier = modifier;
    }
}
