package com.tanya.workrequests.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanya.workrequests.api.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Rejects oversized request bodies with a 413 before anything parses them. A declared
 * Content-Length over the limit is refused without reading; a body of unknown length
 * (chunked) is read up to the limit and refused as soon as it goes past it.
 */
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestBodyLimitFilter.class);
    private static final Set<String> METHODS_WITHOUT_BODY = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final long maxBytes;
    private final String limitLabel;
    private final ObjectMapper objectMapper;

    public RequestBodyLimitFilter(DataSize maxBodySize, ObjectMapper objectMapper) {
        this.maxBytes = maxBodySize.toBytes();
        this.limitLabel = humanReadable(maxBodySize);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (METHODS_WITHOUT_BODY.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        long declared = request.getContentLengthLong();
        if (declared > maxBytes) {
            reject(response, declared + " bytes declared");
            return;
        }
        if (declared >= 0) {
            // The container never reads past a declared Content-Length, so this body fits.
            chain.doFilter(request, response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes((int) maxBytes + 1);
        if (body.length > maxBytes) {
            reject(response, "chunked body over the limit");
            return;
        }
        chain.doFilter(new BufferedBodyRequest(request, body), response);
    }

    private void reject(HttpServletResponse response, String detail) throws IOException {
        log.warn("Rejected request body: {} (limit {})", detail, limitLabel);
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Closing the connection stops the client from streaming the rest of a body we won't read.
        response.setHeader("Connection", "close");
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(HttpStatus.PAYLOAD_TOO_LARGE, "Request body is larger than the " + limitLabel + " limit"));
    }

    static String humanReadable(DataSize size) {
        long bytes = size.toBytes();
        if (bytes % DataSize.ofMegabytes(1).toBytes() == 0) {
            return size.toMegabytes() + " MB";
        }
        if (bytes % DataSize.ofKilobytes(1).toBytes() == 0) {
            return size.toKilobytes() + " KB";
        }
        return bytes + " bytes";
    }

    /** The request with its already-read body, so the rest of the chain can read it again. */
    private static final class BufferedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return source.read(buffer, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("synchronous reads only");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
