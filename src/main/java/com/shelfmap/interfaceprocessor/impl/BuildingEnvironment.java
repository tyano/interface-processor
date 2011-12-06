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

import com.shelfmap.interfaceprocessor.Environment;
import com.shelfmap.interfaceprocessor.InterfaceDefinition;
import javax.annotation.processing.ProcessingEnvironment;


/**
 *
 * @author Tsutomu YANO
 */
public class BuildingEnvironment implements Environment {

    private final ProcessingEnvironment p;
    private final InterfaceDefinition def;
    private int level = 0;

    public BuildingEnvironment(ProcessingEnvironment p, InterfaceDefinition def) {
        this.p = p;
        this.def = def;
    }

    @Override
    public ProcessingEnvironment getProcessingEnvironment() {
        return p;
    }

    @Override
    public InterfaceDefinition getInterfaceDefinition() {
        return def;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public void setLevel(int level) {
        this.level = level;
    }
}
