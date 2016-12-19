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

/**
 * Provides a list of known (and technically unknown) types of releases as returned by the launcher
 * metadata API.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
public enum VersionType {

    /**
     * Represents an outdated alpha version of the game which was released during the game's
     * development phase.
     */
    OLD_ALPHA,

    /**
     * Represents an outdated beta version of the game which was released during the game's
     * development phase.
     */
    OLD_BETA,

    /**
     * Represents an untested version of the game which will be promoted to a {@link #RELEASE}
     * when all of its features are implemented and its contents are sufficiently stable.
     */
    SNAPSHOT,

    /**
     * Represents a modern (semi supported) version of the game.
     */
    RELEASE,

    /**
     * A release type which has not yet classified within our client implementation.
     */
    UNKNOWN
}
