package com.havyn.common.ratelimit;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code havyn.rate-limit.*} — a list of path-prefix rules so later modules (search,
 * booking, messaging — see security/01-security-plan.md#platform) just add a rule in
 * application.yml instead of new filter code.
 */
@ConfigurationProperties(prefix = "havyn.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private List<Rule> rules = List.of();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public static class Rule {
        private String pathPrefix;
        private int limit;
        private int windowSeconds;

        public String getPathPrefix() {
            return pathPrefix;
        }

        public void setPathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
