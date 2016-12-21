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
package org.basinmc.maven.plugins.minecraft.access;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.jboss.forge.roaster.model.Visibility;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Provides an abstraction around access transformation maps.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Immutable
@ThreadSafe
public class AccessTransformationMap {
    private final Map<String, TransformationType> map;

    @JsonCreator
    private AccessTransformationMap(@Nonnull @JsonProperty(required = true) Map<String, TransformationType> map) {
        this.map = map;
    }

    /**
     * Retrieves the mappings for a certain type (if any).
     */
    @Nonnull
    public Optional<TransformationType> getTypeMappings(@Nonnull String type) {
        // unify class name to simplify things for our callee
        if (type.endsWith(".java")) {
            type = type.substring(0, type.length() - 5);
        }

        type = type.replace("/", ".");
        return Optional.ofNullable(this.map.get(type));
    }

    /**
     * Reads an access transformation map from a supplied path.
     *
     * @throws IOException when accessing the file fails.
     */
    @Nonnull
    public static AccessTransformationMap read(@Nonnull Path path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        {
            SimpleModule module = new SimpleModule("Access Transformation Serialization");
            module.addDeserializer(Visibility.class, new VisibilityJsonDeserializer());
            mapper.registerModule(module);
        }

        try (FileInputStream inputStream = new FileInputStream(path.toFile())) {
            return mapper.readValue(inputStream, AccessTransformationMap.class);
        }
    }
}
