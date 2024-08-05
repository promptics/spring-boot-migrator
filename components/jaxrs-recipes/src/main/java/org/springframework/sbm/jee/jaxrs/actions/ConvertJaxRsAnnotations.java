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
package org.springframework.sbm.jee.jaxrs.actions;

import org.springframework.sbm.engine.recipe.AbstractAction;
import org.springframework.sbm.java.api.*;
import org.springframework.sbm.engine.context.ProjectContext;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@NoArgsConstructor
@SuperBuilder
public class ConvertJaxRsAnnotations extends AbstractAction {

    public static final Pattern JAXRS_ANNOTATION_PATTERN = Pattern.compile("javax\\.ws\\.rs\\..*");
    public static final Pattern SPRING_ANNOTATION_PATTERN = Pattern.compile("org\\.springframework\\.web\\.bind\\..*");

    @Override
    public void apply(ProjectContext context) {
        for (JavaSource js : context.getProjectJavaSources().list()) {
            for (Type t : js.getTypes()) {
                if (t.hasAnnotation("javax.ws.rs.Path")) {
                    transform(t);
                }
            }
        }
    }

    private void transform(Type type) {
        transformTypeAnnotations(type);
        transformMethodAnnotations(type);
    }

    private void transformMethodAnnotations(Type type) {
        type.getMethods().stream()
                .filter(this::isJaxRsMethod)
                .forEach(this::convertJaxRsMethodToSpringMvc);
    }

