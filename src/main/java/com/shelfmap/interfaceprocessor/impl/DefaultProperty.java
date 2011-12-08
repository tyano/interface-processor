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

import com.shelfmap.interfaceprocessor.Property;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tsutomu YANO
 */
public class DefaultProperty implements Property {

    private boolean writable;
    private boolean readable;
    private final String name;
    private final TypeMirror type;
    private String retainType = "HOLD";
    private TypeMirror realType;
    private boolean ignore;

    public DefaultProperty(String name, TypeMirror type, boolean readable, boolean writable) {
        this.name = name;
        this.type = type;
        this.readable = readable;
        this.writable = writable;
    }

    @Override
    public boolean isReadable() {
        return this.readable;
    }

    @Override
    public void setReadable(boolean readable) {
        this.readable = readable;
    }

    @Override
    public boolean isWritable() {
        return writable;
    }

    @Override
    public void setWritable(boolean writable) {
        this.writable = writable;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public TypeMirror getType() {
        return type;
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
    public TypeMirror getRealType() {
        return this.realType;
    }

    @Override
    public void setRealType(TypeMirror type) {
        this.realType = type;
    }

    public boolean isIgnored() {
        return ignore;
    }

    public void setIgnored(boolean ignore) {
        this.ignore = ignore;
    }
}
