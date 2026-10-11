package com.clubplatform.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
/** 한국 시간 기준의 현재 시각을 제공하는 Clock을 등록한다. */
public class TimeConfig {

    /** 조회 날짜 계산에 사용할 한국 시간대의 시스템 Clock을 제공한다. */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
