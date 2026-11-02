package com.tanya.workrequests.api;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.tanya.workrequests.config.RequestBodyProperties;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** The chunked path (no Content-Length), which MockMvc can't produce because it always sets one. */
class RequestBodyLimitFilterTest {

    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(DataSize.ofBytes(10), JsonMapper.builder().build());

    @Test
    void rejectsAChunkedBodyAsSoonAsItPassesTheLimit() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(chunked("[1,2,3,4,5,6]"), response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"message\":\"Request body is larger than the 10 bytes limit\"");
        assertThat(response.getHeader("Connection")).isEqualTo("close");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void passesAChunkedBodyWithinTheLimitOnUnchanged() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, jakarta.servlet.ServletResponse response) throws java.io.IOException {
                seen.set(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                        + " length=" + request.getContentLength());
            }
        };

        filter.doFilter(chunked("[1,2]"), new MockHttpServletResponse(), chain);

        assertThat(seen.get()).isEqualTo("[1,2] length=5");
    }

    @Test
    void describesLimitsInTheirNaturalUnit() {
        assertThat(RequestBodyLimitFilter.humanReadable(DataSize.ofMegabytes(2))).isEqualTo("2 MB");
        assertThat(RequestBodyLimitFilter.humanReadable(DataSize.ofKilobytes(512))).isEqualTo("512 KB");
        assertThat(RequestBodyLimitFilter.humanReadable(DataSize.ofBytes(1500))).isEqualTo("1500 bytes");
    }

    @Test
    void refusesALimitOfZeroOrAboveTheCeiling() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RequestBodyProperties(DataSize.ofBytes(0)));
        assertThatIllegalArgumentException().isThrownBy(() -> new RequestBodyProperties(DataSize.ofMegabytes(101)));
    }

    private static MockHttpServletRequest chunked(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/work-requests/batch") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader("Transfer-Encoding", "chunked");
        return request;
    }
}
