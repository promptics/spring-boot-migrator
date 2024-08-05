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
package org.springframework.sbm;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.config.Environment;
import org.openrewrite.java.Assertions;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.springframework.rewrite.plugin.polyglot.RewritePlugin;
import org.springframework.rewrite.plugin.shared.PluginInvocationResult;
import org.springframework.sbm.project.resource.TestProjectContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

/**
 * @author Fabian Krüger
 */
public class JaxRsThroughAdapterTest {

    @Nested
    class WithRewriteTest implements RewriteTest {
        @Override
        public void defaults(RecipeSpec spec) {
            // mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
            List<Path> deps = Arrays.stream("/Users/fkrueger/.m2/repository/org/jboss/spec/javax/ws/rs/jboss-jaxrs-api_2.1_spec/1.0.1.Final/jboss-jaxrs-api_2.1_spec-1.0.1.Final.jar".split(":")).map(d -> Path.of(d)).toList();

            spec.parser(JavaParser.fromJavaVersion().classpath(deps));
            String RECIPE = "example.recipe.SbmAdapterRecipe";

            spec.recipe(Environment.builder()
                    .scanRuntimeClasspath("example.recipe")
                    .build()
                    .activateRecipes(RECIPE));
        }

        @Test
        public void theTest() {
            rewriteRun(
                    (spec) -> spec.expectedCyclesThatMakeChanges(2),
                    mavenProject("project",
                            Assertions.java(
                                    """
                                    package com.example.jee.app;
                                                                        
                                    import javax.ws.rs.*;
                                    import javax.ws.rs.core.MediaType;
                                    import javax.ws.rs.core.Response;
                                    import java.util.stream.Collectors;
                                                                        
                                    import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
                                    import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;
                                                                        
                                    @Path("/")
                                    @Produces("application/json")
                                    public class PersonController {
                                                                        
                                        @POST
                                        @Path("/json/{name}")
                                        @Consumes("application/json")
                                        public String getHelloWorldJSON(@PathParam("name") String name) throws Exception {
                                            System.out.println("name: " + name);
                                            return "{\\"Hello\\":\\"" + name + "\\"";
                                        }
                                                                        
                                        @GET
                                        @Path("/json")
                                        @Produces(APPLICATION_JSON)
                                        @Consumes(APPLICATION_JSON)
                                        public String getAllPersons(@QueryParam("q") String searchBy, @DefaultValue("0") @QueryParam("page") int page) throws Exception {
                                            return "{\\"message\\":\\"No person here...\\"";
                                        }
                                                                        
                                                                        
                                        @POST
                                        @Path("/xml/{name}")
                                        @Produces(MediaType.APPLICATION_XML)
                                        @Consumes(MediaType.APPLICATION_XML)
                                        public String getHelloWorldXML(@PathParam("name") String name) throws Exception {
                                            System.out.println("name: " + name);
                                            return "<xml>Hello "+name+"</xml>";
                                        }
                                                                        
                                        private boolean isResponseStatusSuccessful(Response.Status.Family family) {
                                            return family == SUCCESSFUL;
                                        }
                                                                        
                                    }
                                    """,
                                    """
                                    package com.example.jee.app;
                                                                        
                                    import org.springframework.web.bind.annotation.RequestMapping;
                                    import org.springframework.web.bind.annotation.RequestMethod;
                                    import org.springframework.web.bind.annotation.RestController;
                                                                        
                                    import javax.ws.rs.DefaultValue;
                                    import javax.ws.rs.PathParam;
                                    import javax.ws.rs.QueryParam;
                                    import javax.ws.rs.core.MediaType;
                                    import javax.ws.rs.core.Response;
                                                                        
                                    import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
                                    import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;
                                                                        
                                                                        
                                    @RestController
                                    @RequestMapping(value = "/", produces = "application/json")
                                    public class PersonController {
                                                                        
                                        @RequestMapping(value = "/json/{name}", consumes = "application/json", method = RequestMethod.POST)
                                        public String getHelloWorldJSON(@PathParam("name") String name) throws Exception {
                                            System.out.println("name: " + name);
                                            return "{\\"Hello\\":\\"" + name + "\\"";
                                        }
                                                                        
                                        @RequestMapping(value = "/json", produces = APPLICATION_JSON, consumes = APPLICATION_JSON, method = RequestMethod.GET)
                                        public String getAllPersons(@QueryParam("q") String searchBy, @DefaultValue("0") @QueryParam("page") int page) throws Exception {
                                            return "{\\"message\\":\\"No person here...\\"";
                                        }
                                                                        
                                                                        
                                        @RequestMapping(value = "/xml/{name}", produces = MediaType.APPLICATION_XML, consumes = MediaType.APPLICATION_XML, method = RequestMethod.POST)
                                        public String getHelloWorldXML(@PathParam("name") String name) throws Exception {
                                            System.out.println("name: " + name);
                                            return "<xml>Hello "+name+"</xml>";
                                        }
                                                                        
                                        private boolean isResponseStatusSuccessful(Response.Status.Family family) {
                                            return family == SUCCESSFUL;
                                        }
                                                                        
                                    }
                                    """
                            ),
                            pomXml(
                                    //language=xml
                                    """   
                                    <?xml version="1.0" encoding="UTF-8"?>
                                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
                                        <modelVersion>4.0.0</modelVersion>
                                        <groupId>org.springframework.sbm.examples</groupId>
                                        <artifactId>migrate-jax-rs</artifactId>
                                        <packaging>jar</packaging>
                                        <version>0.0.1-SNAPSHOT</version>
                                        <properties>
                                            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                                        </properties>
                                        <build>
                                            <plugins>
                                                <plugin>
                                                    <groupId>org.apache.maven.plugins</groupId>
                                                    <artifactId>maven-compiler-plugin</artifactId>
                                                    <version>3.5.1</version>
                                                    <configuration>
                                                        <source>1.8</source>
                                                        <target>1.8</target>
                                                    </configuration>
                                                </plugin>
                                            </plugins>
                                        </build>
                                        <dependencies>
                                            <dependency>
                                                <groupId>org.jboss.spec.javax.ws.rs</groupId>
                                                <artifactId>jboss-jaxrs-api_2.1_spec</artifactId>
                                                <version>1.0.1.Final</version>
                                            </dependency>
                                        </dependencies>
                                        <repositories>
                                            <repository>
                                                <id>jcenter</id>
                                                <name>jcenter</name>
                                                <url>https://jcenter.bintray.com</url>
                                            </repository>
                                            <repository>
                                                <id>mavencentral</id>
                                                <name>mavencentral</name>
                                                <url>https://repo.maven.apache.org/maven2</url>
                                            </repository>
                                        </repositories>
                                    </project>
                                    """
                            )
                    )
            );
        }
    }
    
