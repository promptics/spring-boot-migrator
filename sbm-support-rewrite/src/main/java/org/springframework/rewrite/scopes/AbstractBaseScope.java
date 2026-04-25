package org.springframework.rewrite.scopes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.lang.Nullable;
import org.springframework.rewrite.parser.RewriteExecutionContextErrorHandler;

public class AbstractBaseScope implements Scope {
   private static final Logger LOGGER = LoggerFactory.getLogger(RewriteExecutionContextErrorHandler.class);
   private final Map<String, Object> scopedBeans = new ConcurrentHashMap<>();

   public void clear(ConfigurableListableBeanFactory beanFactory) {
      LOGGER.trace("Clearing %d beans from scope %s.".formatted(this.scopedBeans.keySet().size(), this.getClass().getName()));
      this.scopedBeans.keySet().stream().forEach(beanName -> {
         beanFactory.destroyScopedBean(beanName);
         LOGGER.trace("Removed bean '%s' from scan scope.".formatted(beanName));
      });
   }

   public Object get(String name, ObjectFactory<?> objectFactory) {
      Object scopedObject = this.scopedBeans.get(name);
      if (scopedObject == null) {
         scopedObject = objectFactory.getObject();
         this.scopedBeans.put(name, scopedObject);
      }

      return scopedObject;
   }

   @Nullable
   public Object remove(String name) {
      Map<String, Object> scope = this.scopedBeans;
      return scope.remove(name);
   }

   public void registerDestructionCallback(String name, Runnable callback) {
      LOGGER.warn("%s does not support destruction callbacks.".formatted(this.getClass().getName()));
   }

   @Nullable
   public Object resolveContextualObject(String key) {
      return null;
   }

   public String getConversationId() {
      return null;
   }
}
