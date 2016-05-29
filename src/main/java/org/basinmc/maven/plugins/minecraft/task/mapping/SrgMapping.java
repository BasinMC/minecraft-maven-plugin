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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.objectweb.asm.commons.Remapper;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * <strong>CSRG Remapper</strong>
 *
 * Remaps jars using CSRG mappings.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
public class SrgMapping extends Remapper {
        private final Table<String, String, String> fieldNameMap;
        private final Table<String, String, String> methodNameMap;
        private final Map<String, String> typeMap;

        public SrgMapping(@Nonnull Path csrgPath) throws IOException {
                super();

                this.typeMap = new HashMap<>();
                this.methodNameMap = HashBasedTable.create();
                this.fieldNameMap = HashBasedTable.create();

                try (FileInputStream inputStream = new FileInputStream(csrgPath.toFile())) {
                        try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
                                try (BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                                        bufferedReader.lines().forEach((line) -> {
                                                String[] lineEx = line.split(" ");

                                                switch (lineEx.length) {
                                                        case 2:
                                                                this.typeMap.put(lineEx[0], lineEx[1]);
                                                                break;
                                                        case 3:
                                                                this.fieldNameMap.put(lineEx[0], lineEx[1], lineEx[2]);
                                                                break;
                                                        case 4:
                                                                this.methodNameMap.put(lineEx[0], lineEx[1] + " " + lineEx[2], lineEx[3]);
                                                                break;
                                                }
                                        });
                                }
                        }
                }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String map(String typeName) {
                return this.mapType(typeName);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String mapFieldName(String owner, String name, String desc) {
                String mappedName = this.fieldNameMap.get(owner, name);

                if (mappedName == null) {
                        return name;
                }

                return mappedName;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String mapMethodName(String owner, String name, String desc) {
                String mappedName = this.methodNameMap.get(owner, name + " " + desc);

                if (mappedName == null) {
                        return name;
                }

                return mappedName;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String mapType(String type) {
                return this.typeMap.getOrDefault(type, type);
        }
}
