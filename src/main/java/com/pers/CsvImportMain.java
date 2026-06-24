package com.pers;

import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Properties;

/**
 * Hello world!
 */
public class CsvImportMain {
    private static final Properties props = new Properties();

    public static void main(String[] args) {
        try {
            // 加载外部配置文件
            loadConfig();
            String dbType = props.getProperty("db.type", "mysql").trim().toLowerCase();
            String dbUrl = props.getProperty("db.url");
            String dbUser = props.getProperty("db.user");
            String dbPwd = props.getProperty("db.password");
            String csvPath = props.getProperty("csv.file.path");
            int batchSize = Integer.parseInt(props.getProperty("batch.size"));
            boolean hasHeader = "1".equals(props.getProperty("csv.has.header"));
            String insertSql = props.getProperty("insert.sql");

            File csvFile = new File(csvPath);
            if (!csvFile.exists()) {
                System.err.println("错误：CSV文件不存在 -> " + csvPath);
                return;
            }

            // 根据数据库类型加载对应驱动
            String driverClass;
            if ("sqlserver".equals(dbType)) {
                driverClass = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
                System.out.println("当前数据库类型：SQLServer");
            } else {
                driverClass = "com.mysql.cj.jdbc.Driver";
                System.out.println("当前数据库类型：MySQL（默认）");
            }
            Class.forName(driverClass);

            // 获取数据库连接，关闭自动提交手动事务
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPwd)) {
                conn.setAutoCommit(false);
                try (PreparedStatement pstmt = conn.prepareStatement(insertSql);
                     InputStream is = new FileInputStream(csvFile);
                     InputStreamReader isr = new InputStreamReader(is, "UTF-8");
                     CSVReader csvReader = new CSVReader(isr)
                ) {
                    String[] row;
                    int totalCount = 0;

                    // 跳过表头
                    if (hasHeader) {
                        csvReader.readNext();
                    }

                    while ((row = csvReader.readNext()) != null) {
                        // 按实际表字段映射占位符，自行修改
                        pstmt.setObject(1, row[0]);
                        pstmt.setString(2, row[1]);
                        pstmt.setString(3, row[2]);
                        pstmt.setString(4, row[3]);

                        pstmt.addBatch();
                        totalCount++;

                        // 分批提交
                        if (totalCount % batchSize == 0) {
                            pstmt.executeBatch();
                            conn.commit();
                            pstmt.clearBatch();
                            System.out.println("已导入：" + totalCount + " 条");
                        }
                    }

                    // 处理剩余数据
                    pstmt.executeBatch();
                    conn.commit();
                    System.out.println("导入完成！总记录数：" + totalCount);

                } catch (Exception e) {
                    conn.rollback();
                    System.err.println("导入异常，事务已全部回滚！");
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("程序启动失败：");
            e.printStackTrace();
        }
    }

    /** 优先读取外部config.properties，无则读取jar内置配置 */
    private static void loadConfig() throws Exception {
        File outerConfig = new File("config.properties");
        if (outerConfig.exists()) {
            try (InputStream fis = new FileInputStream(outerConfig)) {
                props.load(fis);
                System.out.println("成功加载外部配置文件 config.properties");
            }
        } else {
            try (InputStream is = CsvImportMain.class.getResourceAsStream("/config.properties")) {
                if (is == null) {
                    throw new RuntimeException("缺少配置文件，请将config.properties放到jar包同目录");
                }
                props.load(is);
                System.out.println("使用Jar包内置默认配置");
            }
        }
    }
}
