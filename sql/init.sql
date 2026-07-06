-- 健康大数据应用创新研发中心门户系统
CREATE DATABASE IF NOT EXISTS health_portal DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE health_portal;

-- 用户表
CREATE TABLE sys_user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL COMMENT 'BCrypt',
    real_name   VARCHAR(64),
    role        VARCHAR(32)  NOT NULL DEFAULT 'EDITOR' COMMENT 'ADMIN/EDITOR',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='系统用户';

-- 内容分类
CREATE TABLE cms_category (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL,
    code        VARCHAR(32)  NOT NULL UNIQUE COMMENT 'NEWS/NOTICE/POLICY/KNOWLEDGE',
    sort_order  INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='内容分类';

-- 统一内容表（新闻/公告/政策/知识库）
CREATE TABLE cms_content (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_code VARCHAR(32)  NOT NULL COMMENT 'NEWS/NOTICE/POLICY/KNOWLEDGE',
    title         VARCHAR(256) NOT NULL,
    summary       VARCHAR(512),
    content       MEDIUMTEXT,
    source_url    VARCHAR(512) COMMENT '原文链接(采集去重)',
    cover_url     VARCHAR(512),
    author        VARCHAR(64),
    view_count    INT          NOT NULL DEFAULT 0,
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布',
    publish_time  DATETIME,
    created_by    BIGINT,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cat_status_pub (category_code, status, publish_time)
) ENGINE=InnoDB COMMENT='门户内容';

-- 首页轮播
CREATE TABLE cms_banner (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    title       VARCHAR(128) NOT NULL,
    image_url   VARCHAR(512) NOT NULL,
    link_url    VARCHAR(512),
    sort_order  INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='首页轮播';

-- 应用中心
CREATE TABLE cms_app (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(128) NOT NULL,
    icon_url    VARCHAR(512),
    link_url    VARCHAR(512) NOT NULL,
    description VARCHAR(512),
    sort_order  INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='应用中心';

-- 关于我们 / 站点配置
CREATE TABLE cms_site_config (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key  VARCHAR(64)  NOT NULL UNIQUE,
    config_value MEDIUMTEXT,
    remark      VARCHAR(256),
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='站点配置';

-- AI 对话记录
CREATE TABLE ai_chat_history (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT,
    session_id  VARCHAR(64),
    role        VARCHAR(16) NOT NULL COMMENT 'user/assistant',
    message     TEXT        NOT NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id)
) ENGINE=InnoDB COMMENT='AI问答记录';

-- 初始管理员 admin/Admin@123
INSERT INTO sys_user (username, password, real_name, role) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'ADMIN'),
('editor', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '内容编辑', 'EDITOR');

INSERT INTO cms_category (name, code, sort_order) VALUES
('新闻中心', 'NEWS', 1),
('通知公告', 'NOTICE', 2),
('卫生政策', 'POLICY', 3),
('健康知识库', 'KNOWLEDGE', 4);

INSERT INTO cms_banner (title, image_url, link_url, sort_order) VALUES
('健康大数据创新研发中心', 'https://picsum.photos/1200/400?random=1', '/news', 1),
('智慧医疗数据服务平台', 'https://picsum.photos/1200/400?random=2', '/policy', 2);

INSERT INTO cms_app (name, icon_url, link_url, description, sort_order) VALUES
('国家统计数据库', 'https://picsum.photos/64/64?random=3', 'https://data.stats.gov.cn', '国家统计局官方数据查询与下载', 1),
('上海公共数据开放', 'https://picsum.photos/64/64?random=4', '/data?tab=shanghai', '医疗机构、预防接种、基层卫生等20类CSV', 2),
('数据资源目录', 'https://picsum.photos/64/64?random=5', '/data', '全部45类开放健康数据', 3);

INSERT INTO cms_site_config (config_key, config_value, remark) VALUES
('about_title', '健康大数据应用创新研发中心', '关于我们标题'),
('about_content', '<p>中心依托政务数据共享授权与政府数据开放平台，建设健康大数据统一计算与服务平台，面向科研、产业与公众提供数据服务与知识服务。</p><p>统计数据引用<a href="https://data.stats.gov.cn" target="_blank" rel="noopener">国家统计数据库</a>时，须注明「来源：国家统计局」，并遵守其用户使用协议。</p><p>地址：湖南省长沙市中南大学</p><p>电话：0731-88888888</p>', '关于我们正文'),
('home_intro', '依托政府数据开放平台与政务数据共享授权，汇聚卫生、统计等合规数据资源，打造健康大数据创新生态', '首页简介');

INSERT INTO cms_content (category_code, title, summary, content, author, status, publish_time) VALUES
('NEWS', '中心承办健康大数据产学研对接会', '推动校企协同创新', '<p>近日，健康大数据应用创新研发中心成功举办产学研对接会，多家医疗机构与科技企业参与。</p>', '宣传部', 1, NOW()),
('NEWS', '我中心数据平台通过等保二级测评', '平台建设取得阶段性成果', '<p>经过严格测评，中心数据平台顺利通过网络安全等级保护二级测评。</p>', '技术部', 1, NOW()),
('NOTICE', '关于门户系统上线试运行的通知', '门户将于本周上线', '<p>健康大数据门户系统现进入试运行阶段，欢迎各部门提出宝贵意见。</p>', '办公室', 1, NOW()),
('POLICY', '「健康中国2030」规划纲要摘要', '推进健康中国建设', '<p>为推进健康中国建设，提高人民健康水平，制定本规划纲要。</p>', '政策研究室', 1, NOW()),
('KNOWLEDGE', '高血压患者的日常饮食建议', '低盐低脂、均衡营养', '<p>高血压患者应控制钠盐摄入，多吃蔬菜水果，适量运动，定期监测血压。</p>', '健康科普组', 1, NOW());
