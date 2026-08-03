package com.havyn.admin.web;

import com.havyn.pricing.domain.PlatformSetting;

public record PlatformSettingSummary(String key, String value) {

    public static PlatformSettingSummary from(PlatformSetting setting) {
        return new PlatformSettingSummary(setting.getKey(), setting.getValue());
    }
}
