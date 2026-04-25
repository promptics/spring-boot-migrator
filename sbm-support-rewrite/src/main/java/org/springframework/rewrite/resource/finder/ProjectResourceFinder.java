package org.springframework.rewrite.resource.finder;

import org.springframework.rewrite.resource.ProjectResourceSet;

public interface ProjectResourceFinder<T> {
   T apply(ProjectResourceSet projectResourceSet);
}
