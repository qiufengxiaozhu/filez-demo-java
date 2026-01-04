package com.filez.demo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 数据库配置类，用于SQLite数据库初始化
 */
@Slf4j
@Configuration
@Order(1)
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    private final DataSource dataSource;

    public DatabaseConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 初始化SQLite数据库目录和表结构
     */
    @PostConstruct
    public void initDatabase() {
        try {
            // 从jdbc:sqlite:./data/filez_demo.db中提取文件路径
            if (datasourceUrl.startsWith("jdbc:sqlite:")) {
                String dbPath = datasourceUrl.substring("jdbc:sqlite:".length());
                File dbFile = new File(dbPath);
                File parentDir = dbFile.getParentFile();
                
                // 确保数据库文件的父目录存在
                if (parentDir != null && !parentDir.exists()) {
                    if (parentDir.mkdirs()) {
                        log.info("创建SQLite数据库目录: {}", parentDir.getAbsolutePath());
                    }
                }
                
                log.info("SQLite数据库文件路径: {}", dbFile.getAbsolutePath());
                
                // 检查是否需要初始化数据库
                if (!dbFile.exists() || dbFile.length() == 0) {
                    initializeDatabase();
                } else {
                    // 检查表是否存在
                    if (!isTableExists()) {
                        initializeDatabase();
                    }
                }
            }
        } catch (Exception e) {
            log.error("初始化SQLite数据库失败", e);
        }
    }

    /**
     * 检查表是否存在
     */
    private boolean isTableExists() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='sys_user'");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 初始化数据库表结构和数据
     */
    private void initializeDatabase() {
        log.info("开始初始化SQLite数据库表结构和数据");
        
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // 执行DDL脚本
            executeScript(statement, "sql/ddl.sql");
            
            // 执行DML脚本  
            executeScript(statement, "sql/dml.sql");
            
            log.info("SQLite数据库初始化完成");
        } catch (Exception e) {
            log.error("初始化数据库脚本执行失败", e);
        }
    }

    /**
     * 执行SQL脚本
     */
    private void executeScript(Statement statement, String scriptPath) throws Exception {
        Resource resource = new ClassPathResource(scriptPath);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            StringBuilder sql = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }
                
                sql.append(line).append(" ");
                
                if (line.endsWith(";")) {
                    String sqlStatement = sql.toString().trim();
                    if (!sqlStatement.isEmpty()) {
                        statement.execute(sqlStatement);
                        log.debug("执行SQL: {}", sqlStatement);
                    }
                    sql.setLength(0);
                }
            }
        }
    }
}
