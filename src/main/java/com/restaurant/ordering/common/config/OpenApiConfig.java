package com.restaurant.ordering.common.config;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.media.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;
import java.util.Map;

@Configuration
public class OpenApiConfig {
    @Bean OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("Restaurant QR Ordering API").version("1.0")
            .description("KGS restaurant ordering. Public QR ordering; staff JWT for KDS and management. Unknown JSON fields are rejected. WebSocket contracts: docs/WEBSOCKET_EVENTS.md."))
            .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
    @Bean OpenApiCustomizer commonErrors() {
        return api -> api.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
            Map.of("400","Invalid request", "401","Authentication required", "403","Insufficient permissions or invalid QR",
                "404","Resource not found", "409","Business or concurrent update conflict", "429","Rate limited", "500","Internal error")
                .forEach((code, detail) -> operation.getResponses().addApiResponse(code,
                    new ApiResponse().description(detail).content(problemContent())))));
    }
    private Content problemContent() {
        var schema = new ObjectSchema().addProperty("status", new IntegerSchema()).addProperty("code", new StringSchema())
            .addProperty("detail", new StringSchema()).addProperty("timestamp", new StringSchema().format("date-time"))
            .addProperty("path", new StringSchema()).addProperty("correlationId", new StringSchema());
        return new Content().addMediaType("application/problem+json", new MediaType().schema(schema));
    }
}
