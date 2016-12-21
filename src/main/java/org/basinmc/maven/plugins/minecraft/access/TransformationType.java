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

import org.jboss.forge.roaster.model.Visibility;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Represents the mapping of a class's visibility and its member visibilities (excluding the nested
 * type visibility).
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Immutable
@ThreadSafe
public class TransformationType {
    private final Visibility visibility;
    private final Map<String, Visibility> fields;
    private final Map<String, Visibility> methods;

    @JsonCreator
    private TransformationType(@Nullable @JsonProperty("visibility") Visibility visibility, @Nullable @JsonProperty("fields") Map<String, Visibility> fields, @Nullable @JsonProperty("methods") Map<String, Visibility> methods) {
        if (fields == null) {
            fields = new HashMap<>();
        }

        if (methods == null) {
            methods = new HashMap<>();
        }

        this.visibility = visibility;
        this.fields = fields;
        this.methods = methods;
    }

    /**
     * Retrieves the altered visibility for a field of a specific name (if any).
     */
    @Nonnull
    public Optional<Visibility> getFieldVisibility(@Nonnull String fieldName) {
        return Optional.ofNullable(this.fields.get(fieldName));
    }

    /**
     * Retrieves the altered visibility for a method of a specific name (if any).
     */
    @Nonnull
    public Optional<Visibility> getMethodVisibility(@Nonnull String methodName) {
        return Optional.ofNullable(this.methods.get(methodName));
    }

    /**
     * Retrieves the type visibility.
     */
    @Nonnull
    public Optional<Visibility> getVisibility() {
        return Optional.ofNullable(this.visibility);
    }
}
