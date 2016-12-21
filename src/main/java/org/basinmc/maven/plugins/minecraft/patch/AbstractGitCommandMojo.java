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
import org.basinmc.maven.plugins.minecraft.AbstractMinecraftMojo;
import org.codehaus.plexus.util.IOUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nonnull;

/**
 * Provides a base Mojo to implementations which interact directly with the local git installation.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
public abstract class AbstractGitCommandMojo extends AbstractMinecraftMojo {

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.verifyProperties("sourceDirectory");
        this.verifyGitInstallation();
    }

    /**
     * Executes a command as specified by the supplied process builder.
     */
    protected int execute(@Nonnull ProcessBuilder builder) throws IOException, InterruptedException {
        final Process process = builder.start();

        if (process.waitFor() != 0) {
            try (InputStream errorStream = process.getErrorStream()) {
                this.getLog().error(IOUtil.toString(errorStream));
            }
        }

        return process.exitValue();
    }

    /**
     * Verifies whether git is installed and available within the current system search path.
     */
    protected boolean isGitInstalled() {
        try {
            return this.execute(new ProcessBuilder("git", "--version")) == 0;
        } catch (FileNotFoundException ex) {
            this.getLog().error("Failed to locate git in executable search path: " + ex.getMessage());
        } catch (InterruptedException ex) {
            this.getLog().error("Received interrupt while waiting for git to exit: " + ex.getMessage());
        } catch (IOException ex) {
            this.getLog().error("Failed to access git executable: " + ex.getMessage(), ex);
        }

        return false;
    }

    /**
     * Verifies whether the local git installation is present and accessible.
     */
    protected void verifyGitInstallation() throws MojoFailureException {
        if (!this.isGitInstalled()) {
            throw new MojoFailureException("Could not locate git installation");
        }
    }
}
