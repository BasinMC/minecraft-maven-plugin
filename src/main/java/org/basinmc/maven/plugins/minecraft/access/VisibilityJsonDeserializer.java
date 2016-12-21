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
package org.basinmc.maven.plugins.minecraft.access;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import org.jboss.forge.roaster.model.Visibility;

import java.io.IOException;

import javax.annotation.Nonnull;

/**
 * Provides a Jackson deserializer in order to make AT configs a little more human readable.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
public class VisibilityJsonDeserializer extends JsonDeserializer<Visibility> {

    /**
     * {@inheritDoc}
     */
    @Override
    public Visibility deserialize(@Nonnull JsonParser jsonParser, @Nonnull DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        String name = jsonParser.getText().toUpperCase();

        if ("PACKAGE-PRIVATE".equals(name)) {
            return Visibility.PACKAGE_PRIVATE;
        }

        return Visibility.valueOf(name);
    }
}
