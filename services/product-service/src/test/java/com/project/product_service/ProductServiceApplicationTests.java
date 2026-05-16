package com.project.product_service;

import com.project.product_service.search.ProductSearchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ProductServiceApplicationTests {

	@MockitoBean
	private ProductSearchRepository productSearchRepository;

	@Test
	void contextLoads() {
	}

}
