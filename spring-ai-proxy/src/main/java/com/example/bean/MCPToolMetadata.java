package com.example.bean;

import lombok.Data;

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
}
