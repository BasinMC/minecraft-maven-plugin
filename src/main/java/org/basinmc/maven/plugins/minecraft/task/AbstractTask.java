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
package org.basinmc.maven.plugins.minecraft.task;

import org.apache.maven.plugin.logging.Log;
import org.basinmc.maven.plugins.minecraft.MinecraftMojo;

import javax.annotation.Nonnull;

/**
 * <strong>Abstract Task</strong>
 *
 * Provides an abstract task
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
public abstract class AbstractTask implements Task {
        private final MinecraftMojo mojo;

        public AbstractTask(@Nonnull MinecraftMojo mojo) {
                this.mojo = mojo;
        }

        /**
         * Retrieves the parent logger.
         *
         * @return a logger.
         */
        @Nonnull
        protected Log getLog() {
                return this.mojo.getLog();
        }

        /**
         * Retrieves the parent mojo instance.
         *
         * @return an instance.
         */
        @Nonnull
        protected MinecraftMojo getMojo() {
                return this.mojo;
        }
}
