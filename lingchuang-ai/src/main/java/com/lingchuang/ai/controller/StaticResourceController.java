package com.lingchuang.ai.controller;

import com.lingchuang.ai.constant.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;
import java.nio.file.Path;

/**
 * 静态资源访问
 */
@RestController
public class StaticResourceController {

    // 应用生成根目录（用于浏览）
    private static final String PREVIEW_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    // 应用部署根目录（用于部署后访问和截图）
    private static final String DEPLOY_ROOT_DIR = AppConstant.CODE_DEPLOY_ROOT_DIR;

    /**
     * 提供静态资源访问，支持目录重定向
     * 访问格式：http://localhost:8123/api/static/{deployKey}[/{fileName}]
     */
    @GetMapping("/static/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        return serveResource(PREVIEW_ROOT_DIR, "/static/" + deployKey, deployKey, request);
    }

    /**
     * 提供部署后静态资源访问，支持截图服务直接访问部署产物
     * 访问格式：http://localhost:8123/api/deploy/{deployKey}[/{fileName}]
     */
    @GetMapping("/deploy/{deployKey}/**")
    public ResponseEntity<Resource> serveDeployResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        return serveResource(DEPLOY_ROOT_DIR, "/deploy/" + deployKey, deployKey, request);
    }

    private ResponseEntity<Resource> serveResource(
            String rootDir,
            String requestPathPrefix,
            String resourceKey,
            HttpServletRequest request) {
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            if (resourcePath == null || !resourcePath.startsWith(requestPathPrefix)) {
                return ResponseEntity.notFound().build();
            }
            resourcePath = resourcePath.substring(requestPathPrefix.length());
            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            // 默认返回 index.html
            // 构建文件路径
            Path rootPath = Path.of(rootDir).toAbsolutePath().normalize();
            Path resourceRootPath = rootPath.resolve(resourceKey).normalize();
            Path targetPath = resourceRootPath.resolve(resourcePath.substring(1)).normalize();
            if (!targetPath.startsWith(resourceRootPath)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            if (targetPath.toFile().isDirectory()) {
                targetPath = targetPath.resolve("index.html").normalize();
                if (!targetPath.startsWith(resourceRootPath)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            }
            File file = targetPath.toFile();
            // 检查文件是否存在
            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }
            // 返回文件资源
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(targetPath.toString()))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
