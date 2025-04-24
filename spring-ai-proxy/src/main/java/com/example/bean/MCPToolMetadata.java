package com.example.bean;

import lombok.Data;

import java.util.Map;

@Data
public class MCPToolMetadata {
    //接口名字，注意唯一性
    private String name;
    //接口描述信息
    private String description;
    //接口说明信息
    private Object inputSchema;
    //实际调用地址
    private String url;
    //调用方式
    private String method;
    //超时时间
    private Integer timeout;
    //请求头信息
    private Map<String, String> headers;
    //请求体信息 json path 语法
    private Map<String, String> requestBodyResolver;
    //请求参数信息
    private Map<String, String> requestParamResolver;
}
