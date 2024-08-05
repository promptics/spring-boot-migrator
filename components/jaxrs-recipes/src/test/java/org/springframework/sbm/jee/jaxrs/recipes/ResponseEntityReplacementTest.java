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
package org.springframework.sbm.jee.jaxrs.recipes;

import org.junit.jupiter.api.Test;
import org.springframework.sbm.engine.context.ProjectContext;
import org.springframework.sbm.engine.recipe.AbstractAction;
import org.springframework.sbm.project.resource.TestProjectContext;
import org.springframework.sbm.testhelper.common.utils.TestDiff;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class ResponseEntityReplacementTest {

    private final static String SPRING_VERSION = "5.3.13";

    final private AbstractAction action =
            new AbstractAction() {
                @Override
                public void apply(ProjectContext context) {
                    context.getProjectJavaSources().apply(
                            new SwapResponseWithResponseEntity(),
                            new ReplaceMediaType()
                    );
                }
            };


    @Test
    void testUnsupportedStaticCall() {

        String javaSource = ""
                + "import javax.ws.rs.core.Response;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public Response respond() {\n"
                + "       return Response.status(200, \"All good\").build();\n"
                + "    }\n"
                + "}\n"
                + "";

        String expected = ""
                + "import org.springframework.http.ResponseEntity;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public ResponseEntity respond() {\n"
                + "       return /* SBM FIXME: Couldn't find exact replacement for status(int, java.lang.String) - dropped java.lang.String argument */ ResponseEntity.status(200).build();\n"
                + "    }\n"
                + "}\n"
                + "";

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();


        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void testUnsupportedBuilderCall() {

        String javaSource = """
                import javax.ws.rs.core.Response;
                
                public class TestController {
                    public Response respond() {
                       return Response.status(200).tag("My Tag").build();
                    }
                }
                """;

        String expected = """
                import org.springframework.http.ResponseEntity;
                
                public class TestController {
                    public ResponseEntity respond() {
                       return ResponseEntity.status(200).eTag("My Tag").build();
                    }
                }
                """;

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void testUnsupportedBuilder() {

        String javaSource = ""
                + "import java.util.stream.LongStream;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public LongStream respond() {\n"
                + "       return LongStream.builder().add(1).add(2).build();\n"
                + "    }\n"
                + "}\n"
                + "";

        String expected = javaSource;

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void testOnlyReturnStatementBuilder() {

        String javaSource = ""
                + "import javax.ws.rs.core.Response;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public Response respond() {\n"
                + "       Response r = Response.status(200).build();\n"
                + "       return r;\n"
                + "    }\n"
                + "}\n"
                + "";

        String expected = ""
                + "import org.springframework.http.ResponseEntity;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public ResponseEntity respond() {\n"
                + "       ResponseEntity r = ResponseEntity.status(200).build();\n"
                + "       return r;\n"
                + "    }\n"
                + "}\n"
                + "";

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }


    @Test
    void testSimplestCase() {

        String javaSource = ""
                + "import javax.ws.rs.core.Response;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public Response respond() {\n"
                + "       return Response.status(200).build();\n"
                + "    }\n"
                + "}\n"
                + "";

        String expected = ""
                + "import org.springframework.http.ResponseEntity;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public ResponseEntity respond() {\n"
                + "       return ResponseEntity.status(200).build();\n"
                + "    }\n"
                + "}\n"
                + "";

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void testReplaceBuildWithBody() {

        String javaSource = ""
                + "import javax.ws.rs.core.Response;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public Response respond() {\n"
                + "       return Response.ok(\"All good!\").build();\n"
                + "    }\n"
                + "}\n"
                + "";

        String expected = ""
                + "import org.springframework.http.ResponseEntity;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public ResponseEntity respond() {\n"
                + "       return ResponseEntity.ok().body(\"All good!\");\n"
                + "    }\n"
                + "}\n"
                + "";

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void testReplaceOkWithBody() {
        String javaSource = ""
                + "import javax.ws.rs.core.Response;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public Response respond() {\n"
                + "       Response r = Response.ok(\"great!\").build();\n"
                + "       return r;\n"
                + "    }\n"
                + "}\n"
                + "";


        String expected = ""
                + "import org.springframework.http.ResponseEntity;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public ResponseEntity respond() {\n"
                + "       ResponseEntity r = ResponseEntity.ok().body(\"great!\");\n"
                + "       return r;\n"
                + "    }\n"
                + "}\n"
                + "";

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void testReplaceOkWithMediaTypeAndBody() {

        String javaSource = ""
                + "import javax.ws.rs.core.Response;\n"
                + "import javax.ws.rs.core.MediaType;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public Response respond() {\n"
                + "       return Response.ok(\"All good!\", MediaType.APPLICATION_JSON_TYPE).build();\n"
                + "    }\n"
                + "}\n"
                + "";

        String expected = ""
                + "import org.springframework.http.MediaType;\n"
                + "import org.springframework.http.ResponseEntity;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public ResponseEntity respond() {\n"
                + "       return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(\"All good!\");\n"
                + "    }\n"
                + "}\n"
                + "";

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }


    @Test
    void testReplaceOkWithMediaTypeStringAndBody() {

        String javaSource = """
                import javax.ws.rs.core.Response;
                
                public class TestController {
                
                    public Response respond() {
                       return Response.ok("All good!", "application/json").build();
                    }
                }
                """;

        String expected = """
                import org.springframework.http.MediaType;
                import org.springframework.http.ResponseEntity;
                
                public class TestController {
                
                    public ResponseEntity respond() {
                       return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/json")).body("All good!");
                    }
                }
                """;

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void accepted_1() {

        String javaSource = ""
                + "import javax.ws.rs.core.Response;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public Response respond() {\n"
                + "       return Response.accepted().build();\n"
                + "    }\n"
                + "}\n"
                + "";

        String expected = ""
                + "import org.springframework.http.ResponseEntity;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public ResponseEntity respond() {\n"
                + "       return ResponseEntity.accepted().build();\n"
                + "    }\n"
                + "}\n"
                + "";

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }


    @Test
    void accepted_2() {

        String javaSource = ""
                + "import javax.ws.rs.core.Response;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public Response respond() {\n"
                + "       return Response.accepted(\"Correct\").build();\n"
                + "    }\n"
                + "}\n"
                + "";

        String expected = ""
                + "import org.springframework.http.ResponseEntity;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public ResponseEntity respond() {\n"
                + "       return ResponseEntity.accepted().body(\"Correct\");\n"
                + "    }\n"
                + "}\n"
                + "";

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void created() {

        String javaSource = ""
                + "import javax.ws.rs.core.Response;\n"
                + "import java.net.URI;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public Response respond() {\n"
                + "       URI uri = URI.create(\"https://spring.io\");\n"
                + "       return Response.created(uri).build();\n"
                + "    }\n"
                + "}\n"
                + "";

        String expected = ""
                + "import org.springframework.http.ResponseEntity;\n"
                + "import java.net.URI;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public ResponseEntity respond() {\n"
                + "       URI uri = URI.create(\"https://spring.io\");\n"
                + "       return ResponseEntity.created(uri).build();\n"
                + "    }\n"
                + "}\n"
                + "";

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void fromResponse() {
        String javaSource = ""
                + "import javax.ws.rs.core.Response;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public Response respond() {\n"
                + "       Response r = Response.ok(\"great!\").build();\n"
                + "       return Response.fromResponse(r).build();\n"
                + "    }\n"
                + "}\n"
                + "";

        String expected = ""
                + "import org.springframework.http.ResponseEntity;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public ResponseEntity respond() {\n"
                + "       ResponseEntity r = ResponseEntity.ok().body(\"great!\");\n"
                + "       return ResponseEntity.status(r.getStatusCode()).headers(r.getHeaders()).body(r.getBody());\n"
                + "    }\n"
                + "}\n"
                + "";

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void notModified() {
        String javaSource = ""
                + "import javax.ws.rs.core.Response;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public Response respond() {\n"
                + "        Response.notModified(\"great!\");\n"
                + "        return Response.notModified().build();\n"
                + "    }\n"
                + "}\n"
                + "";

        String expected = ""
                + "import org.springframework.http.HttpStatus;\n"
                + "import org.springframework.http.ResponseEntity;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public ResponseEntity respond() {\n"
                + "        ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(\"great!\");\n"
                + "        return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();\n"
                + "    }\n"
                + "}\n"
                + "";

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void seeOther() {

        String javaSource = ""
                + "import javax.ws.rs.core.Response;\n"
                + "import java.net.URI;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public Response respond() {\n"
                + "       URI uri = URI.create(\"https://spring.io\");\n"
                + "       return Response.seeOther(uri).build();\n"
                + "    }\n"
                + "}\n"
                + "";

        String expected = ""
                + "import org.springframework.http.HttpStatus;\n"
                + "import org.springframework.http.ResponseEntity;\n"
                + "import java.net.URI;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public ResponseEntity respond() {\n"
                + "       URI uri = URI.create(\"https://spring.io\");\n"
                + "       return ResponseEntity.status(HttpStatus.SEE_OTHER).location(uri).build();\n"
                + "    }\n"
                + "}\n"
                + "";

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void serverError() {

        String javaSource = ""
                + "import javax.ws.rs.core.Response;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public Response respond() {\n"
                + "       return Response.serverError().build();\n"
                + "    }\n"
                + "}\n"
                + "";

        String expected = ""
                + "import org.springframework.http.HttpStatus;\n"
                + "import org.springframework.http.ResponseEntity;\n"
                + "\n"
                + "public class TestController {\n"
                + "\n"
                + "    public ResponseEntity respond() {\n"
                + "       return ResponseEntity.status(HttpStatus.SERVER_ERROR).build();\n"
                + "    }\n"
                + "}\n"
                + "";

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void temporaryRedirect() {

        String javaSource = """
                import javax.ws.rs.core.Response;
                import java.net.URI;
                
                public class TestController {
                
                    public Response respond() {
                       URI uri = URI.create("https://spring.io");
                       return Response.temporaryRedirect(uri).build();
                    }
                }
                """;

        String expected = """
                import org.springframework.http.HttpStatus;
                import org.springframework.http.ResponseEntity;
                import java.net.URI;
                
                public class TestController {
                
                    public ResponseEntity respond() {
                       URI uri = URI.create("https://spring.io");
                       return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).location(uri).build();
                    }
                }
                """;

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void instanceMethods() {
        String javaSource = """
                import javax.ws.rs.core.Response;
                import javax.ws.rs.core.GenericType;
                import java.lang.annotation.Annotation;
                
                public class TestController {
                
                    public String respond() {
                        Response r =  Response.ok().build();
                        r.getAllowedMethods();
                        r.bufferEntity();
                        r.close();
                        r.getCookies();
                        r.getDate();
                        r.getEntity();
                        r.bufferEntity();
                        r.getEntityTag();
                        r.getHeaders();
                        r.getHeaderString("Accept");
                        r.getLanguage();
                        r.getLastModified();
                        r.getLength();
                        r.getLink("Something");
                        r.getLinkBuilder("Something");
                        r.getLinks();
                        r.getLocation();
                        r.getMediaType();
                        r.getMetadata();
                        r.getStatus();
                        r.getStatusInfo();
                        r.getStringHeaders();
                        r.hasEntity();
                        r.hasLink("Something");
                        r.readEntity(String.class, new Annotation[0]);
                        r.readEntity(GenericType.forInstance("Something"));
                        r.readEntity(GenericType.forInstance("Something"), new Annotation[0]);
                        return r.readEntity(String.class);
                    }
                }
                """;

        String expected = """
                import org.springframework.http.ResponseEntity;
                import java.util.Date;
                import java.util.stream.Collectors;
                
                public class TestController {
                
                    public String respond() {
                        ResponseEntity r =  ResponseEntity.ok().build();
                        r.getHeaders().getAllow().stream().map(m -> m.toString()).collect(Collectors.toList());
                        r.bufferEntity();
                        r.close();
                        r.getCookies();
                        new Date(r.getHeaders().getDate());
                        r.getBody();
                        r.bufferEntity();
                        r.getHeaders().getETag();
                        r.getHeaders();
                        r.getHeaders().get("Accept").stream().collect(Collectors.joining(", "));
                        r.getHeaders().getContentLanguage();
                        new Date(r.getHeaders().getLastModified());
                        r.getHeaders().getContentLength();
                        r.getLink("Something");
                        r.getLinkBuilder("Something");
                        r.getLinks();
                        r.getHeaders().getLocation();
                        r.getHeaders().getContentType();
                        r.getHeaders();
                        r.getStatusCodeValue();
                        r.getStatusCode();
                        r.getHeaders();
                        r.hasBody();
                        r.hasLink("Something");
                        r.getBody();
                        r.getBody();
                        r.getBody();
                        return r.getBody();
                    }
                }
                """;

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void chain_1() {
        String javaSource = """
                import javax.ws.rs.core.Response;
                import javax.ws.rs.core.MediaType;
                
                public class TestController {
                
                    public Response respond() {
                       return Response.status(200).entity("Hello").tag("My Tag").type(MediaType.TEXT_PLAIN_TYPE).build();
                    }
                }
                """;

        String expected = """
                import org.springframework.http.MediaType;
                
                import org.springframework.http.ResponseEntity;
                
                public class TestController {
                
                    public ResponseEntity respond() {
                       return ResponseEntity.status(200).eTag("My Tag").contentType(MediaType.TEXT_PLAIN).body("Hello");
                    }
                }
                """;

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }

    @Test
    void chain_2() {
        String javaSource = """
                import javax.ws.rs.core.Response;
                import javax.ws.rs.core.Response.ResponseBuilder;
                import javax.ws.rs.core.MediaType;
                
                public class TestController {
                
                    public ResponseBuilder respond() {
                       return Response.status(200).entity("Hello").tag("My Tag").type(MediaType.TEXT_PLAIN_TYPE);
                    }
                }
                """;

        String expected = """
                import org.springframework.http.MediaType;
                import org.springframework.http.ResponseEntity;
                
                public class TestController {
                
                    public ResponseEntity respond() {
                       return ResponseEntity.status(200).eTag("My Tag").contentType(MediaType.TEXT_PLAIN).body("Hello");
                    }
                }
                """;

        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                .withBuildFileHavingDependencies(
                        "javax:javaee-api:8.0",
                        "org.springframework:spring-core:"+SPRING_VERSION
                )
                .withJavaSources(javaSource)
                .build();

        action.apply(projectContext);

        String actual = projectContext.getProjectJavaSources().list().get(0).print();
        assertThat(actual)
                .as(TestDiff.of(actual, expected))
                .isEqualTo(expected);
    }
}
