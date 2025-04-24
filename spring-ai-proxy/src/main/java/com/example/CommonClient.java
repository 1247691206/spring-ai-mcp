package com.example;

import com.example.bean.MCPToolMetadata;
import com.example.bean.User;
import com.example.utils.HttpRemoteInvoker;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;

import static com.example.utils.ToolsLoader.loadTools;

public class CommonClient {

    ObjectMapper objectMapper = new ObjectMapper();

    public String execute(String query) throws Exception {
        JavaType javaType = objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class);
        Map<String, String> argument = objectMapper.readValue(query, javaType);
        String toolName = argument.get("toolName");
        argument.remove("toolName");
        List<MCPToolMetadata> mcpToolMetadata = loadTools();
        MCPToolMetadata metadata = mcpToolMetadata.stream().filter(mcpToolMetadata1 -> mcpToolMetadata1.getName().equals(toolName)).findFirst().orElse(null);
        HttpRemoteInvoker httpRemoteInvoker = new HttpRemoteInvoker();
        String response;
        try {
            response = httpRemoteInvoker.invoke(metadata, argument);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println(String.format("execute result for: %s \n response: %s ", query, response));
        return response;
    }

    @Tool(description = "测试基础类型")
    public String testBaseDemo(@ToolParam(description = "String信息") String query) {
        System.out.println("execute query: " + query);
        return "execute result for: " + query;
    }

    @Tool(description = "测试dto类型 demo")
    public String testDtoDemo(@ToolParam(description = "User用户集合") User user) {
        System.out.println("execute query: " + user);
        return "execute result for: " + user;
    }

    @Tool(description = "测试基础组合类型")
    public String testListBaseDemo(@ToolParam(description = "List<Sting>信息") List<String> query) {
        System.out.println("execute query: " + query);
        return "execute result for: " + query;
    }

    @Tool(description = "测试dto组合类型 demo")
    public String testListDtoDemo(@ToolParam(description = "List<User>用户集合") List<User> users) {
        System.out.println("execute query: " + users);
        return "execute result for: " + users;
    }
}
