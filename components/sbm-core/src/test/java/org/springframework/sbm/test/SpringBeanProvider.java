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
package org.springframework.sbm.test;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.annotation.Configurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.sbm.archfitfun.ExecutionScopeArchFitTestContext;
import org.springframework.sbm.engine.recipe.UserInteractions;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class SpringBeanProvider {

    @Configuration
    @ComponentScan(value = {"org.springframework.sbm", "org.springframework.rewrite"}, excludeFilters = @ComponentScan.Filter(classes = TestConfiguration.class))
    public static class ComponentScanConfiguration { }

    private static UserInteractions defaultUserInteractions() {
        return new UserInteractions() {
            @Override
            public boolean askUserYesOrNo(String question) {
                return false;
            }

            @Override
            public String askForInput(String question) {
                return "";
            }
        };
    }

    public static void run(ContextConsumer<AssertableApplicationContext> testcode, Class<?>... springBeans) {
        // Spring Boot 3.x defaults allow-bean-definition-overriding=false. Some SBM beans
        // (e.g. mavenSettingsInitializer) get registered twice via component-scan + @Bean
        // method; allow override at test level rather than fix every duplicate registration.
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withInitializer(applicationContext -> {
                    if (applicationContext.getBeanFactory()
                            instanceof org.springframework.beans.factory.support.DefaultListableBeanFactory dlbf) {
                        dlbf.setAllowBeanDefinitionOverriding(true);
                    }
                });
        for (Class<?> springBean : springBeans) {
            if(springBean.isAssignableFrom(Configurations.class)) {
                Configurations c = Configurations.class.cast(springBean);
                contextRunner.withConfiguration(c);
            } else {
                contextRunner = contextRunner.withBean(springBean);
            }
        }
        contextRunner.run(testcode);
    }

    public static <T> void run(ContextConsumer<AnnotationConfigApplicationContext> testcode, Map<Class<?>, Object> replacedBeans, Class<?>... springBeans) {
        AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext();
        ConfigurableListableBeanFactory beanFactory = annotationConfigApplicationContext.getBeanFactory();
        // Spring Boot 3.x defaults allow-bean-definition-overriding=false. SBM tests register
        // some beans (e.g. mavenSettingsInitializer) twice (component-scan + @Bean method);
        // allow override at test level rather than fix every duplicate registration.
        if (beanFactory instanceof org.springframework.beans.factory.support.DefaultListableBeanFactory dlbf) {
            dlbf.setAllowBeanDefinitionOverriding(true);
        }
        beanFactory.addBeanPostProcessor(new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                Class<?> beanClass = bean.getClass();
                Optional<Object> newBean = findReplacementForBean(replacedBeans, beanClass);
                if(newBean.isPresent()) {
                    return newBean.get();
                }
                return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
            }

            private Optional<Object> findReplacementForBean(Map<Class<?>, Object> replacedBeans, Class<?> beanClass) {
                return replacedBeans.keySet().stream()
                        .filter(replacedType -> beanClass.isAssignableFrom(replacedType))
                        .map(replacedType -> replacedBeans.get(replacedType))
                        .findFirst();
            }
        });

        Arrays.stream(springBeans).forEach(beanDef -> annotationConfigApplicationContext.register(beanDef));
        annotationConfigApplicationContext.registerBean(ComponentScanConfiguration.class);
        // No-op fallback so UserInteractions-requiring @Configuration recipes
        // (e.g. MigrateJaxWsRecipe) wire up in the default test context.
        // Skip if any of the explicitly-passed springBeans already provides
        // UserInteractions (e.g. RecipeIntegrationTestSupport passes
        // UserInteractionsDummy.class) so we don't end up with two beans.
        boolean hasExplicitUserInteractions = Arrays.stream(springBeans)
                .anyMatch(UserInteractions.class::isAssignableFrom);
        if (!hasExplicitUserInteractions) {
            annotationConfigApplicationContext.registerBean(
                    "userInteractions",
                    UserInteractions.class,
                    SpringBeanProvider::defaultUserInteractions);
        }
//        annotationConfigApplicationContext.scan("org.springframework.sbm", "org.springframework.freemarker");
        annotationConfigApplicationContext.refresh();
        if (new File("./src/main/resources/templates").exists()) {
            // Disambiguate by bean name: spring-boot-starter-freemarker (transitively
            // pulled in by sbm-recipes-jee-to-boot etc.) registers a bean named
            // freeMarkerConfiguration via Boot auto-config, in addition to sbm-core's
            // FreemarkerConfiguration#configuration() bean. Use the sbm-core one.
            freemarker.template.Configuration configuration = annotationConfigApplicationContext.getBean("configuration", freemarker.template.Configuration.class);
            try {
                configuration.setDirectoryForTemplateLoading(new File("./src/main/resources/templates"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            testcode.accept(annotationConfigApplicationContext);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}