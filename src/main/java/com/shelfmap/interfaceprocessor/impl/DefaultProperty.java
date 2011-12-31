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
import javax.lang.model.element.ExecutableElement;
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
    private ExecutableElement reader;
    private ExecutableElement writer;

    public DefaultProperty(String name, TypeMirror type, ExecutableElement reader, ExecutableElement writer) {
        this.name = name;
        this.type = type;
        this.readable = (reader != null);
        this.writable = (writer != null);
        this.reader = reader;
        this.writer = writer;
    }

    @Override
    public boolean isReadable() {
        return this.readable;
    }

    @Override
    public boolean isWritable() {
        return writable;
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

    @Override
    public boolean isIgnored() {
        return ignore;
    }

    @Override
    public void setIgnored(boolean ignore) {
        this.ignore = ignore;
    }

    @Override
    public ExecutableElement getReader() {
        return reader;
    }

    @Override
    public void setReader(ExecutableElement reader) {
        this.reader = reader;
        this.readable = reader != null;
    }

    @Override
    public ExecutableElement getWriter() {
        return writer;
    }

    @Override
    public void setWriter(ExecutableElement writer) {
        this.writer = writer;
        this.writable = writer != null;
    }
}
