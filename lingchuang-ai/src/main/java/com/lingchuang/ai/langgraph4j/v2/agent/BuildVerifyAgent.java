package com.lingchuang.ai.langgraph4j.v2.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.lingchuang.ai.core.builder.VueProjectBuilder;
import com.lingchuang.ai.langgraph4j.v2.model.AgentExecutionRecord;
import com.lingchuang.ai.langgraph4j.v2.model.TaskSpec;
import com.lingchuang.ai.langgraph4j.v2.model.VerificationArtifact;
import com.lingchuang.ai.langgraph4j.v2.model.WorkflowStage;
import com.lingchuang.ai.langgraph4j.v2.service.GeneratedArtifactSupport;
import com.lingchuang.ai.langgraph4j.v2.state.AgentSessionState;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 构建与验证 Agent。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BuildVerifyAgent {

    private static final String AGENT_NAME = "BuildVerifyAgent";
    private static final Pattern STATIC_REF_PATTERN = Pattern.compile("(?:src|href)\\s*=\\s*[\"']([^\"'#?]+(?:\\?[^\"']*)?(?:#[^\"']*)?)[\"']",
            Pattern.CASE_INSENSITIVE);

    private final VueProjectBuilder vueProjectBuilder;
    private final GeneratedArtifactSupport generatedArtifactSupport;

    public Map<String, Object> execute(MessagesState<String> state) {
        AgentSessionState sessionState = AgentSessionState.getState(state);
        AgentExecutionRecord executionRecord = sessionState.beginAgentExecution(
                AGENT_NAME,
                WorkflowStage.VERIFYING,
                "targetType=%s".formatted(sessionState.getTaskSpec() == null ? "unknown" : sessionState.getTaskSpec().getTargetCodeGenType()),
                "local-verifier"
        );
        VerificationArtifact verificationArtifact = verify(sessionState);
        sessionState.setVerificationArtifact(verificationArtifact);
        sessionState.finishAgentExecution(
                executionRecord,
                verificationArtifact.isPassed() ? "SUCCESS" : (verificationArtifact.isCanFix() ? "FAILED" : "DEGRADED"),
                verificationArtifact.getSummary(),
                "unavailable"
        );
        log.info("requestId={}, agent={}, passed={}, costMs={}",
                sessionState.getRequestId(),
                AGENT_NAME,
                verificationArtifact.isPassed(),
                executionRecord.getDurationMs());
        return AgentSessionState.saveState(sessionState);
    }

    private VerificationArtifact verify(AgentSessionState sessionState) {
        CodeGenTypeEnum codeGenTypeEnum = resolveCodeGenType(sessionState.getTaskSpec());
        String generatedCodeDir = sessionState.getCodeArtifact() == null ? null : sessionState.getCodeArtifact().getGeneratedCodeDir();
        if (!generatedArtifactSupport.directoryExists(generatedCodeDir)) {
            return VerificationArtifact.builder()
                    .buildRequired(codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT)
                    .passed(false)
                    .buildResultDir(generatedCodeDir)
                    .issues(List.of("生成代码目录不存在"))
                    .summary("验证前检查失败")
                    .errorMessage("生成代码目录不存在")
                    .canFix(true)
                    .failureType("missing_artifact")
                    .build();
        }
        return switch (codeGenTypeEnum) {
            case HTML, MULTI_FILE -> verifyStaticProject(generatedCodeDir, codeGenTypeEnum);
            case VUE_PROJECT -> verifyVueProject(generatedCodeDir);
        };
    }

    private VerificationArtifact verifyStaticProject(String generatedCodeDir, CodeGenTypeEnum codeGenTypeEnum) {
        List<String> issues = new ArrayList<>();
        List<String> keyFiles = generatedArtifactSupport.listKeyFiles(generatedCodeDir, 20);
        if (!generatedArtifactSupport.fileExists(generatedCodeDir, "index.html")) {
            issues.add("缺少入口文件 index.html");
        }
        if (keyFiles.isEmpty()) {
            issues.add("未发现关键代码文件");
        }
        issues.addAll(findMissingStaticReferences(generatedCodeDir));
        issues.addAll(checkJavaScriptSyntax(generatedCodeDir));
        issues = deduplicate(issues);
        boolean passed = issues.isEmpty();
        String summary = passed
                ? "%s 静态验证通过".formatted(codeGenTypeEnum.getText())
                : "%s 静态验证失败，发现 %d 个问题".formatted(codeGenTypeEnum.getText(), issues.size());
        return VerificationArtifact.builder()
                .buildRequired(false)
                .passed(passed)
                .buildResultDir(generatedCodeDir)
                .details(keyFiles)
                .issues(issues)
                .summary(summary)
                .errorMessage(passed ? null : String.join("；", issues))
                .canFix(!passed)
                .failureType(passed ? null : determineStaticFailureType(issues))
                .build();
    }

    private VerificationArtifact verifyVueProject(String generatedCodeDir) {
        List<String> issues = new ArrayList<>();
        if (!generatedArtifactSupport.fileExists(generatedCodeDir, "package.json")) {
            issues.add("缺少 package.json");
        }
        boolean hasEntry = generatedArtifactSupport.fileExists(generatedCodeDir, "src/main.ts")
                || generatedArtifactSupport.fileExists(generatedCodeDir, "src/main.js");
        if (!hasEntry) {
            issues.add("缺少入口文件 src/main.ts 或 src/main.js");
        }
        if (!generatedArtifactSupport.fileExists(generatedCodeDir, "src/App.vue")) {
            issues.add("缺少关键组件 src/App.vue");
        }
        if (!issues.isEmpty()) {
            return VerificationArtifact.builder()
                    .buildRequired(true)
                    .passed(false)
                    .buildResultDir(generatedCodeDir)
                    .details(generatedArtifactSupport.listKeyFiles(generatedCodeDir, 20))
                    .issues(deduplicate(issues))
                    .summary("Vue 项目结构校验失败")
                    .errorMessage(String.join("；", deduplicate(issues)))
                    .canFix(true)
                    .failureType("structure")
                    .build();
        }
        try {
            boolean buildSuccess = vueProjectBuilder.buildProject(generatedCodeDir);
            String buildResultDir = buildSuccess
                    ? generatedArtifactSupport.resolveBuildResultDir(generatedCodeDir)
                    : generatedCodeDir;
            List<String> buildIssues = buildSuccess ? List.of() : List.of("npm install 或 npm run build 执行失败");
            return VerificationArtifact.builder()
                    .buildRequired(true)
                    .passed(buildSuccess)
                    .buildResultDir(buildResultDir)
                    .details(buildSuccess
                            ? generatedArtifactSupport.listKeyFiles(buildResultDir, 20)
                            : generatedArtifactSupport.listKeyFiles(generatedCodeDir, 20))
                    .issues(buildIssues)
                    .summary(buildSuccess ? "Vue 项目构建验证通过" : "Vue 项目构建验证失败")
                    .errorMessage(buildSuccess ? null : String.join("；", buildIssues))
                    .canFix(!buildSuccess)
                    .failureType(buildSuccess ? null : "build")
                    .build();
        } catch (Exception e) {
            log.error("agent={}, 构建验证异常: {}", AGENT_NAME, e.getMessage(), e);
            return VerificationArtifact.builder()
                    .buildRequired(true)
                    .passed(false)
                    .buildResultDir(generatedCodeDir)
                    .details(generatedArtifactSupport.listKeyFiles(generatedCodeDir, 20))
                    .issues(List.of("构建验证异常: " + e.getMessage()))
                    .summary("构建验证异常")
                    .errorMessage(e.getMessage())
                    .canFix(false)
                    .failureType("environment")
                    .build();
        }
    }

    private List<String> findMissingStaticReferences(String generatedCodeDir) {
        List<String> issues = new ArrayList<>();
        Path rootPath = Path.of(generatedCodeDir).toAbsolutePath().normalize();
        for (File htmlFile : listFilesByExtensions(generatedCodeDir, ".html", ".htm")) {
            String relativeHtmlPath = normalizeRelativePath(rootPath, htmlFile.toPath());
            String content = FileUtil.readUtf8String(htmlFile);
            Matcher matcher = STATIC_REF_PATTERN.matcher(content);
            while (matcher.find()) {
                String reference = sanitizeReference(matcher.group(1));
                if (shouldSkipReference(reference)) {
                    continue;
                }
                Path resolved = resolveReferencedPath(rootPath, htmlFile.toPath(), reference);
                if (!Files.exists(resolved)) {
                    issues.add("静态资源引用不存在: %s -> %s".formatted(relativeHtmlPath, reference));
                }
            }
        }
        return deduplicate(issues);
    }

    private List<String> checkJavaScriptSyntax(String generatedCodeDir) {
        List<String> issues = new ArrayList<>();
        Path rootPath = Path.of(generatedCodeDir).toAbsolutePath().normalize();
        for (File jsFile : listFilesByExtensions(generatedCodeDir, ".js")) {
            CommandResult commandResult = runCommand(jsFile.getParentFile(), Duration.ofSeconds(30), "node", "--check", jsFile.getAbsolutePath());
            if (!commandResult.success()) {
                issues.add("JS 语法检查失败: %s".formatted(normalizeRelativePath(rootPath, jsFile.toPath())));
            }
        }
        return deduplicate(issues);
    }

    private List<File> listFilesByExtensions(String rootDir, String... extensions) {
        if (!generatedArtifactSupport.directoryExists(rootDir)) {
            return List.of();
        }
        Set<String> suffixes = Set.of(extensions);
        List<File> files = new ArrayList<>();
        FileUtil.walkFiles(new File(rootDir), file -> {
            if (!file.isFile()) {
                return;
            }
            String normalized = file.getAbsolutePath().replace("\\", "/");
            if (normalized.contains("/node_modules/")
                    || normalized.contains("/dist/")
                    || normalized.contains("/target/")
                    || normalized.contains("/.git/")) {
                return;
            }
            for (String suffix : suffixes) {
                if (file.getName().toLowerCase().endsWith(suffix)) {
                    files.add(file);
                    return;
                }
            }
        });
        return files;
    }

    private String determineStaticFailureType(List<String> issues) {
        if (CollUtil.isEmpty(issues)) {
            return null;
        }
        if (issues.stream().anyMatch(issue -> issue.contains("语法"))) {
            return "syntax";
        }
        if (issues.stream().anyMatch(issue -> issue.contains("静态资源引用"))) {
            return "reference";
        }
        if (issues.stream().anyMatch(issue -> issue.contains("入口文件"))) {
            return "structure";
        }
        return "static_check";
    }

    private Path resolveReferencedPath(Path rootPath, Path htmlFilePath, String reference) {
        String normalizedReference = reference.replace("\\", "/");
        if (normalizedReference.startsWith("/")) {
            return rootPath.resolve(normalizedReference.substring(1)).normalize();
        }
        return htmlFilePath.getParent().resolve(normalizedReference).normalize();
    }

    private String normalizeRelativePath(Path rootPath, Path filePath) {
        return rootPath.relativize(filePath.toAbsolutePath().normalize()).toString().replace("\\", "/");
    }

    private String sanitizeReference(String reference) {
        if (reference == null) {
            return "";
        }
        int queryIndex = reference.indexOf('?');
        int hashIndex = reference.indexOf('#');
        int endIndex = reference.length();
        if (queryIndex >= 0) {
            endIndex = Math.min(endIndex, queryIndex);
        }
        if (hashIndex >= 0) {
            endIndex = Math.min(endIndex, hashIndex);
        }
        return reference.substring(0, endIndex).trim();
    }

    private boolean shouldSkipReference(String reference) {
        return StrUtil.isBlank(reference)
                || reference.startsWith("http://")
                || reference.startsWith("https://")
                || reference.startsWith("//")
                || reference.startsWith("data:")
                || reference.startsWith("mailto:")
                || reference.startsWith("tel:");
    }

    private List<String> deduplicate(List<String> items) {
        if (CollUtil.isEmpty(items)) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String item : items) {
            if (StrUtil.isNotBlank(item)) {
                unique.add(item.trim());
            }
        }
        return List.copyOf(unique);
    }

    private CommandResult runCommand(File workingDirectory, Duration timeout, String... command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory);
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes());
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(false, output, "command_timeout");
            }
            return new CommandResult(process.exitValue() == 0, output, process.exitValue() == 0 ? null : "command_failed");
        } catch (IOException e) {
            return new CommandResult(false, e.getMessage(), "command_io_error");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(false, e.getMessage(), "command_interrupted");
        }
    }

    private CodeGenTypeEnum resolveCodeGenType(TaskSpec taskSpec) {
        if (taskSpec == null) {
            return CodeGenTypeEnum.HTML;
        }
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(taskSpec.getTargetCodeGenType());
        return codeGenTypeEnum == null ? CodeGenTypeEnum.HTML : codeGenTypeEnum;
    }

    private record CommandResult(boolean success, String output, String errorType) {
    }
}
