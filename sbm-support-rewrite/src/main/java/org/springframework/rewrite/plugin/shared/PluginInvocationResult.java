package org.springframework.rewrite.plugin.shared;

public record PluginInvocationResult(boolean success, String capturedOutput) {
}
