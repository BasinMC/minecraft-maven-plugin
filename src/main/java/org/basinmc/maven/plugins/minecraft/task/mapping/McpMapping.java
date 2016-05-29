/*
 * Copyright 2016 Johannes Donath <johannesd@torchmind.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.basinmc.maven.plugins.minecraft.task.mapping;

import com.opencsv.CSVReader;
import org.objectweb.asm.commons.Remapper;

import javax.annotation.Nonnull;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * <strong>Mapping</strong>
 *
 * Represents a parsed MCP mapping consisting of three different CSV documents.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
public class McpMapping extends Remapper {
        private final Map<String, String> fieldMap;
        private final Map<String, String> methodMap;
        private final String side;

        public McpMapping(@Nonnull String side, @Nonnull Path fieldMapFile, @Nonnull Path methodMapFile) throws IOException {
                this.side = side;
                int sideNumeric = ("client".equals(side) ? 0 : 1);

                {
                        Map<String, String> fieldMap = new HashMap<>();

                        CSVReader reader = new CSVReader(new FileReader(fieldMapFile.toFile()));
                        String[] line;

                        reader.readNext();

                        while ((line = reader.readNext()) != null) {
                                int mappingSide = Integer.parseUnsignedInt(line[2]);

                                if (mappingSide == 2 || mappingSide == sideNumeric) {
                                        fieldMap.put(line[0], line[1]);
                                }
                        }

                        this.fieldMap = Collections.unmodifiableMap(fieldMap);
                }

                {
                        Map<String, String> methodMap = new HashMap<>();

                        CSVReader reader = new CSVReader(new FileReader(methodMapFile.toFile()));
                        String[] line;

                        reader.readNext();

                        while ((line = reader.readNext()) != null) {
                                int mappingSide = Integer.parseUnsignedInt(line[2]);

                                if (mappingSide == 2 || mappingSide == sideNumeric) {
                                        methodMap.put(line[0], line[1]);
                                }
                        }

                        this.methodMap = Collections.unmodifiableMap(methodMap);
                }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String mapFieldName(String owner, String name, String desc) {
                return this.fieldMap.getOrDefault(name, name);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String mapMethodName(String owner, String name, String desc) {
                return this.methodMap.getOrDefault(name, name);
        }
}
