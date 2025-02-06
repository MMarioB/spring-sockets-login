package com.discobingohits.login_sockets_bingo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class LoginSocketsBingoApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoginSocketsBingoApplication.class, args);
	}

}