    void convertJaxRsMethodToSpringMvc(Method method) {
        Map<String, Expression> attrs = new LinkedHashMap<>();
        Set<String> methods = new LinkedHashSet<>();
        var annotations = method.getAnnotations();

        // Add @RequestBody over the first non-annotated parameter without other jax-rs annotations
        method.getParams().stream()
                .filter(p -> !p.containsAnnotation(JAXRS_ANNOTATION_PATTERN))
                .filter(p -> !p.containsAnnotation(SPRING_ANNOTATION_PATTERN))
                .findFirst()
                .ifPresent(p -> p.addAnnotation("@RequestBody", "org.springframework.web.bind.annotation.RequestBody"));

        for (Annotation a : annotations) {
            if (a == null) {
                continue;
            }
            String fullyQualifiedName = a.getFullyQualifiedName();
            if (fullyQualifiedName != null) {
                switch (fullyQualifiedName) {
                    case "javax.ws.rs.Path":
                        attrs.put("value", a.getAttribute("value"));
                        method.removeAnnotation(a);
                        break;
                    case "javax.ws.rs.Consumes":
                        attrs.put("consumes", a.getAttribute("value"));
                        method.removeAnnotation(a);
                        break;
                    case "javax.ws.rs.Produces":
                        attrs.put("produces", a.getAttribute("value"));
                        method.removeAnnotation(a);
                        break;
                    case "javax.ws.rs.POST":
                        methods.add("POST");
                        method.removeAnnotation(a);
                        break;
                    case "javax.ws.rs.GET":
                        methods.add("GET");
                        method.removeAnnotation(a);
                        break;
                    case "javax.ws.rs.PUT":
                        methods.add("PUT");
                        method.removeAnnotation(a);
                        break;
                    case "javax.ws.rs.DELETE":
                        methods.add("DELETE");
                        method.removeAnnotation(a);
                        break;
                    case "javax.ws.rs.HEAD":
                        methods.add("HEAD");
                        method.removeAnnotation(a);
                        break;
                    case "javax.ws.rs.PATCH":
                        methods.add("PATCH");
                        method.removeAnnotation(a);
                        break;
                    case "javax.ws.rs.TRACE":
                        methods.add("TRACE");
                        method.removeAnnotation(a);
                        break;
                    default:
                }
            }
        }

        if (method.getAnnotations().size() < annotations.size()) {
            StringBuilder sb = new StringBuilder("@RequestMapping");
            boolean parametersPresent = !(attrs.isEmpty() && methods.isEmpty());
            if (parametersPresent) {
                sb.append("(");
            }

            sb.append(attrs.entrySet().stream()
                              .map(e -> e.getKey() + " = " + e.getValue().print())
                              .collect(Collectors.joining(", ")));

            if (!methods.isEmpty()) {
                if(!attrs.entrySet().isEmpty()) {
                    sb.append(", ");
                }
                if (methods.size() == 1) {
                    sb.append("method = RequestMethod." + methods.iterator().next());
                } else {
                    sb.append("method = {");
                    sb.append(methods.stream().map(m -> "RequestMethod." + m).collect(Collectors.joining(", ")));
                    sb.append("}");
                }
            }
            if (parametersPresent) {
                sb.append(")");
            }

            Set<String> typeStubs = Set.of(
                    """
                    package org.springframework.web.bind.annotation;
                    import java.lang.annotation.Documented;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;
                    import org.springframework.aot.hint.annotation.Reflective;
                    import org.springframework.core.annotation.AliasFor;
                    @Target({ElementType.TYPE, ElementType.METHOD})
                    @Retention(RetentionPolicy.RUNTIME)
                    @Documented
                    @Mapping
                    @Reflective(ControllerMappingReflectiveProcessor.class)
                    public @interface RequestMapping {
                        String name() default "";
                        @AliasFor("path")
                        String[] value() default {};
                        @AliasFor("value")
                        String[] path() default {};
                        RequestMethod[] method() default {};
                        String[] params() default {};
                        String[] headers() default {};
                        String[] consumes() default {};
                        String[] produces() default {};
                                        
                    }
                    """,
                    """
                    package org.springframework.web.bind.annotation;
                                        
                    import org.springframework.http.HttpMethod;
                    import org.springframework.lang.Nullable;
                    import org.springframework.util.Assert;
                    public enum RequestMethod {
                                        
                        GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE;
                                        
                        @Nullable
                        public static RequestMethod resolve(String method) {
                            Assert.notNull(method, "Method must not be null");
                            return switch (method) {
                                case "GET" -> GET;
                                case "HEAD" -> HEAD;
                                case "POST" -> POST;
                                case "PUT" -> PUT;
                                case "PATCH" -> PATCH;
                                case "DELETE" -> DELETE;
                                case "OPTIONS" -> OPTIONS;
                                case "TRACE" -> TRACE;
                                default -> null;
                            };
                        }
                        @Nullable
                        public static RequestMethod resolve(HttpMethod httpMethod) {
                            Assert.notNull(httpMethod, "HttpMethod must not be null");
                            return resolve(httpMethod.name());
                        }
                        public HttpMethod asHttpMethod() {
                            return switch (this) {
                                case GET -> HttpMethod.GET;
                                case HEAD -> HttpMethod.HEAD;
                                case POST -> HttpMethod.POST;
                                case PUT -> HttpMethod.PUT;
                                case PATCH -> HttpMethod.PATCH;
                                case DELETE -> HttpMethod.DELETE;
                                case OPTIONS -> HttpMethod.OPTIONS;
                                case TRACE -> HttpMethod.TRACE;
                            };
                        }
                    }
                    """
            );

            method.addAnnotation(sb.toString(), "org.springframework.web.bind.annotation.RequestMapping", typeStubs, "org.springframework.web.bind.annotation.RequestMethod");

        }
    }

    private boolean isJaxRsMethod(Method method) {
        return method.containsAnnotation(JAXRS_ANNOTATION_PATTERN);
    }

