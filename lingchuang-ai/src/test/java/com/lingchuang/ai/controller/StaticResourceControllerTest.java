package com.lingchuang.ai.controller;

import com.lingchuang.ai.constant.AppConstant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticResourceControllerTest {

    private final StaticResourceController controller = new StaticResourceController();

    @AfterEach
    void cleanUp() throws Exception {
        Path testDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, "static-controller-test");
        if (Files.exists(testDir)) {
            try (var paths = Files.walk(testDir)) {
                paths.sorted((left, right) -> right.compareTo(left))
                        .forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test
    void shouldServeIndexWhenStaticDirectoryRequested() throws Exception {
        Path htmlDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, "static-controller-test", "1", "html");
        Files.createDirectories(htmlDir);
        Files.writeString(htmlDir.resolve("index.html"), "<html><body>小范</body></html>");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/static/static-controller-test/1/html/");
        request.setAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
                "/static/static-controller-test/1/html/"
        );

        var response = controller.serveStaticResource("static-controller-test", request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("text/html; charset=UTF-8", response.getHeaders().getFirst("Content-Type"));
        assertNotNull(response.getBody());
    }

    @Test
    void shouldRejectPathTraversal() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/static/static-controller-test/../application.yml");
        request.setAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
                "/static/static-controller-test/../application.yml"
        );

        var response = controller.serveStaticResource("static-controller-test", request);

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void shouldRedirectBareResourceKeyToSlash() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/static/static-controller-test");
        request.setAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
                "/static/static-controller-test"
        );

        var response = controller.serveStaticResource("static-controller-test", request);

        assertEquals(301, response.getStatusCode().value());
        assertTrue(response.getHeaders().getFirst("Location").endsWith("/"));
    }
}
