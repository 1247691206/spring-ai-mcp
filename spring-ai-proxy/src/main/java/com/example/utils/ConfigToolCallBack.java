package com.example.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 由于回调时会校验 硬编码方法的一致性，故需要重写类
 * org.springframework.ai.tool.method.MethodToolCallback#buildMethodArguments
 *
 * @see MethodToolCallback
 */
public class ConfigToolCallBack implements ToolCallback {

    ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(ConfigToolCallBack.class);

    private static final ToolCallResultConverter DEFAULT_RESULT_CONVERTER = new DefaultToolCallResultConverter();

    private static final ToolMetadata DEFAULT_TOOL_METADATA = ToolMetadata.builder().build();

    private final ToolDefinition toolDefinition;

    private final ToolMetadata toolMetadata;

    private final Method toolMethod;

    @Nullable
    private final Object toolObject;

    private final ToolCallResultConverter toolCallResultConverter;

    public ConfigToolCallBack(ToolDefinition toolDefinition, @Nullable ToolMetadata toolMetadata, Method toolMethod, @Nullable Object toolObject, @Nullable ToolCallResultConverter toolCallResultConverter) {
        Assert.notNull(toolDefinition, "toolDefinition cannot be null");
        Assert.notNull(toolMethod, "toolMethod cannot be null");
        Assert.isTrue(Modifier.isStatic(toolMethod.getModifiers()) || toolObject != null, "toolObject cannot be null for non-static methods");
        this.toolDefinition = toolDefinition;
        this.toolMetadata = toolMetadata != null ? toolMetadata : DEFAULT_TOOL_METADATA;
        this.toolMethod = toolMethod;
        this.toolObject = toolObject;
        this.toolCallResultConverter = toolCallResultConverter != null ? toolCallResultConverter : DEFAULT_RESULT_CONVERTER;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return toolMetadata;
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, @Nullable ToolContext toolContext) {
        Assert.hasText(toolInput, "toolInput cannot be null or empty");

        logger.debug("Starting execution of tool: {}", toolDefinition.name());

        validateToolContextSupport(toolContext);

        Map<String, Object> toolArguments = extractToolArguments(toolInput);

        //Object[] methodArguments = buildMethodArguments(toolArguments, toolContext);
        Object[] methodArguments = buildConfigMethodArguments(toolArguments, toolContext, toolDefinition);

        Object result = callMethod(methodArguments);

        logger.debug("Successful execution of tool: {}", toolDefinition.name());

        Type returnType = toolMethod.getGenericReturnType();

        return toolCallResultConverter.convert(result, returnType);
    }

    private void validateToolContextSupport(@Nullable ToolContext toolContext) {
        var isNonEmptyToolContextProvided = toolContext != null && !CollectionUtils.isEmpty(toolContext.getContext());
        var isToolContextAcceptedByMethod = Stream.of(toolMethod.getParameterTypes()).anyMatch(type -> ClassUtils.isAssignable(type, ToolContext.class));
        if (isToolContextAcceptedByMethod && !isNonEmptyToolContextProvided) {
            throw new IllegalArgumentException("ToolContext is required by the method as an argument");
        }
    }

    private Map<String, Object> extractToolArguments(String toolInput) {
        return JsonParser.fromJson(toolInput, new TypeReference<>() {
        });
    }

    // Based on the implementation in MethodInvokingFunctionCallback.
    private Object[] buildMethodArguments(Map<String, Object> toolInputArguments, @Nullable ToolContext toolContext) {
        return Stream.of(toolMethod.getParameters()).map(parameter -> {
            if (parameter.getType().isAssignableFrom(ToolContext.class)) {
                return toolContext;
            }
            Object rawArgument = toolInputArguments.get(parameter.getName());
            return buildTypedArgument(rawArgument, parameter.getType());
        }).toArray();
    }

    /**
     * 给commonClient 调用接口类型 string json 类型
     *
     * @return
     */
    private Object[] buildConfigMethodArguments(Map<String, Object> toolInputArguments, @Nullable ToolContext toolContext, ToolDefinition toolDefinition) {
        String rawInputArgument = "";
        try {
            toolInputArguments.put("toolName", toolDefinition.name());
            rawInputArgument = objectMapper.writeValueAsString(toolInputArguments);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        System.out.println("rawInputArgument = " + rawInputArgument);
        return new Object[]{buildTypedArgument(rawInputArgument, String.class)};
    }

    @Nullable
    private Object buildTypedArgument(@Nullable Object value, Class<?> type) {
        if (value == null) {
            return null;
        }
        return JsonParser.toTypedObject(value, type);
    }

    @Nullable
    private Object callMethod(Object[] methodArguments) {
        if (isObjectNotPublic() || isMethodNotPublic()) {
            toolMethod.setAccessible(true);
        }

        Object result;
        try {
            result = toolMethod.invoke(toolObject, methodArguments);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not access method: " + ex.getMessage(), ex);
        } catch (InvocationTargetException ex) {
            throw new ToolExecutionException(toolDefinition, ex.getCause());
        }
        return result;
    }

    private boolean isObjectNotPublic() {
        return toolObject != null && !Modifier.isPublic(toolObject.getClass().getModifiers());
    }

    private boolean isMethodNotPublic() {
        return !Modifier.isPublic(toolMethod.getModifiers());
    }

    @Override
    public String toString() {
        return "ConfigToolCallBack{" + "toolDefinition=" + toolDefinition + ", toolMetadata=" + toolMetadata + '}';
    }

    public static ConfigToolCallBack.Builder builder() {
        return new ConfigToolCallBack.Builder();
    }

    public static class Builder {

        private ToolDefinition toolDefinition;

        private ToolMetadata toolMetadata;

        private Method toolMethod;

        private Object toolObject;

        private ToolCallResultConverter toolCallResultConverter;

        private Builder() {
        }

        public ConfigToolCallBack.Builder toolDefinition(ToolDefinition toolDefinition) {
            this.toolDefinition = toolDefinition;
            return this;
        }

        public ConfigToolCallBack.Builder toolMetadata(ToolMetadata toolMetadata) {
            this.toolMetadata = toolMetadata;
            return this;
        }

        public ConfigToolCallBack.Builder toolMethod(Method toolMethod) {
            this.toolMethod = toolMethod;
            return this;
        }

        public ConfigToolCallBack.Builder toolObject(Object toolObject) {
            this.toolObject = toolObject;
            return this;
        }

        public ConfigToolCallBack.Builder toolCallResultConverter(ToolCallResultConverter toolCallResultConverter) {
            this.toolCallResultConverter = toolCallResultConverter;
            return this;
        }

        public ConfigToolCallBack build() {
            return new ConfigToolCallBack(toolDefinition, toolMetadata, toolMethod, toolObject, toolCallResultConverter);
        }

    }
}
