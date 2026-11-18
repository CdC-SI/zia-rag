package zas.admin.zia.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import zas.admin.zia.rag.config.properties.RerankProperties;
import zas.admin.zia.rag.config.properties.RetrievalProperties;
import zas.admin.zia.rag.observability.CorrelationIdFilter;
import zas.admin.zia.rag.rerank.RemoteReranker;
import zas.admin.zia.rag.rerank.Reranker;

@Configuration
@EnableConfigurationProperties({RerankProperties.class, RetrievalProperties.class})
public class RetrievalConfig {

    @Bean
    public RestClient rerankRestClient(RestClient.Builder builder, RerankProperties properties) {
        RestClient.Builder rerankBuilder = builder.clone()
                .requestInterceptor(CorrelationIdFilter.restClientInterceptor())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(HttpClientSettings.defaults()
                        .withTimeouts(properties.connectTimeout(), properties.readTimeout())));
        if (StringUtils.hasText(properties.baseUrl())) {
            rerankBuilder.baseUrl(properties.baseUrl());
        }
        if (StringUtils.hasText(properties.apiKey())) {
            rerankBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey());
        }
        return rerankBuilder.build();
    }

    @Bean
    public Reranker reranker(RestClient rerankRestClient, RerankProperties properties) {
        return new RemoteReranker(rerankRestClient, properties);
    }
}
