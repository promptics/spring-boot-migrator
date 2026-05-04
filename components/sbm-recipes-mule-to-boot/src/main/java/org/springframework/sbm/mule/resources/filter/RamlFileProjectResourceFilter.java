/*
 * Copyright 2021 - 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.sbm.mule.resources.filter;

import org.springframework.sbm.common.api.TextResource.TextSource;
import org.springframework.rewrite.resource.ProjectResourceSet;
import org.springframework.rewrite.resource.finder.ProjectResourceFinder;
import org.openrewrite.marker.Markers;
import org.openrewrite.text.PlainText;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class RamlFileProjectResourceFilter implements ProjectResourceFinder<List<TextSource>> {
    @Override
    public List<TextSource> apply(ProjectResourceSet projectResourceSet) {
        return projectResourceSet.stream()
                .filter(r -> r.getAbsolutePath().toString().endsWith(".raml"))
                .map(r -> {
                    if (PlainText.class.isInstance(r.getSourceFile())) {
                        return new TextSource(r.getAbsoluteProjectDir(), (PlainText) r.getSourceFile());
                    }
                    // OR 8.80.1 wraps unknown extensions (.raml) as Quark; rebuild a PlainText
                    // by reading the file content from disk so the action can process it.
                    try {
                        String text = Files.readString(r.getAbsolutePath(), StandardCharsets.UTF_8);
                        PlainText plainText = new PlainText(
                                UUID.randomUUID(),
                                r.getAbsoluteProjectDir().relativize(r.getAbsolutePath()),
                                Markers.EMPTY,
                                StandardCharsets.UTF_8.name(),
                                false,
                                null,
                                null,
                                text,
                                Collections.emptyList());
                        return new TextSource(r.getAbsoluteProjectDir(), plainText);
                    } catch (IOException e) {
                        throw new RuntimeException("Could not read raml file " + r.getAbsolutePath(), e);
                    }
                })
                .collect(Collectors.toList());
    }
}
