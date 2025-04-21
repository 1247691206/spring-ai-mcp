package com.example.utils;

import com.example.bean.MCPToolMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.ai.model.ModelOptionsUtils.OBJECT_MAPPER;

public class ToolsLoader {

    public static ObjectMapper objectMapper = new ObjectMapper();

    public static List<MCPToolMetadata> loadTools() {
        List<MCPToolMetadata> tools = new ArrayList<MCPToolMetadata>();
        Path path = Paths.get(ToolsLoader.class.getClassLoader().getResource("tools.json").getPath());
        File file = path.toFile();
        if (!file.exists()) {
            return tools;
        } else {
            try {
                JavaType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, MCPToolMetadata.class);
                tools = objectMapper.readValue(file, javaType);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return tools;

    }

    public static void main(String[] args) {
        List<MCPToolMetadata> mcpToolMetadatas = loadTools();
        for (MCPToolMetadata mcpToolMetadata : mcpToolMetadatas) {
            try {
                McpSchema.JsonSchema jsonSchema = OBJECT_MAPPER.readValue(mcpToolMetadata.getInputSchema().toString(), McpSchema.JsonSchema.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

    }
}
