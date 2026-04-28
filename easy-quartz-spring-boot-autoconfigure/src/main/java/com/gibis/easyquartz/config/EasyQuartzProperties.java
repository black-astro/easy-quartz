package com.gibis.easyquartz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;


@ConfigurationProperties(prefix = "easy.quartz")
public class EasyQuartzProperties {
    /**
     * EasyQuartz 활성화 여부
     */
    private boolean enabled = true;

    /**
     * 기존 스케줄 업데이트 여부 (재시작 시)
     */
    private boolean updateExisting = true;

    /**
     * 기본 타임존
     */
    private String defaultTimeZone = "Asia/Seoul";

    /**
     * 스캔할 패키지 목록 (비우면 전체 스캔)
     */
    private List<String> scanPackages = new ArrayList<>();

    // ============== Getters & Setters ==============
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isUpdateExisting() {
        return updateExisting;
    }

    public void setUpdateExisting(boolean updateExisting) {
        this.updateExisting = updateExisting;
    }

    public String getDefaultTimeZone() {
        return defaultTimeZone;
    }

    public void setDefaultTimeZone(String defaultTimeZone) {
        this.defaultTimeZone = defaultTimeZone;
    }

    public List<String> getScanPackages() {
        return scanPackages;
    }

    public void setScanPackages(List<String> scanPackages) {
        this.scanPackages = scanPackages;
    }
}
