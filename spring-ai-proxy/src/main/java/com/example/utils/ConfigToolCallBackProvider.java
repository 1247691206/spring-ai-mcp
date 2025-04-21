package com.example.utils;

import com.example.CommonClient;
import com.example.bean.MCPToolMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.tool.util.ToolUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 基于配置的tool callback
 *
 * @see MethodToolCallbackProvider
 */
public class ConfigToolCallBackProvider implements ToolCallbackProvider {


    private static final Logger logger = LoggerFactory.getLogger(ConfigToolCallBackProvider.class);

    private final List<MCPToolMetadata> toolMetadataList;
    private CommonClient commonClient;

    private ConfigToolCallBackProvider(List<MCPToolMetadata> toolMetadataList, CommonClient commonClient) {
        Assert.notNull(toolMetadataList, "toolMetadataList cannot be null");
        Assert.noNullElements(toolMetadataList, "toolMetadataList cannot contain null elements");
        this.toolMetadataList = toolMetadataList;
        this.commonClient = commonClient;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        Method method = Arrays.stream(ReflectionUtils.getDeclaredMethods(commonClient.getClass())).filter(toolMethod -> toolMethod.getName().equals("execute")).findFirst().get();
        Assert.notNull(method, "execute method not found");
        var toolCallbacks = toolMetadataList.stream()
                .map(toolMetadata -> ConfigToolCallBack.builder()
                        .toolDefinition(ToolDefinition.builder().name(toolMetadata.getName()).description(toolMetadata.getDescription()).inputSchema(toolMetadata.getInputSchema().toString()).build())
                        //toolMetadata 注解@Tool.returnDirect
                        .toolMetadata(new DefaultToolMetadata(false))
                        .toolMethod(method)
                        .toolObject(commonClient)
                        //toolMetadata 注解@Tool.resultConverter
                        .toolCallResultConverter(new DefaultToolCallResultConverter())
                        .build()).toArray(ToolCallback[]::new);

        validateToolCallbacks(toolCallbacks);

        return toolCallbacks;
    }

    private boolean isFunctionalType(Method toolMethod) {
        var isFunction = ClassUtils.isAssignable(toolMethod.getReturnType(), Function.class)
                || ClassUtils.isAssignable(toolMethod.getReturnType(), Supplier.class)
                || ClassUtils.isAssignable(toolMethod.getReturnType(), Consumer.class);

        if (isFunction) {
            logger.warn("Method {} is annotated with @Tool but returns a functional type. "
                    + "This is not supported and the method will be ignored.", toolMethod.getName());
        }

        return isFunction;
    }

    private void validateToolCallbacks(ToolCallback[] toolCallbacks) {
        List<String> duplicateToolNames = ToolUtils.getDuplicateToolNames(toolCallbacks);
        if (!duplicateToolNames.isEmpty()) {
            throw new IllegalStateException("Multiple tools with the same name (%s) found in sources: %s".formatted(
                    String.join(", ", duplicateToolNames),
                    toolMetadataList.stream().map(o -> o.getClass().getName()).collect(Collectors.joining(", "))));
        }
    }

    public static ConfigToolCallBackProvider.Builder builder() {
        return new ConfigToolCallBackProvider.Builder();
    }

    public static class Builder {

        private List<MCPToolMetadata> toolMetadataList;

        private CommonClient commonClient;

        private Builder() {
        }

        public ConfigToolCallBackProvider.Builder toolMetadata(MCPToolMetadata... toolMetadata) {
            Assert.notNull(toolMetadata, "toolObjects cannot be null");
            this.toolMetadataList = Arrays.asList(toolMetadata);
            return this;
        }

        public ConfigToolCallBackProvider.Builder commonClient(CommonClient commonClient) {
            Assert.notNull(commonClient, "toolObjects cannot be null");
            this.commonClient = commonClient;
            return this;
        }

        public ConfigToolCallBackProvider build() {
            List<MCPToolMetadata> mcpToolMetadataList = ToolsLoader.loadTools();
            toolMetadataList = toolMetadataList == null ? new ArrayList<>() : toolMetadataList;
            commonClient = commonClient == null ? new CommonClient() : commonClient;
            toolMetadataList.addAll(mcpToolMetadataList);
            return new ConfigToolCallBackProvider(toolMetadataList, commonClient);
        }

    }


    public static void main(String[] args) {
        ConfigToolCallBackProvider configToolCallBackProvider = ConfigToolCallBackProvider.builder().build();
        ToolCallback[] toolCallbacks = configToolCallBackProvider.getToolCallbacks();
        System.out.println(toolCallbacks.length);
    }
}