    private void transformTypeAnnotations(Type type) {
        List<Annotation> annotations = type.getAnnotations();
        Optional<Annotation> found = annotations.stream().filter(a -> "javax.ws.rs.Path".equals(a.getFullyQualifiedName())).findFirst();
        if (found.isPresent()) {
            type.removeAnnotation(found.get());
            String fqName = "org.springframework.web.bind.annotation.RestController";
            String snippet = "@" + fqName.substring(fqName.lastIndexOf('.') + 1);
            Set<String> stubs = Set.of("""
                    package org.springframework.web.bind.annotation;
                                        
                    import java.lang.annotation.Documented;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;
                                        
                    import org.springframework.core.annotation.AliasFor;
                    import org.springframework.stereotype.Controller;
                    @Target(ElementType.TYPE)
                    @Retention(RetentionPolicy.RUNTIME)
                    @Documented
                    @Controller
                    @ResponseBody
                    public @interface RestController {
                    	@AliasFor(annotation = Controller.class)
                    	String value() default "";
                    }
                    """);
            type.addAnnotation(snippet, "org.springframework.web.bind.annotation.RestController", stubs);
            Map<String, Expression> attributes = new LinkedHashMap<>(found.get().getAttributes());
            for (Annotation a : annotations) {
                if (a == null) {
                    continue;
                }
                String fullyQualifiedName = a.getFullyQualifiedName();
                if (fullyQualifiedName != null) {
                    switch (fullyQualifiedName) {
                        case "javax.ws.rs.Consumes" -> {
                            attributes.put("consumes", a.getAttribute("value"));
                            type.removeAnnotation(a);
                        }
                        case "javax.ws.rs.Produces" -> {
                            attributes.put("produces", a.getAttribute("value"));
                            type.removeAnnotation(a);
                        }
                        default -> {
                        }
                    }
                }
            }
            String rmAttrs = attributes.entrySet().stream().map(e -> e.getKey() + " = " + e.getValue().print()).collect(Collectors.joining(", "));
            type.addAnnotation("@RequestMapping(" + rmAttrs + ")", "org.springframework.web.bind.annotation.RequestMapping", Set.of("""
                    /*
                     * Copyright 2002-2024 the original author or authors.
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
                                        
                    package org.springframework.web.bind.annotation;
                                        
                    import java.lang.annotation.Documented;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;
                                        
                    import org.springframework.aot.hint.annotation.Reflective;
                    import org.springframework.core.annotation.AliasFor;
                                        
                    /**
                     * Annotation for mapping web requests onto methods in request-handling classes
                     * with flexible method signatures.
                     *
                     * <p>Both Spring MVC and Spring WebFlux support this annotation through a
                     * {@code RequestMappingHandlerMapping} and {@code RequestMappingHandlerAdapter}
                     * in their respective modules and package structures. For the exact list of
                     * supported handler method arguments and return types in each, please use the
                     * reference documentation links below:
                     * <ul>
                     * <li>Spring MVC
                     * <a href="https://docs.spring.io/spring/docs/current/spring-framework-reference/web.html#mvc-ann-arguments">Method Arguments</a>
                     * and
                     * <a href="https://docs.spring.io/spring/docs/current/spring-framework-reference/web.html#mvc-ann-return-types">Return Values</a>
                     * </li>
                     * <li>Spring WebFlux
                     * <a href="https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html#webflux-ann-arguments">Method Arguments</a>
                     * and
                     * <a href="https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html#webflux-ann-return-types">Return Values</a>
                     * </li>
                     * </ul>
                     *
                     * <p><strong>NOTE:</strong> This annotation can be used both at the class and
                     * at the method level. In most cases, at the method level applications will
                     * prefer to use one of the HTTP method specific variants
                     * {@link GetMapping @GetMapping}, {@link PostMapping @PostMapping},
                     * {@link PutMapping @PutMapping}, {@link DeleteMapping @DeleteMapping}, or
                     * {@link PatchMapping @PatchMapping}.
                     *
                     * <p><strong>NOTE:</strong> This annotation cannot be used in conjunction with
                     * other {@code @RequestMapping} annotations that are declared on the same element
                     * (class, interface, or method). If multiple {@code @RequestMapping} annotations
                     * are detected on the same element, a warning will be logged, and only the first
                     * mapping will be used. This also applies to composed {@code @RequestMapping}
                     * annotations such as {@code @GetMapping}, {@code @PostMapping}, etc.
                     *
                     * <p><b>NOTE:</b> When using controller interfaces (e.g. for AOP proxying),
                     * make sure to consistently put <i>all</i> your mapping annotations &mdash; such
                     * as {@code @RequestMapping} and {@code @SessionAttributes} &mdash; on
                     * the controller <i>interface</i> rather than on the implementation class.
                     *
                     * @author Juergen Hoeller
                     * @author Arjen Poutsma
                     * @author Sam Brannen
                     * @since 2.5
                     * @see GetMapping
                     * @see PostMapping
                     * @see PutMapping
                     * @see DeleteMapping
                     * @see PatchMapping
                     */
                    @Target({ElementType.TYPE, ElementType.METHOD})
                    @Retention(RetentionPolicy.RUNTIME)
                    @Documented
                    @Mapping
                    @Reflective(ControllerMappingReflectiveProcessor.class)
                    public @interface RequestMapping {
                                        
                    	/**
                    	 * Assign a name to this mapping.
                    	 * <p><b>Supported at the type level as well as at the method level!</b>
                    	 * When used on both levels, a combined name is derived by concatenation
                    	 * with "#" as separator.
                    	 * @see org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder
                    	 * @see org.springframework.web.servlet.handler.HandlerMethodMappingNamingStrategy
                    	 */
                    	String name() default "";
                                        
                    	/**
                    	 * The path mapping URIs &mdash; for example, {@code "/profile"}.
                    	 * <p>This is an alias for {@link #path}. For example,
                    	 * {@code @RequestMapping("/profile")} is equivalent to
                    	 * {@code @RequestMapping(path="/profile")}.
                    	 * <p>See {@link #path} for further details.
                    	 */
                    	@AliasFor("path")
                    	String[] value() default {};
                                        
                    	/**
                    	 * The path mapping URIs &mdash; for example, {@code "/profile"}.
                    	 * <p>Ant-style path patterns are also supported (e.g. {@code "/profile/**"}).
                    	 * At the method level, relative paths (e.g. {@code "edit"}) are supported
                    	 * within the primary mapping expressed at the type level.
                    	 * Path mapping URIs may contain placeholders (e.g. <code>"/${profile_path}"</code>).
                    	 * <p><b>Supported at the type level as well as at the method level!</b>
                    	 * When used at the type level, all method-level mappings inherit
                    	 * this primary mapping, narrowing it for a specific handler method.
                    	 * <p><strong>NOTE</strong>: A handler method that is not mapped to any path
                    	 * explicitly is effectively mapped to an empty path.
                    	 * @since 4.2
                    	 */
                    	@AliasFor("value")
                    	String[] path() default {};
                                        
                    	/**
                    	 * The HTTP request methods to map to, narrowing the primary mapping:
                    	 * GET, POST, HEAD, OPTIONS, PUT, PATCH, DELETE, TRACE.
                    	 * <p><b>Supported at the type level as well as at the method level!</b>
                    	 * When used at the type level, all method-level mappings inherit this
                    	 * HTTP method restriction.
                    	 */
                    	RequestMethod[] method() default {};
                                        
                    	/**
                    	 * The parameters of the mapped request, narrowing the primary mapping.
                    	 * <p>Same format for any environment: a sequence of "myParam=myValue" style
                    	 * expressions, with a request only mapped if each such parameter is found
                    	 * to have the given value. Expressions can be negated by using the "!=" operator,
                    	 * as in "myParam!=myValue". "myParam" style expressions are also supported,
                    	 * with such parameters having to be present in the request (allowed to have
                    	 * any value). Finally, "!myParam" style expressions indicate that the
                    	 * specified parameter is <i>not</i> supposed to be present in the request.
                    	 * <p><b>Supported at the type level as well as at the method level!</b>
                    	 * When used at the type level, all method-level mappings inherit this
                    	 * parameter restriction.
                    	 */
                    	String[] params() default {};
                                        
                    	/**
                    	 * The headers of the mapped request, narrowing the primary mapping.
                    	 * <p>Same format for any environment: a sequence of "My-Header=myValue" style
                    	 * expressions, with a request only mapped if each such header is found
                    	 * to have the given value. Expressions can be negated by using the "!=" operator,
                    	 * as in "My-Header!=myValue". "My-Header" style expressions are also supported,
                    	 * with such headers having to be present in the request (allowed to have
                    	 * any value). Finally, "!My-Header" style expressions indicate that the
                    	 * specified header is <i>not</i> supposed to be present in the request.
                    	 * <p>Also supports media type wildcards (*), for headers such as Accept
                    	 * and Content-Type. For instance,
                    	 * <pre class="code">
                    	 * &#064;RequestMapping(value = "/something", headers = "content-type=text/*")
                    	 * </pre>
                    	 * will match requests with a Content-Type of "text/html", "text/plain", etc.
                    	 * <p><b>Supported at the type level as well as at the method level!</b>
                    	 * When used at the type level, all method-level mappings inherit this
                    	 * header restriction.
                    	 * @see org.springframework.http.MediaType
                    	 */
                    	String[] headers() default {};
                                        
                    	/**
                    	 * Narrows the primary mapping by media types that can be consumed by the
                    	 * mapped handler. Consists of one or more media types one of which must
                    	 * match to the request {@code Content-Type} header. Examples:
                    	 * <pre class="code">
                    	 * consumes = "text/plain"
                    	 * consumes = {"text/plain", "application/*"}
                    	 * consumes = MediaType.TEXT_PLAIN_VALUE
                    	 * </pre>
                    	 * <p>If a declared media type contains a parameter, and if the
                    	 * {@code "content-type"} from the request also has that parameter, then
                    	 * the parameter values must match. Otherwise, if the media type from the
                    	 * request {@code "content-type"} does not contain the parameter, then the
                    	 * parameter is ignored for matching purposes.
                    	 * <p>Expressions can be negated by using the "!" operator, as in
                    	 * "!text/plain", which matches all requests with a {@code Content-Type}
                    	 * other than "text/plain".
                    	 * <p><b>Supported at the type level as well as at the method level!</b>
                    	 * If specified at both levels, the method level consumes condition overrides
                    	 * the type level condition.
                    	 * @see org.springframework.http.MediaType
                    	 * @see jakarta.servlet.http.HttpServletRequest#getContentType()
                    	 */
                    	String[] consumes() default {};
                                        
                    	/**
                    	 * Narrows the primary mapping by media types that can be produced by the
                    	 * mapped handler. Consists of one or more media types one of which must
                    	 * be chosen via content negotiation against the "acceptable" media types
                    	 * of the request. Typically those are extracted from the {@code "Accept"}
                    	 * header but may be derived from query parameters, or other. Examples:
                    	 * <pre class="code">
                    	 * produces = "text/plain"
                    	 * produces = {"text/plain", "application/*"}
                    	 * produces = MediaType.TEXT_PLAIN_VALUE
                    	 * produces = "text/plain;charset=UTF-8"
                    	 * </pre>
                    	 * <p>If a declared media type contains a parameter (e.g. "charset=UTF-8",
                    	 * "type=feed", "type=entry") and if a compatible media type from the request
                    	 * has that parameter too, then the parameter values must match. Otherwise,
                    	 * if the media type from the request does not contain the parameter, it is
                    	 * assumed the client accepts any value.
                    	 * <p>Expressions can be negated by using the "!" operator, as in "!text/plain",
                    	 * which matches all requests with a {@code Accept} other than "text/plain".
                    	 * <p><b>Supported at the type level as well as at the method level!</b>
                    	 * If specified at both levels, the method level produces condition overrides
                    	 * the type level condition.
                    	 * @see org.springframework.http.MediaType
                    	 */
                    	String[] produces() default {};
                                        
                    }
                    """));
        }
    }
}
