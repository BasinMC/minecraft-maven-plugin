# Minecraft Maven Plugin ![State](https://img.shields.io/badge/state-stable-green.svg) [![Latest Tag](https://img.shields.io/github/release/basinmc/minecraft-maven-plugin.svg)](https://github.com/BasinMC/minecraft-maven-plugin/releases)

The Minecraft maven plugin allows its users to easily apply patches to the Minecraft server or client using maven's
well established build management.

### Requirements

* Maven 2+
* Git in the executing shell's PATH

## Usage

```xml
<plugin>
        <groupId>org.basinmc.maven.plugins</groupId>
        <artifactId>minecraft-maven-plugin</artifactId>
        <version>1.0-SNAPSHOT</version>

        <configuration>
                <gameVersion>1.9.4</gameVersion>
                <mcpVersion>snapshot-20160601</mcpVersion>
                <module>server</module>
                <patchDirectory>${project.basedir}/src/minecraft/patch</patchDirectory>
                <sourceDirectory>${project.build.directory}/generated-sources/minecraft</sourceDirectory>
        </configuration>

        <executions>
                <execution>
                        <id>minecraft-safeguard</id>

                        <goals>
                                <goal>git-safeguard</goal>
                        </goals>
                </execution>
                <execution>
                        <id>minecraft-download</id>

                        <goals>
                                <goal>download</goal>
                        </goals>
                </execution>
                <execution>
                        <id>minecraft-decompile</id>

                        <goals>
                                <goal>decompile</goal>
                        </goals>
                </execution>
                <execution>
                        <id>minecraft-patch</id>

                        <goals>
                                <goal>git-patch</goal>
                        </goals>
                </execution>
        </executions>
</plugin>
```

| Property        | Type   | Default                                                | Purpose                                                                           |
| --------------- | ------ | ------------------------------------------------------ | --------------------------------------------------------------------------------- |
| gameVersion     | String | 1.9.4                                                  | Specifies the (vanilla) Minecraft game version to download and install.           |
| mcpVersion      | String | snapshot-20160601                                      | Specifies the [MCP version](http://export.mcpbot.bspk.rs/) to download and apply. |
| module          | String | server                                                 | Indicates which module (server or client) is going to be built.                   |
| patchDirectory  | File   | ${project.basedir}/src/minecraft/patch                 | Specifies where the patches to apply/generate will be stored.                     |
| sourceDirectory | Files  | ${project.build.directory}/generated-sources/minecraft | Specifies where the decompiled Minecraft sources will be stored.                  |

Generally it is recommended to set `sourceDirectory` to a value which is cleaned automatically. Alternatively it can be
set to a regular directory which prevents it from being deleted on accident automatically.

In addition all users should configure their IDEs to be able to execute
`the org.basinmc.maven.plugins:minecraft-maven-plugin:generate-patches` goal from within their project directory in
order to re-generate their patches from changes made and commited in the source directory.

## Acknowledgements

This plugin wouldn't be possible without the great people bind [MCP Bot](http://mcbot.bspk.rs/) who provide access to
the most recent version of MCP as well as the CSRG mappings for the game. We've been searching for mappings that fit our
needs for quite a bit and this solution is by far the most outstanding.

## Need Help?

The [official documentation][wiki] has help articles and specifications on the implementation. If, however, you still
require assistance with the application, you are welcome to join our [IRC Channel](#contact) and ask veteran users and
developers. Make sure to include a detailed description of your problem when asking questions though:

1. Include a complete error message along with its stack trace when applicable.
2. Describe the expected result.
3. Describe the actual result when different from the expected result.

[wiki]: https://github.com/BasinMC/minecraft-maven-plugin/wiki

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for information on working on Beacon and submitting patches. You can also join
the [project's chat room](#contact) to discuss future improvements or to get your custom implementation listed.

## Contact

**IRC:** irc.basinmc.org (port 6667 or port +66697) in [#Basin](irc://irc.basinmc.org/Basin)<br />
**Website:** https://www.basinmc.org
