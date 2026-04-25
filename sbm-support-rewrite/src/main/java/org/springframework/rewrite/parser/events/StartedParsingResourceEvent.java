package org.springframework.rewrite.parser.events;

import org.openrewrite.Parser.Input;

public record StartedParsingResourceEvent(Input input) {
}
