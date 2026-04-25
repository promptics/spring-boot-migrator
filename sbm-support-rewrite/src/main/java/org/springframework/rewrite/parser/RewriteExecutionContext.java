package org.springframework.rewrite.parser;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.lang.Nullable;

public class RewriteExecutionContext implements ExecutionContext {
   private ExecutionContext delegate;

   public RewriteExecutionContext(Consumer<Throwable> onError) {
      this(new InMemoryExecutionContext(onError));
   }

   public RewriteExecutionContext() {
      this(new InMemoryExecutionContext(new RewriteExecutionContextErrorHandler(new RewriteExecutionContextErrorHandler.ThrowExceptionSwitch())));
   }

   public RewriteExecutionContext(ExecutionContext delegate) {
      this.delegate = delegate;
   }

   public void putMessage(String key, @Nullable Object value) {
      this.delegate.putMessage(key, value);
   }

   @Nullable
   public <T> T getMessage(String key) {
      return (T)this.delegate.getMessage(key);
   }

   public <V, C extends Collection<V>> C putMessageInCollection(String key, V value, Supplier<C> newCollection) {
      return (C)this.delegate.putMessageInCollection(key, value, newCollection);
   }

   public <T> Set<T> putMessageInSet(String key, T value) {
      return this.delegate.putMessageInSet(key, value);
   }

   @Nullable
   public <T> T pollMessage(String key) {
      return (T)this.delegate.pollMessage(key);
   }

   public <T> T pollMessage(String key, T defaultValue) {
      return (T)this.delegate.pollMessage(key, defaultValue);
   }

   public void putCurrentRecipe(Recipe recipe) {
      this.delegate.putCurrentRecipe(recipe);
   }

   public Consumer<Throwable> getOnError() {
      return this.delegate.getOnError();
   }

   public BiConsumer<Throwable, ExecutionContext> getOnTimeout() {
      return this.delegate.getOnTimeout();
   }

   // ExecutionContext#getMessages() became abstract in OR 8.x
   public Map<String, Object> getMessages() {
      return this.delegate.getMessages();
   }
}