    @Test
    @DisplayName("jax-rs recipes through adapter in openrewrite")
    void jaxRsRecipesThroughAdapterInOpenrewrite(@TempDir Path tmpDir) throws IOException {

        String projectRootDir = "jee/jaxrs/bootify-jaxrs";
        Path from = Path.of("./testcode").toAbsolutePath().normalize().resolve(projectRootDir).resolve("given");
        Path to = Path.of("./target/test-projects/").resolve(projectRootDir).toAbsolutePath().normalize();
        if(Files.exists(to)) {
            FileUtils.deleteDirectory(to.toFile());
        }
        Files.createDirectories(to);
        FileUtils.deleteDirectory(to.toFile());
        FileUtils.forceMkdir(to.toFile());
        FileUtils.copyDirectory(from.toFile(), to.toFile());

        String mavenPluginVersion = "";
        String gradlePluginVersion = "";
        Path baseDir = tmpDir;

//        mvn -B --fail-at-end org.openrewrite.maven:rewrite-maven-plugin:5.32.1:dryRun \
//        -Drewrite.activeRecipes=example.recipe.SbmAdapterRecipe \ 
//        -Drewrite.recipeArtifactCoordinates=org.springframework.sbm:jaxrs-recipes:0.15.2-SNAPSHOT \
//                -Dmaven.opts=“-Xms256M -Xmx1024M  -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005“


        PluginInvocationResult pluginInvocationResult = RewritePlugin.dryRun()
                .mavenPluginVersion(mavenPluginVersion)
                .gradlePluginVersion(gradlePluginVersion)
                // TODO: Add method to provide path to compiled classes
                .recipes("example.recipe.SbmAdapterRecipe")
                .dependencies("org.springframework.sbm:jaxrs-recipes:0.15.2-SNAPSHOT")
//                .withDebugging(5005, true)
//                .withDebug()
                .onDir(to);

        System.out.println(pluginInvocationResult.capturedOutput());


    }
}
