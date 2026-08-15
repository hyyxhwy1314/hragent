package org.example.hragent;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@MapperScan("org.example.hragent.mapper")
class HrAgentApplicationTests {

    @Test
    void contextLoads() {
    }

}
