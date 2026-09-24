package com.banking.core_banking_system;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(info = @Info(
	title = "Core Banking System API",
	version = "0.0.1",
	description = "Ledger ngân hàng lõi theo bút toán kép. Thứ tự thử: tạo 2 tài khoản → nạp tiền → rút tiền → xem số dư."
))
public class CoreBankingSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(CoreBankingSystemApplication.class, args);
	}

}
