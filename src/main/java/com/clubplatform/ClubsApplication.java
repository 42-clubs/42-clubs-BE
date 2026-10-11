package com.clubplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 동아리 플랫폼의 Spring Boot 애플리케이션 진입점. */
@SpringBootApplication
public class ClubsApplication {

    /** 애플리케이션 컨텍스트와 웹 서버를 시작한다. */
    public static void main(String[] args) {
        SpringApplication.run(ClubsApplication.class, args);
    }
}
