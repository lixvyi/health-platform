# MyBatis 配置说明（对照讲义）

本模块按《MyBatis编程技术》讲义完成以下配置，并与 `health_portal` 数据库对接。

## 配置文件一览

| 文件 | 讲义对应章节 | 作用 |
|------|-------------|------|
| `jdbc.properties` | JDBC 配置 | 驱动、URL、账号、密码 |
| `log4j.properties` | 日志配置 | 控制台 SQL 日志 |
| `mybatis-config.xml` | 核心配置（Spring 集成版） | settings、typeAliases |
| `mybatis-config-native.xml` | 核心配置（原生测试版） | 含 environments + mappers |
| `mappers/*.xml` | Mapper 映射文件 | SQL 与 Dao 绑定 |

## jdbc.properties（已填写门户库）

```properties
mysql.driver=com.mysql.cj.jdbc.Driver
mysql.url=jdbc:mysql://localhost:3306/health_portal?...
mysql.username=root
mysql.password=root123
```

修改数据库账号密码时**只改此文件**，`application.yml` 通过 `${mysql.*}` 自动引用。

## 执行流程（讲义）

1. 读取 `mybatis-config-native.xml`
2. `SqlSessionFactoryBuilder` 构建 `SqlSessionFactory`
3. `openSession()` 获取 `SqlSession`
4. `getMapper(XxxMapper.class)` 调用 XML 中的 SQL

原生测试类：`src/test/java/.../mybatis/MyBatisNativeSessionTest.java`

## XML 已实现讲义知识点

- `<sql>` + `<include>` 复用
- `<where>` + `<if>` 动态查询
- `<set>` + `<if>` 动态更新
- `<foreach>` 批量 id 查询
- `namespace` = Mapper 接口全限定名
- `id` = 接口方法名

## Spring Boot 集成

`application.yml`：

```yaml
spring.config.import: classpath:jdbc.properties
mybatis-plus.mapper-locations: classpath:mappers/*.xml
mybatis-plus.config-location: classpath:mybatis-config.xml
```

业务层 `ContentService` 已改为调用 XML Mapper 方法（动态 SQL）。
