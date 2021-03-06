package com.lhiot.oc.delivery.model;

import lombok.Getter;

/**
 * 应用类型
 */
public enum ApplicationType {
    APP("视食"),
    WECHAT_MALL("微商城"),
    S_MALL("小程序"),
    FRUIT_DOCTOR("小程序"),
    WXSMALL_SHOP("微商城小程序");
    @Getter
    private String description;

    ApplicationType(String description) {
        this.description = description;
    }
}
