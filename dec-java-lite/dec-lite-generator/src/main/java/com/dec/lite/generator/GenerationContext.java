package com.dec.lite.generator;

import java.util.List;
import java.util.Map;

record GenerationContext(String basePackage, Map<String, Object> projectModel,
                           Map<String, Object> applicationModel,
                           List<DesignProjectionGenerator.Source> projections,
                           List<Map<String, Object>> entities,
                           List<Map<String, Object>> enums,
                           List<Map<String, Object>> views,
                           List<Map<String, Object>> requests,
                           List<Map<String, Object>> apis,
                           List<Map<String, Object>> dataSources) { }
