package zas.admin.zia.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ZiaRagApplication {
    private ZiaRagApplication() {
        /* This utility class should not be instantiated */
    }

    static void main(String[] args) {
        SpringApplication.run(ZiaRagApplication.class, args);
    }
}
