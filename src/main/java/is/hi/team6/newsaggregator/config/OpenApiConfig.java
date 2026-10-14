package is.hi.team6.newsaggregator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI newsAggregatorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Team 6 News Aggregator API")
                        .description(
                                "REST API for the HBV501G Team 6 News Aggregator project")
                        .version("Sprint 2"));
    }
}