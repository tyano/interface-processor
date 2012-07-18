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

import javax.lang.model.element.ExecutableElement;

/**
 *
 * @author Tsutomu YANO
 */
public interface Property extends Attribute {
    /**
     * @return true if a property is defined by the interface, not by superclasses.
     */
    boolean isDefined();
    boolean isReadable();
    boolean isWritable();
    
    /**
     * return true if a backend field for a property is already defined.
     * if this is true, processor would not generate a field for this property.
     * @return true if a backend field for a property is already defined.
     */
    boolean isFieldDefined();
    void setFieldDefined(boolean defined);

    ExecutableElement getReader();
    void setReader(ExecutableElement element);
    ExecutableElement getWriter();
    void setWriter(ExecutableElement element);
}
