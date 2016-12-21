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

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Represents the information attached to a Minecraft version including its name, state and download
 * URLs.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Immutable
@ThreadSafe
@JsonIgnoreProperties(ignoreUnknown = true)
public class VersionMetadata {
    private final String id;
    private final VersionType type;
    private final DownloadDescriptorMap downloads;

    @JsonCreator
    public VersionMetadata(@Nonnull @JsonProperty(value = "id", required = true) String id, @Nonnull @JsonProperty(value = "type", required = true) String type, @Nonnull @JsonProperty(value = "downloads", required = true) DownloadDescriptorMap downloads) {
        this.id = id;
        this.type = VersionType.fromString(type);
        this.downloads = downloads;
    }

    public String getId() {
        return this.id;
    }

    public VersionType getType() {
        return this.type;
    }

    @Nonnull
    public DownloadDescriptor getClientDownload() {
        return this.downloads.client;
    }

    @Nonnull
    public DownloadDescriptor getServerDownload() {
        return this.downloads.server;
    }

    /**
     * Represents an internal map of downloads.
     */
    @Immutable
    @ThreadSafe
    private static class DownloadDescriptorMap {
        private final DownloadDescriptor client;
        private final DownloadDescriptor server;

        @JsonCreator
        public DownloadDescriptorMap(@Nonnull @JsonProperty(value = "client", required = true) DownloadDescriptor client, @Nonnull @JsonProperty(value = "server", required = true) DownloadDescriptor server) {
            this.server = server;
            this.client = client;
        }
    }
}
