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
package org.basinmc.maven.plugins.minecraft.patch;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.basinmc.maven.plugins.minecraft.AbstractMinecraftMojo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.IOException;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Provides a safeguard to prevent accidental loss of changes to the codebase when running a
 * standard build with pending modifications within the repository.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "safeguard",
        requiresProject = false,
        threadSafe = true,
        defaultPhase = LifecyclePhase.VALIDATE
)
@Immutable
@ThreadSafe
public class SafeguardMojo extends AbstractMinecraftMojo {

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.verifyProperties("sourceDirectory", "force");

        this.getLog().info("Running repository safeguard checks");

        if (this.isForced()) {
            this.getLog().warn(
                    "Skipping safeguard - Override is enabled\n" +
                            "\n" +
                            "+--------------------------------------+\n" +
                            "|                                      |\n" +
                            "|    THIS MAY CAUSE LOSS OF CHANGES    |\n" +
                            "|         USE AT YOUR OWN RISK         |\n" +
                            "|                                      |\n" +
                            "+--------------------------------------+\n"
            );
            return;
        }

        try {
            Repository repository = new FileRepositoryBuilder()
                    .setWorkTree(this.getSourceDirectory())
                    .build();

            if (!repository.getObjectDatabase().exists()) {
                this.getLog().info("Skipping safeguard - No repository in source path");
                return;
            }

            Git git = new Git(repository);

            if (!git.status().call().isClean()) {
                this.getLog().error("The repository at " + this.getSourceDirectory().toString() + " is not in a clean state");
                this.getLog().error("As such the build will be halted - Please verify that all changes you wish to retain have been commited and turned into patch files");
                this.getLog().error("If you wish to override this behavior, start the build by passing -Dminecraft.force=true");

                throw new MojoFailureException("Repository is in a dirty state");
            }

            // TODO: Compare number of commits since first commit against amount of patches to
            // prevent accidental loss of changes due to the pending git reset operation
        } catch (GitAPIException ex) {
            throw new MojoFailureException("Failed to execute git command: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoFailureException("Could not access source repository: " + ex.getMessage(), ex);
        }
    }
}
