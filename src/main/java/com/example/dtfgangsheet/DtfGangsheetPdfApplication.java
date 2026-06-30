package com.example.dtfgangsheet;

import com.example.dtfgangsheet.config.ImageProperties;
import com.example.dtfgangsheet.config.PdfProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ImageProperties.class, PdfProperties.class})
public class DtfGangsheetPdfApplication {

    public static void main(String[] args) {
        SpringApplication.run(DtfGangsheetPdfApplication.class, args);
    }
}
