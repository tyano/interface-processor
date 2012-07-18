/*
 * Copyright 2011 Tsutomu YANO.
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
package com.shelfmap.classprocessor.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Tsutomu YANO
 */
public final class IO {

    public static void close(Closeable target, Object owner) {
        assert owner != null;
        close(target, owner.getClass());
    }

    public static void close(Closeable target, Class<?> loggerOwner) {
        assert loggerOwner != null;
        if(target != null) {
            try {
                target.close();
            } catch (IOException ex) {
                Logger logger = LoggerFactory.getLogger(loggerOwner);
                logger.error("Could not close an object.", ex);
            }
        }
    }

    private static final int BUFFER_SIZE = 1024;

    public static byte[] readBytes(InputStream stream) throws IOException {
        byte[] bytes = new byte[BUFFER_SIZE];
        ByteArrayOutputStream output = new ByteArrayOutputStream(BUFFER_SIZE);
        try {
            int size = stream.read(bytes);
            while(size >= 0) {
                if(size > 0) {
                    output.write(bytes, 0, size);
                }
                size = stream.read(bytes);
            }
        } finally {
            close(output, IO.class);
        }
        return output.toByteArray();
    }
}
