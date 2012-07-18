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
package com.shelfmap.classprocessor.util;

/**
 *
 * @author Tsutomu YANO
 */
public final class Strings {
    private Strings() {
        super();
    }

    public static String capitalize(CharSequence value) {
        if(value == null) return null;
        if(value.length() == 0) return "";
        if(value.length() == 1) return String.valueOf(Character.toUpperCase(value.charAt(0)));

        return new StringBuilder()
                    .append(Character.toUpperCase(value.charAt(0)))
                    .append(value.subSequence(1, value.length()))
                    .toString();
    }

    public static String uncapitalize(CharSequence value) {
        if(value == null) return null;
        if(value.length() == 0) return "";
        if(value.length() == 1) return String.valueOf(Character.toLowerCase(value.charAt(0)));

        return new StringBuilder()
                    .append(Character.toLowerCase(value.charAt(0)))
                    .append(value.subSequence(1, value.length()))
                    .toString();
    }
}
