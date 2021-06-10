//package com.virjar.echo.meta.test;
//
//
//import com.baomidou.mybatisplus.core.toolkit.StringPool;
//import com.baomidou.mybatisplus.generator.AutoGenerator;
//import com.baomidou.mybatisplus.generator.InjectionConfig;
//import com.baomidou.mybatisplus.generator.config.*;
//import com.baomidou.mybatisplus.generator.config.po.TableInfo;
//import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
//import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
//
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * Created by virjar on 2018/11/3.
// */
//public class CodeGenerator {
//
//
//    public static void main(String[] args) {
//        // 代码生成器
//        AutoGenerator mpg = new AutoGenerator();
//
//        // 全局配置
//        GlobalConfig gc = new GlobalConfig();
//        final String projectPath = System.getProperty("user.dir");
//        gc.setOutputDir(projectPath + "/echo-meta-server/src/main/java");
//        gc.setAuthor("virjar");
//        gc.setOpen(false);
//        mpg.setGlobalConfig(gc);
//
//        // 数据源配置
//        DataSourceConfig dsc = new DataSourceConfig();
//        dsc.setUrl("jdbc:mysql://echonew.virjar.com:3307/echo?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull");
//        // dsc.setSchemaName("public");
//        dsc.setDriverName("com.mysql.cj.jdbc.Driver");
//        dsc.setUsername("echo");
//        dsc.setPassword("Echo1234");
//        mpg.setDataSource(dsc);
//
//        // 包配置
//        final PackageConfig pc = new PackageConfig();
//        pc.setModuleName("");
//        pc.setParent("com.virjar.echo.meta.server");
//        mpg.setPackageInfo(pc);
//
//        // 自定义配置
//        InjectionConfig cfg = new InjectionConfig() {
//            @Override
//            public void initMap() {
//                // to do nothing
//            }
//        };
//        List<FileOutConfig> focList = new ArrayList<>();
//        focList.add(new FileOutConfig("/templates/mapper.xml.ftl") {
//            @Override
//            public String outputFile(TableInfo tableInfo) {
//                // 自定义输入文件名称
//                return projectPath + "/echo-meta-server/src/main/resources/mapper/" + pc.getModuleName()
//                        + "/" + tableInfo.getEntityName() + "Mapper" + StringPool.DOT_XML;
//            }
//        });
//        cfg.setFileOutConfigList(focList);
//        mpg.setCfg(cfg);
//        mpg.setTemplate(new TemplateConfig().setXml(null));
//
//
//        // 策略配置
//        StrategyConfig strategy = new StrategyConfig();
//        strategy.setNaming(NamingStrategy.underline_to_camel);
//        strategy.setColumnNaming(NamingStrategy.underline_to_camel);
//        //strategy.setSuperEntityClass("com.baomidou.ant.common.BaseEntity");
//        strategy.setEntityLombokModel(true);
//        strategy.setRestControllerStyle(true);
//        //strategy.setSuperControllerClass("com.baomidou.ant.common.BaseController");
//        //strategy.setSuperEntityColumns("id");
//        strategy.setControllerMappingHyphenStyle(true);
//        strategy.setEntityColumnConstant(true);
//        strategy.setInclude(
//                "nat_mapping_server",
//                "downstream_server",
//                "proxy_resource",
//                "user_info",
//                "client_info",
//                "auth_white_ip",
//                "penetration"
//        );
//        strategy.setTablePrefix(pc.getModuleName() + "_");
//        mpg.setStrategy(strategy);
//        mpg.setTemplateEngine(new FreemarkerTemplateEngine());
//
//        mpg.getGlobalConfig().setFileOverride(true).setSwagger2(true);
//        mpg.execute();
//    }
//}
