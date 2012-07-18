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
package com.shelfmap.classprocessor;

import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tsutomu YANO
 */
public interface Attribute {
    String getName();
    TypeMirror getType();
    String getRetainType();
    void setRetainType(String type);
    
    boolean isIgnored();
    void setIgnored(boolean ignore);

    /**
     * @return Concrete class for this property. it must be defined manually if this property is a kind of Collection.
     */
    TypeMirror getRealType();
    void setRealType(TypeMirror type);
}
