package com.example;

import com.example.bean.User;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

public class CommonClient {

    @Tool(description = "一个通用的客户端，用来发起 通用的httpclient 请求")
    public String execute(@ToolParam(description = "请求体信息") String query) {
        System.out.println("execute query: " + query);
        return "execute result for: " + query;
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
