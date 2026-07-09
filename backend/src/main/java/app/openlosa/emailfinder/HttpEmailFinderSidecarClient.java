package app.openlosa.emailfinder;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import app.openlosa.common.api.BadRequestException;
import app.openlosa.common.api.UpstreamServiceException;

@Component
class HttpEmailFinderSidecarClient implements EmailFinderSidecarClient {

    private static final Logger logger = LoggerFactory.getLogger(HttpEmailFinderSidecarClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    HttpEmailFinderSidecarClient(
        @Value("${openlosa.email-finder.url}") String baseUrl,
        @Value("${openlosa.email-finder.read-timeout:125s}") Duration readTimeout,
        ObjectMapper objectMapper
    ) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public EmailFinderSidecarResponse find(EmailFinderSidecarRequest request) {
        try {
            var response = restClient.post()
                .uri("/find")
                .body(request)
                .retrieve()
                .body(EmailFinderSidecarResponse.class);
            if (response == null) {
                throw new UpstreamServiceException("Email Finder sidecar returned an empty response");
            }
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.BAD_REQUEST || exception.getStatusCode().value() == 422) {
                throw new BadRequestException(sidecarClientMessage(exception));
            }
            logger.warn("Email Finder sidecar returned {}: {}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new UpstreamServiceException(sidecarMessage(exception));
        } catch (ResourceAccessException exception) {
            throw new UpstreamServiceException("Email Finder sidecar is unavailable or timed out");
        } catch (RestClientException exception) {
            throw new UpstreamServiceException("Email Finder sidecar returned an invalid response");
        }
    }

    private String sidecarClientMessage(RestClientResponseException exception) {
        var message = sidecarJsonMessage(exception.getResponseBodyAsString());
        return message == null ? "Email Finder sidecar rejected the lookup" : message;
    }

    private String sidecarMessage(RestClientResponseException exception) {
        return "Email Finder sidecar failed";
    }

    private String sidecarJsonMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode json = objectMapper.readTree(body);
            var error = json.path("error").asText(null);
            var detail = json.path("detail").asText(null);
            if (error != null && detail != null) {
                return error + ": " + detail;
            }
            if (error != null) {
                return error;
            }
            return detail;
        } catch (Exception exception) {
            return null;
        }
    }
}
