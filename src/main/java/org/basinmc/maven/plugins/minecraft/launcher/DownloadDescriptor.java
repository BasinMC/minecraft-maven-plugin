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

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Represents the information required to retrieve and verify a Minecraft version artifact.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Immutable
@ThreadSafe
@JsonIgnoreProperties(ignoreUnknown = true)
public class DownloadDescriptor {
    private final String sha1;
    private final URL url;

    @JsonCreator
    public DownloadDescriptor(@Nonnull @JsonProperty(value = "sha1", required = true) String sha1, @Nonnull @JsonProperty(value = "url", required = true) URL url) {
        this.sha1 = sha1;
        this.url = url;
    }

    /**
     * Fetches the artifact from the server and stores it in a specified file.
     */
    public void fetch(@Nonnull Path outputFile) throws IOException {
        HttpClient client = HttpClients.createMinimal();

        try {
            HttpGet request = new HttpGet(this.url.toURI());
            HttpResponse response = client.execute(request);

            StatusLine line = response.getStatusLine();

            if (line.getStatusCode() != 200) {
                throw new IOException("Unexpected status code: " + line.getStatusCode() + " - " + line.getReasonPhrase());
            }

            try (InputStream inputStream = response.getEntity().getContent()) {
                try (FileChannel fileChannel = FileChannel.open(outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                    try (ReadableByteChannel inputChannel = Channels.newChannel(inputStream)) {
                        fileChannel.transferFrom(inputChannel, 0, Long.MAX_VALUE);
                    }
                }
            }
        } catch (URISyntaxException ex) {
            throw new IOException("Received invalid URI from API: " + ex.getMessage(), ex);
        }
    }

    @Nonnull
    public String getSha1() {
        return this.sha1;
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
        DownloadDescriptor that = (DownloadDescriptor) o;
        return Objects.equals(this.sha1, that.sha1) &&
                Objects.equals(this.url, that.url);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(this.sha1, this.url);
    }
}
