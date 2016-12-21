# Minecraft Maven Plugin ![State](https://img.shields.io/badge/state-prototype-orange.svg) [![Latest Tag](https://img.shields.io/github/release/basinmc/minecraft-maven-plugin.svg)](https://github.com/BasinMC/minecraft-maven-plugin/releases)

The Minecraft maven plugin allows its users to easily apply patches to the Minecraft server or
client using maven's well established build management.

### Requirements

* Maven 2+
* Git in the executing shell's PATH

## Usage

```xml
<pluginRepositories>
        <pluginRepository>
                <id>basin</id>
                <name>Basin</name>
                <url>https://www.basinmc.org/nexus/repository/maven-releases/</url>
        </pluginRepository>
</pluginRepositories>
```

For an example configuration, refer to the [Example Project](example/pom.xml).

| Property             | Type    | User Property   | Default                                                | Purpose                                                                                                                 |
| -------------------- | ------- | --------------- | ------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------- |
| gameVersion          | String  | N/A             | N/A                                                    | Specifies the vanilla game version to retrieve and patch.                                                               |
| mappingVersion       | String  | N/A             | N/A                                                    | Specifies the [MCP version](http://export.mcpbot.bspk.rs) to download and apply.                                        |
| module               | String  | N/A             | N/A                                                    | Indicates which module (server or client) is going to be built against.                                                 |
| patchDirectory       | File    | N/A             | ${project.basedir}/src/minecraft/patch                 | Specifies where the patches will be pulled from/written to.                                                             |
| sourceDirectory      | File    | N/A             | ${project.basedir}/src/minecraft/java                  | Specifies where the decompiled and patched Minecraft sources will be stored.                                            |
| resourceDirectory    | File    | N/A             | ${project.build.directory}/generated-sources/minecraft | Specifies where the non-code resources will be stored.                                                                  |
| accessTransformation | File    | N/A             | N/A                                                    | Indicates whether there is and where to locate an [Access Transformation configuration](example/src/minecraft/at.json). |
| force                | Boolean | minecraft.force | false                                                  | Indicates whether the git safeguard shall be skipped.                                                                   |

| Goal                  | Phase               | Purpose                                                                                                                      |
| --------------------- | ------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| safeguard             | Validate            | Ensures all changes to the source were commited and turned into a respective patch file to avoid accidental loss of changes. |
| fetch-module          | Initialize          | Fetches a vanilla Minecraft artifact and caches it.                                                                          |
| fetch-mappings        | Initialize          | Fetches MCP and SRG mappings and caches them.                                                                                |
| apply-mappings        | Initialize          | Generates a mapped Minecraft artifact and caches it.                                                                         |
| decompile-module      | Initialize          | Generated a source Minecraft artifact and caches it.                                                                         |
| initialize-repository | Generated Sources   | Extracts a Minecraft source artifact and adds them to a local git repository.                                                |
| extract-resources     | Generated Resources | Extracts all non-code Minecraft sources.                                                                                     |
| apply-patches         | Generate Sources    | Applies all patches within the patches directory to the local git repository.                                                |
| generated-patches     | Generate Sources    | Re-generates patches based on the commit history within the local git repository.                                            |
    
Generally it is recommended to set `resourceDirectory` to a value which is cleaned automatically in
order to indicate to other developers that modifications to these files will be overridden.

In addition all users should configure their IDEs to be able to execute
`the org.basinmc.maven.plugins:minecraft-maven-plugin:generate-patches` goal from within their
project directory in order to re-generate their patches from changes made and commited in the source
directory.

## Acknowledgements

This plugin wouldn't be possible without the great people bind [MCP Bot](http://mcbot.bspk.rs/) who
provide access to the most recent version of MCP as well as the CSRG mappings for the game. We've
been searching for mappings that fit our needs for quite a bit and this solution is by far the most
outstanding.

## Need Help?

The [official documentation][wiki] has help articles and specifications on the implementation. If,
however, you still require assistance with the application, you are welcome to join our
[IRC Channel](#contact) and ask veteran users and developers. Make sure to include a detailed
description of your problem when asking questions though:

1. Include a complete error message along with its stack trace when applicable.
2. Describe the expected result.
3. Describe the actual result when different from the expected result.

[wiki]: https://github.com/BasinMC/minecraft-maven-plugin/wiki

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for information on working on minecraft-maven-plugin and
submitting patches. You can also join the [project's chat room](#contact) to discuss future
improvements or to get your custom implementation listed.

## Contact

**IRC:** irc.basinmc.org (port 6667 or port +6697) in [#Basin](irc://irc.basinmc.org/Basin)<br />
**Website:** https://www.basinmc.org
