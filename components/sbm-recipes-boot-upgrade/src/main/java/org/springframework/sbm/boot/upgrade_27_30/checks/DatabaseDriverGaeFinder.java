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
package org.springframework.sbm.boot.upgrade_27_30.checks;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.java.tree.JavaType;
import org.springframework.rewrite.parser.maven.ClasspathDependencies;
import org.springframework.sbm.build.api.Module;
import org.springframework.sbm.engine.context.ProjectContext;
import org.springframework.sbm.java.api.JavaSource;
import org.springframework.sbm.java.impl.OpenRewriteJavaSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class DatabaseDriverGaeFinder implements Sbm30_Finder<Set<Module>> {

    private static final String APP_ENGINE_DRIVER_FQCN = "com.google.appengine.api.rdbms.AppEngineDriver";
    private static final String APP_ENGINE_DRIVER_RESOURCE = "com/google/appengine/api/rdbms/AppEngineDriver.class";

    @Override
    @NotNull
    public Set<Module> findMatches(ProjectContext context) {
        return context.getApplicationModules()
                .stream()
                .filter(this::hasClassAppEngineDriverOnClasspath)
                .collect(Collectors.toSet());
    }

    private boolean hasClassAppEngineDriverOnClasspath(Module m) {
        return Stream.concat(m.getTestJavaSourceSet().stream(), m.getMainJavaSourceSet().stream())
                .anyMatch(this::dependsOnAppEngineDriver);
    }

    private boolean dependsOnAppEngineDriver(JavaSource js) {
        if (!(js instanceof OpenRewriteJavaSource javaSource)) {
            return false;
        }
        var markers = javaSource.getSourceFile().getMarkers();
        // Prefer the resolved-types marker when the class is reachable; OR 8.80.1's
        // JavaParser only resolves a subset of jar contents (transitive closure of
        // referenced types), so fall back to scanning ClasspathDependencies jars
        // directly when the FQCN is missing from the resolved set.
        boolean inResolvedTypes = markers.findFirst(org.openrewrite.java.marker.JavaSourceSet.class)
                .map(jss -> jss.getClasspath().stream()
                        .map(JavaType.FullyQualified::getFullyQualifiedName)
                        .anyMatch(APP_ENGINE_DRIVER_FQCN::equals))
                .orElse(false);
        if (inResolvedTypes) {
            return true;
        }
        return markers.findFirst(ClasspathDependencies.class)
                .map(cd -> cd.getDependencies().stream().anyMatch(this::jarContainsAppEngineDriver))
                .orElse(false);
    }

    private boolean jarContainsAppEngineDriver(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            return jar.getEntry(APP_ENGINE_DRIVER_RESOURCE) != null;
        } catch (IOException e) {
            return false;
        }
    }
}
