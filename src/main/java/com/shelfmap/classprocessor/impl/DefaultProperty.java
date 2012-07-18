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

import com.shelfmap.classprocessor.Property;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tsutomu YANO
 */
public class DefaultProperty implements Property {

    private boolean defined;
    private boolean writable;
    private boolean readable;
    private boolean fieldDefined = false;
    private final String name;
    private final TypeMirror type;
    private String retainType = "HOLD";
    private TypeMirror realType;
    private boolean ignore;
    private ExecutableElement reader;
    private ExecutableElement writer;

    public DefaultProperty(String name, TypeMirror type, boolean defined, ExecutableElement reader, ExecutableElement writer) {
        this.name = name;
        this.type = type;
        this.defined = defined;
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
    public boolean isDefined() {
        return defined;
    }

    @Override
    public boolean isFieldDefined() {
        return fieldDefined;
    }

    @Override
    public void setFieldDefined(boolean isFieldDefined) {
        this.fieldDefined = isFieldDefined;
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

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + (this.defined ? 1 : 0);
        hash = 67 * hash + (this.writable ? 1 : 0);
        hash = 67 * hash + (this.readable ? 1 : 0);
        hash = 67 * hash + (this.fieldDefined ? 1 : 0);
        hash = 67 * hash + (this.name != null ? this.name.hashCode() : 0);
        hash = 67 * hash + (this.type != null ? this.type.hashCode() : 0);
        hash = 67 * hash + (this.retainType != null ? this.retainType.hashCode() : 0);
        hash = 67 * hash + (this.realType != null ? this.realType.hashCode() : 0);
        hash = 67 * hash + (this.ignore ? 1 : 0);
        hash = 67 * hash + (this.reader != null ? this.reader.hashCode() : 0);
        hash = 67 * hash + (this.writer != null ? this.writer.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DefaultProperty other = (DefaultProperty) obj;
        if (this.defined != other.defined) {
            return false;
        }
        if (this.writable != other.writable) {
            return false;
        }
        if (this.readable != other.readable) {
            return false;
        }
        if (this.fieldDefined != other.fieldDefined) {
            return false;
        }
        if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
            return false;
        }
        if (this.type != other.type && (this.type == null || !this.type.equals(other.type))) {
            return false;
        }
        if ((this.retainType == null) ? (other.retainType != null) : !this.retainType.equals(other.retainType)) {
            return false;
        }
        if (this.realType != other.realType && (this.realType == null || !this.realType.equals(other.realType))) {
            return false;
        }
        if (this.ignore != other.ignore) {
            return false;
        }
        if (this.reader != other.reader && (this.reader == null || !this.reader.equals(other.reader))) {
            return false;
        }
        if (this.writer != other.writer && (this.writer == null || !this.writer.equals(other.writer))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "DefaultProperty{" + "defined=" + defined + ", writable=" + writable + ", readable=" + readable + ", fieldDefined=" + fieldDefined + ", name=" + name + ", type=" + type + ", retainType=" + retainType + ", realType=" + realType + ", ignore=" + ignore + ", reader=" + reader + ", writer=" + writer + '}';
    }
}
