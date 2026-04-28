package com.gibis.easyquartz.enums;

public enum FixedMode {
    CATCH_UP,        // 늦으면 바로 연달아 실행(스케줄 기준)
    FIXED_DELAY      // 실행 끝난 후 delay만큼 쉬고 다음 실행
}
