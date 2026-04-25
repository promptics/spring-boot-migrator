package org.springframework.rewrite.resource.finder;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.rewrite.resource.ProjectResourceSet;

public class GenericTypeFinder<T> implements ProjectResourceFinder<Optional<T>> {
   private final Class<T> type;

   public GenericTypeFinder(Class<T> type) {
      this.type = type;
   }

   public Class<T> getType() {
      return this.type;
   }

   public Optional<T> apply(ProjectResourceSet projectResourceSet) {
      List<T> collect = projectResourceSet.stream().filter(pr -> this.type.isAssignableFrom(pr.getClass())).map(this.type::cast).collect(Collectors.toList());
      if (collect.size() > 1) {
         throw new ResourceFilterException(
            String.format("Found more than one resource of type '%s'. Use %s instead.", this.type.getClass(), GenericTypeListFinder.class)
         );
      } else {
         return collect.isEmpty() ? Optional.empty() : Optional.of(collect.get(0));
      }
   }
}
