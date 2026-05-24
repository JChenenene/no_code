package com.lingchuang.ai.core;

import cn.hutool.json.JSONUtil;
import com.lingchuang.ai.ai.AiCodeGeneratorService;
import com.lingchuang.ai.ai.AiCodeGeneratorServiceFactory;
import com.lingchuang.ai.ai.model.HtmlCodeResult;
import com.lingchuang.ai.ai.model.MultiFileCodeResult;
import com.lingchuang.ai.ai.model.message.AiResponseMessage;
import com.lingchuang.ai.ai.model.message.ToolExecutedMessage;
import com.lingchuang.ai.ai.model.message.ToolRequestMessage;
import com.lingchuang.ai.ai.tools.ToolPathResolver;
import com.lingchuang.ai.constant.AppConstant;
import com.lingchuang.ai.core.builder.VueProjectBuilder;
import com.lingchuang.ai.core.parser.CodeParserExecutor;
import com.lingchuang.ai.core.saver.CodeFileSaverExecutor;
import com.lingchuang.ai.exception.BusinessException;
import com.lingchuang.ai.exception.ErrorCode;
import com.lingchuang.ai.model.enums.CodeGenTypeEnum;
import com.lingchuang.ai.monitor.MonitorContext;
import com.lingchuang.ai.monitor.MonitorContextHolder;
import com.lingchuang.ai.rag.RagInvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成门面类，组合代码生成和保存功能，门面模式
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ToolPathResolver toolPathResolver;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        // 根据 appId 获取相应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     * @return 保存的目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        return generateAndSaveCodeStream(userMessage, codeGenTypeEnum, appId, true);
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage        用户提示词
     * @param codeGenTypeEnum    生成类型
     * @param appId              应用 ID
     * @param buildAfterGenerate Vue 项目生成后是否立即构建
     * @return 保存的目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage,
                                                  CodeGenTypeEnum codeGenTypeEnum,
                                                  Long appId,
                                                  boolean buildAfterGenerate) {
        return generateAndSaveCodeStream(userMessage, codeGenTypeEnum, appId, buildAfterGenerate, null, appId);
    }

    public Flux<String> generateAndSaveCodeStream(String userMessage,
                                                  CodeGenTypeEnum codeGenTypeEnum,
                                                  Long appId,
                                                  boolean buildAfterGenerate,
                                                  String outputDir,
                                                  Long toolMemoryId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        // 根据 appId 获取相应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = subscribeWithInvocationContexts(aiCodeGeneratorService.generateHtmlCodeStream(userMessage));
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId, outputDir);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = subscribeWithInvocationContexts(aiCodeGeneratorService.generateMultiFileCodeStream(userMessage));
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId, outputDir);
            }
            case VUE_PROJECT -> {
                long resolvedToolMemoryId = toolMemoryId == null ? appId : toolMemoryId;
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(resolvedToolMemoryId, userMessage);
                yield processTokenStream(tokenStream, resolvedToolMemoryId, outputDir, buildAfterGenerate);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @param appId       应用 ID
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream,
                                            Long toolMemoryId,
                                            String outputDir,
                                            boolean buildAfterGenerate) {
        RagInvocationContext ragInvocationContext = RagInvocationContext.getCurrent();
        MonitorContext monitorContext = MonitorContextHolder.getContext();
        return Flux.create(sink -> {
            boolean registeredWorkspace = outputDir != null && !outputDir.isBlank() && toolMemoryId != null;
            if (registeredWorkspace) {
                toolPathResolver.registerWorkspaceRoot(toolMemoryId, outputDir);
            }
            Runnable cleanup = () -> {
                if (registeredWorkspace) {
                    toolPathResolver.clearWorkspaceRoot(toolMemoryId);
                }
            };
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        if (buildAfterGenerate) {
                            // 执行 Vue 项目构建（同步执行，确保预览时项目已就绪）
                            String projectPath = outputDir == null || outputDir.isBlank()
                                    ? AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + toolMemoryId
                                    : outputDir;
                            vueProjectBuilder.buildProject(projectPath);
                        }
                        cleanup.run();
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        cleanup.run();
                        sink.error(error);
                    });
            sink.onCancel(() -> cleanup.run());
            sink.onDispose(() -> cleanup.run());
            withInvocationContexts(ragInvocationContext, monitorContext, tokenStream::start);
        });
    }

    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @param appId       应用 ID
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId, String outputDir) {
        // 字符串拼接器，用于当流式返回所有的代码之后，再保存代码
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            // 实时收集代码片段
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            // 流式返回完成后，保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码
                File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId, outputDir);
                log.info("保存成功，目录为：{}", saveDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }

    private Flux<String> subscribeWithInvocationContexts(Flux<String> source) {
        RagInvocationContext ragInvocationContext = RagInvocationContext.getCurrent();
        MonitorContext monitorContext = MonitorContextHolder.getContext();
        if (ragInvocationContext == null && monitorContext == null) {
            return source;
        }
        return Flux.create(sink -> withInvocationContexts(ragInvocationContext, monitorContext, () -> {
            Disposable disposable = source.subscribe(
                    sink::next,
                    sink::error,
                    sink::complete
            );
            sink.onCancel(disposable::dispose);
            sink.onDispose(disposable::dispose);
        }));
    }

    private void withInvocationContexts(RagInvocationContext ragInvocationContext,
                                        MonitorContext monitorContext,
                                        Runnable action) {
        if (monitorContext != null) {
            MonitorContextHolder.setContext(monitorContext);
        }
        if (ragInvocationContext != null) {
            RagInvocationContext.setCurrent(ragInvocationContext);
        }
        try {
            action.run();
        } finally {
            RagInvocationContext.clear();
            MonitorContextHolder.clearContext();
        }
    }
}
