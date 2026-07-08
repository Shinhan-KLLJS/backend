package com.shinhan.klljs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class KlljsApplication {

	public static void main(String[] args) {
		// DB/애플리케이션 내부 시각은 전부 UTC로 통일한다 (KST 변환은 조회 시 경계값 계산에서만 수행).
		// bare LocalDateTime.now() 등 JVM 기본 타임존에 의존하는 코드를 위한 안전장치.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(KlljsApplication.class, args);
	}

}
