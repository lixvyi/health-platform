package com.csu.health.portal.mybatis;

import com.csu.health.portal.module.auth.entity.SysUser;
import com.csu.health.portal.module.auth.mapper.SysUserMapper;
import com.csu.health.portal.module.content.entity.CmsContent;
import com.csu.health.portal.module.content.mapper.CmsContentMapper;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * 讲义标准 MyBatis 原生测试流程：
 * 1. 加载 mybatis-config-native.xml
 * 2. 构建 SqlSessionFactory（静态一次）
 * 3. 创建 SqlSession，getMapper 调用
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MyBatisNativeSessionTest {

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void initFactory() throws IOException {
        InputStream in = Resources.getResourceAsStream("mybatis-config-native.xml");
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(in);
    }

    @Test
    @Order(1)
    @DisplayName("讲义流程：getMapper 查询用户")
    void testGetMapperFindUser() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            SysUserMapper mapper = session.getMapper(SysUserMapper.class);
            SysUser user = mapper.findByUsername("admin");
            Assertions.assertNotNull(user);
            Assertions.assertEquals("ADMIN", user.getRole());
        }
    }

    @Test
    @Order(2)
    @DisplayName("XML映射：动态SQL where-if 分页查询内容")
    void testDynamicSqlPageQuery() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            CmsContentMapper mapper = session.getMapper(CmsContentMapper.class);
            List<CmsContent> list = mapper.selectPageByCondition("NEWS", null, null, 1, 0, 10);
            Assertions.assertFalse(list.isEmpty());
        }
    }

    @Test
    @Order(3)
    @DisplayName("XML映射：foreach 按 id 列表查询")
    void testForeachSelectByIds() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            CmsContentMapper mapper = session.getMapper(CmsContentMapper.class);
            List<CmsContent> list = mapper.selectByIds(Arrays.asList(1L, 2L));
            Assertions.assertNotNull(list);
        }
    }
}
