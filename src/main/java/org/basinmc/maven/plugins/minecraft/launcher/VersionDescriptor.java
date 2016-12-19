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
package org.basinmc.maven.plugins.minecraft.launcher;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URL;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Provides a representation of a Launcher Metadata version descriptor.
 *
 * Note: The URL within this type does not actually represent a download URL. It is only a reference
 * to a version metadata file which contains detailed information on a version's dependencies and
 * respective module download URLs.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Immutable
@ThreadSafe
@JsonIgnoreProperties(ignoreUnknown = true)
public class VersionDescriptor {
    private final String id;
    private final VersionType type;
    private final URL url;
    // Release Time and Time fields are omitted since we won't make use of them

    @JsonCreator
    public VersionDescriptor(@Nonnull @JsonProperty(value = "id", required = true) String id, @Nonnull @JsonProperty(value = "type", required = true) String type, @Nonnull @JsonProperty(value = "url", required = true) URL url) {
        VersionType tmp;

        try {
            tmp = VersionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException ex) {
            tmp = VersionType.UNKNOWN;
        }

        this.id = id;
        this.type = tmp;
        this.url = url;
    }

    @Nonnull
    public String getId() {
        return this.id;
    }

    @Nonnull
    public VersionType getType() {
        return this.type;
    }

    @Nonnull
    public URL getUrl() {
        return this.url;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;

        VersionDescriptor that = (VersionDescriptor) o;
        return Objects.equals(this.id, that.id) &&
                this.type == that.type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.type);
    }
}
