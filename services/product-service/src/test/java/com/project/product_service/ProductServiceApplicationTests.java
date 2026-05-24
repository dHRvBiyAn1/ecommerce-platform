package com.project.product_service;

import com.project.product_service.search.ProductSearchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class ProductServiceApplicationTests {

	@MockBean
	private ProductSearchRepository productSearchRepository;

	@Test
	void contextLoads() {
	}

}
