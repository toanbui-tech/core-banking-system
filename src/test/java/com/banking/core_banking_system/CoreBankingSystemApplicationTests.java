package com.banking.core_banking_system;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "outbox.publisher.enabled=false")
class CoreBankingSystemApplicationTests {

	@Test
	void contextLoads() {
	}

}
