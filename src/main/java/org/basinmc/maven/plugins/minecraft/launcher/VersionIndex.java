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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Provides an index of Minecraft versions.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Immutable
@ThreadSafe
@JsonIgnoreProperties(ignoreUnknown = true)
public class VersionIndex {
    private static final String URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    static final ObjectReader READER;
    private final Map<String, VersionDescriptor> maps;

    static {
        ObjectMapper mapper = new ObjectMapper();
        READER = mapper.reader();
    }

    public VersionIndex(@Nonnull @JsonProperty(value = "versions", required = true) List<VersionDescriptor> versions) {
        HashMap<String, VersionDescriptor> map = new HashMap<>();
        {
            for (VersionDescriptor version : versions) {
                map.put(version.getId(), version);
            }
        }
        this.maps = Collections.unmodifiableMap(map);
    }

    /**
     * Returns the descriptor for a certain Minecraft version.
     */
    @Nonnull
    public Optional<VersionDescriptor> getDescriptor(@Nonnull String id) {
        return Optional.ofNullable(this.maps.get(id));
    }

    /**
     * Retrieves the version metadata for a specific version ID.
     */
    @Nonnull
    public Optional<VersionMetadata> getMetadata(@Nonnull String id) throws IOException {
        try {
            return this.getDescriptor(id).map(this::fetchMetadata);
        } catch (WrapperException ex) {
            Throwable inner = ex.getCause();

            if (inner == null) {
                throw new RuntimeException("Unexpected empty wrapper exception: " + ex.getMessage(), ex);
            } else if (!(inner instanceof IOException)) {
                throw new RuntimeException("Unexpected exception: " + ex.getMessage(), ex);
            }

            throw (IOException) inner;
        }
    }

    /**
     * Fetches a version index from the Mojang servers.
     */
    @Nonnull
    public static VersionIndex fetch() throws IOException {
        HttpClient client = HttpClients.createMinimal();

        HttpGet request = new HttpGet(URL);
        HttpResponse response = client.execute(request);

        StatusLine line = response.getStatusLine();

        if (line.getStatusCode() != 200) {
            throw new IOException("Unexpected response code: " + line.getStatusCode() + " - " + line.getReasonPhrase());
        }

        try (InputStream inputStream = response.getEntity().getContent()) {
            return READER.forType(VersionIndex.class).readValue(inputStream);
        }
    }

    /**
     * Fetches a version metadata object from the URL indicated by a supplied descriptor.
     */
    @Nonnull
    private VersionMetadata fetchMetadata(@Nonnull VersionDescriptor descriptor) {
        HttpClient client = HttpClients.createMinimal();

        try {
            HttpGet request = new HttpGet(descriptor.getUrl().toURI());
            HttpResponse response = client.execute(request);

            StatusLine status = response.getStatusLine();

            if (status.getStatusCode() != 200) {
                throw new IOException("Unexpected status code: " + status.getStatusCode() + " - " + status.getReasonPhrase());
            }

            try (InputStream inputStream = response.getEntity().getContent()) {
                return READER.forType(VersionMetadata.class).readValue(inputStream);
            }
        } catch (IOException ex) {
            throw new WrapperException(ex);
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("Invalid metadata URL: " + ex.getMessage(), ex);
        }
    }

    /**
     * Provides an exception type to transport exceptions through the boundaries of Java's standard
     * functionals.
     *
     * Note: This is an absolute hack and only necessary due to the lack of functionals with
     * exceptions.
     */
    private static final class WrapperException extends RuntimeException {

        public WrapperException(Throwable throwable) {
            super(throwable);
        }
    }
}
