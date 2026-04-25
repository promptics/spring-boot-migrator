package org.springframework.rewrite.parser.events;

import java.util.List;
import org.springframework.core.io.Resource;

public record StartedParsingProjectEvent(List<Resource> resources) {
}
