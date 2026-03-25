package org.example.analyzerback;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
		"org.example.analyzerback",
		"com.app"
})
@MapperScan("com.app")
public class AnalyzerBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(AnalyzerBackApplication.class, args);
	}

}
