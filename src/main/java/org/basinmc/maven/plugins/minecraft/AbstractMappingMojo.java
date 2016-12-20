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
package org.basinmc.maven.plugins.minecraft;

import org.apache.maven.artifact.Artifact;
import org.basinmc.maven.plugins.minecraft.AbstractArtifactMojo;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Provides utility methods to all types which rely on interacting with mapping artifacts.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Immutable
@ThreadSafe
public abstract class AbstractMappingMojo extends AbstractArtifactMojo {

    /**
     * Retrieves a mapping artifact.
     */
    @Nonnull
    protected Artifact getMappingArtifact() {
        return this.createArtifactWithClassifier(MINECRAFT_GROUP_ID, this.getModule(), this.getMappingArtifactVersion(), MAPPED_CLASSIFIER);
    }

    /**
     * Retrieves the version attributed to all mapped artifacts.
     */
    @Nonnull
    protected String getMappingArtifactVersion() {
        return this.getGameVersion() + "-" + ("live".equals(this.getMappingVersion()) ? MCP_LIVE_VERSION : this.getMappingVersion());
    }
}
