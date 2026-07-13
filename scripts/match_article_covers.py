# -*- coding: utf-8 -*-
"""Match published CMS articles with relevant openly licensed cover images."""
from __future__ import annotations

import argparse
import html
import io
import json
import re
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import pymysql
import requests
from PIL import Image, ImageOps

from crawl_policy_cms import load_db_config


ROOT = Path(__file__).resolve().parents[1]
COVER_DIR = ROOT / "health-portal-frontend" / "public" / "article-covers"
MANIFEST_PATH = COVER_DIR / "attribution.json"
OPENVERSE_API = "https://api.openverse.org/v1/images/"
ALLOWED_LICENSES = {"pdm", "cc0", "by", "by-sa"}
USER_AGENT = "HealthPlatformCoverMatcher/1.0 (CSU student health portal)"


TOPICS: list[dict[str, Any]] = [
    {
        "code": "drinking_water",
        "label": "安全饮水",
        "keywords": ["健康饮水", "饮水", "饮用水", "水日", "用水安全", "喝水", "白开水"],
        "query": "safe drinking water",
        "queries": ["safe drinking water", "drinking water health", "clean water"],
        "terms": ["drinking water", "safe water", "clean water", "water", "hydration"],
        "exclude_terms": ["flood", "ocean", "lake", "river pollution", "award", "number without"],
    },
    {
        "code": "child_nutrition",
        "label": "儿童营养与喂养",
        "keywords": ["生长迟缓", "食养", "辅食", "喂养", "食物摄入", "摄入不均衡", "学龄儿童"],
        "query": "child nutrition",
        "queries": ["healthy eating children", "children healthy food", "school children meal", "infant complementary feeding"],
        "terms": ["child nutrition", "children", "healthy food", "infant", "feeding", "nutrition"],
        "exclude_terms": ["summit", "meeting", "senator", "leader", "advocate", "executive chef", "bottle", "horn", "framework", "political economy", "grimaldi", "popcorn", "banana chips"],
    },
    {
        "code": "child_health",
        "label": "儿童健康与成长",
        "keywords": ["儿童", "孩子", "小儿", "青少年", "寒假儿童", "儿童友好"],
        "query": "child health",
        "queries": ["child health", "children doctor clinic", "healthy children activity", "children community"],
        "terms": ["child health", "children", "child", "youth", "community", "activity", "health"],
        "require_title_terms": ["child", "children", "youth"],
        "exclude_terms": ["conflict", "refugee", "military", "somalia", "violence"],
        "exclude_terms": ["maternal", "pregnancy", "senator", "conference", "meeting"],
    },
    {
        "code": "child_digital",
        "label": "儿童数字健康",
        "keywords": ["智能手表", "社交媒体", "手机化", "网络沉迷", "注意力"],
        "query": "children technology",
        "queries": ["children technology", "child smartphone", "children internet"],
        "terms": ["children", "child", "technology", "smartphone", "phone", "internet", "digital"],
        "exclude_terms": ["conference", "meeting", "representative"],
        "priority": 20,
    },
    {
        "code": "measles",
        "label": "麻疹防治",
        "keywords": ["麻疹"],
        "query": "measles vaccination",
        "terms": ["measles", "vaccination", "vaccine", "immunization"],
    },
    {
        "code": "lung_cancer",
        "label": "肺癌防治",
        "keywords": ["肺癌", "肺恶性肿瘤", "肺结节", "磨玻璃样结节"],
        "query": "lung cancer",
        "terms": ["lung cancer", "lung", "pulmonary", "cancer"],
    },
    {
        "code": "prostate_cancer",
        "label": "前列腺癌防治",
        "keywords": ["前列腺癌"],
        "query": "prostate cancer",
        "queries": ["prostate cancer screening", "prostate cancer awareness", "prostate health medical"],
        "terms": ["prostate cancer", "prostate", "cancer", "screening"],
        "exclude_terms": ["concert", "event"],
    },
    {
        "code": "liver_cancer",
        "label": "肝癌防治",
        "keywords": ["肝癌", "肝脏肿瘤"],
        "query": "liver cancer",
        "terms": ["liver cancer", "liver", "hepatic", "cancer"],
        "exclude_terms": ["gobshite"],
    },
    {
        "code": "colorectal_cancer",
        "label": "结直肠癌防治",
        "keywords": ["结直肠癌", "肠癌"],
        "query": "colorectal cancer",
        "terms": ["colorectal cancer", "colon cancer", "colorectal", "colon", "cancer"],
    },
    {
        "code": "esophageal_cancer",
        "label": "食管癌防治",
        "keywords": ["食管癌", "食道癌"],
        "query": "esophageal cancer",
        "terms": ["esophageal cancer", "esophagus", "oesophageal", "cancer"],
    },
    {
        "code": "stomach_cancer",
        "label": "胃癌防治",
        "keywords": ["胃癌"],
        "query": "stomach cancer",
        "terms": ["stomach cancer", "gastric cancer", "stomach", "gastric", "cancer"],
    },
    {
        "code": "weight_management",
        "label": "体重管理",
        "keywords": ["肥胖", "减重", "体重", "代谢密码", "平台期"],
        "query": "healthy weight obesity",
        "queries": ["healthy weight exercise", "weight management", "childhood obesity", "child weight measurement", "family healthy weight", "physical activity"],
        "terms": ["healthy weight", "obesity", "weight management", "weight loss", "fitness"],
        "exclude_terms": ["pill", "capsule", "supplement", "policy", "launch", "conference", "summit", "press event"],
        "min_ratio": 1.1,
    },
    {
        "code": "child_underweight",
        "label": "儿童营养与生长",
        "keywords": ["儿童青少年消瘦", "消瘦", "营养不良", "怎么吃都不胖", "体重出现这种情况"],
        "query": "underweight child nutrition growth",
        "queries": ["underweight child nutrition", "child growth measurement school", "pediatric growth monitoring", "healthy child nutrition"],
        "terms": ["underweight", "child", "children", "growth", "nutrition", "pediatric"],
        "require_title_terms": ["underweight", "child", "growth", "nutrition", "pediatric"],
        "exclude_terms": ["conference", "meeting", "report"],
        "priority": 9,
        "min_ratio": 1.1,
    },
    {
        "code": "smoking_cessation",
        "label": "戒烟与烟草危害",
        "keywords": ["戒烟", "吸烟", "烟草", "控烟"],
        "query": "stop smoking health",
        "queries": ["stop smoking health", "smoking cessation", "tobacco health"],
        "terms": ["stop smoking", "smoking cessation", "tobacco", "cigarette", "smoking", "health"],
    },
    {
        "code": "autism",
        "label": "孤独症关爱",
        "keywords": ["孤独症", "自闭症"],
        "query": "autism child support",
        "queries": ["autism child support", "autism therapy child", "autism awareness family", "autistic child education"],
        "terms": ["autism", "autistic", "child", "support", "therapy"],
        "exclude_terms": ["proclamation", "conference", "fundraiser"],
    },
    {
        "code": "medicine_guide",
        "label": "药品说明与合理用药",
        "keywords": ["说明书", "阿莫西林", "布洛芬", "奥美拉唑", "头孢"],
        "query": "medicine pharmacy",
        "terms": ["medicine", "pharmacy", "medication", "capsule", "pills", "drug"],
    },
    {
        "code": "epidemic",
        "label": "传染病与疫情",
        "keywords": ["传染病", "疫情", "感染", "病毒", "流感", "肺结核", "肝炎", "艾滋病", "霍乱", "梅毒"],
        "query": "virus laboratory",
        "queries": ["virus laboratory", "infectious disease", "microbiology virus"],
        "terms": ["infectious", "disease", "infection", "virus", "epidemic", "laboratory", "public health", "cdc"],
    },
    {
        "code": "vaccination",
        "label": "疫苗接种",
        "keywords": ["疫苗", "接种", "免疫规划", "预防接种"],
        "query": "vaccination nurse",
        "queries": ["vaccination nurse", "vaccine injection", "child vaccination"],
        "terms": ["vaccination", "vaccine", "immunization", "syringe", "public health"],
        "require_title_terms": ["vaccin", "vaccine", "immunization", "injection"],
        "exclude_terms": ["schedule", "map", "chart", "lego", "toy", "toxic waste", "stop vaccination", "anti vaccine", "anti-vaccine", "protest"],
        "priority": 20,
    },
    {
        "code": "cancer",
        "label": "癌症防治",
        "keywords": ["癌症", "癌细胞", "肿瘤", "肺癌", "肝癌", "胃癌", "乳腺癌", "结直肠癌", "食管癌", "前列腺癌"],
        "query": "cancer cells",
        "queries": ["cancer cells", "cancer prevention", "cancer screening"],
        "terms": ["cancer", "oncology", "tumor", "screening", "medical"],
    },
    {
        "code": "cardiovascular",
        "label": "心脑血管健康",
        "keywords": ["心血管", "脑血管", "心脏", "卒中", "中风", "高血压", "冠心病", "血压"],
        "query": "heart health",
        "queries": ["blood pressure health", "hypertension prevention", "heart healthy lifestyle", "cardiovascular health"],
        "terms": ["cardiovascular", "heart", "blood pressure", "hypertension", "medical"],
    },
    {
        "code": "diabetes",
        "label": "糖尿病与代谢健康",
        "keywords": ["糖尿病", "血糖", "胰岛素", "代谢", "肥胖"],
        "query": "diabetes health",
        "terms": ["diabetes", "glucose", "insulin", "blood sugar", "health"],
    },
    {
        "code": "mental_health",
        "label": "心理健康",
        "keywords": ["心理", "精神卫生", "抑郁", "焦虑", "孤独症", "压力", "睡眠"],
        "query": "mental health",
        "terms": ["mental health", "wellbeing", "psychology", "stress", "healthcare"],
    },
    {
        "code": "nutrition",
        "label": "营养与饮食",
        "keywords": ["营养", "饮食", "膳食", "食品", "减盐", "控糖", "食物"],
        "query": "healthy nutrition",
        "terms": ["nutrition", "healthy food", "diet", "fruit", "vegetable", "public health"],
    },
    {
        "code": "exercise",
        "label": "运动健康",
        "keywords": ["运动", "健身", "锻炼", "体重", "体质", "健步"],
        "query": "physical activity",
        "terms": ["exercise", "physical activity", "fitness", "health", "sport"],
    },
    {
        "code": "maternal_child",
        "label": "妇幼健康",
        "keywords": ["妇幼", "孕产妇", "孕妇", "生育", "母婴", "婴幼儿", "新生儿"],
        "query": "maternal health",
        "terms": ["maternal", "child health", "mother", "baby", "pediatric", "healthcare"],
    },
    {
        "code": "elderly",
        "label": "老年健康",
        "keywords": ["老年", "老人", "养老", "衰老", "阿尔茨海默", "认知障碍"],
        "query": "healthy aging",
        "terms": ["healthy aging", "elderly", "senior", "older people", "healthcare"],
    },
    {
        "code": "medicine",
        "label": "药品与合理用药",
        "keywords": ["药品", "药物", "用药", "处方", "医保药品", "药监"],
        "query": "medicine pharmacy",
        "terms": ["medicine", "pharmacy", "medication", "drug", "healthcare"],
    },
    {
        "code": "hospital",
        "label": "医疗服务",
        "keywords": ["医院", "医生", "护士", "诊疗", "医疗服务", "基层医疗", "卫生院", "门诊", "急诊"],
        "query": "hospital healthcare",
        "terms": ["hospital", "medical care", "doctor", "nurse", "healthcare"],
    },
    {
        "code": "health_security",
        "label": "医疗数据安全",
        "keywords": ["等保", "网络安全", "数据安全", "信息安全", "隐私保护", "安全测评"],
        "query": "healthcare cybersecurity",
        "terms": ["healthcare", "cybersecurity", "cyber", "data security", "privacy", "medical device"],
    },
    {
        "code": "health_data",
        "label": "健康数据与统计",
        "keywords": ["健康数据", "医疗数据", "卫生统计", "数据平台", "数据资源", "大数据", "统计年鉴", "统计调查"],
        "query": "health data visualization",
        "queries": ["health data visualization", "electronic health record"],
        "terms": ["health data", "medical data", "visualization", "graph", "analytics", "electronic health", "data"],
        "exclude_terms": ["meetup", "speech", "speaker", "conference", "summit", "senator", "representatives"],
    },
    {
        "code": "research",
        "label": "医学科研与创新",
        "keywords": ["医学研究", "科研", "创新", "实验室", "人工智能", "产学研", "研究项目", "科技"],
        "query": "medical research",
        "queries": ["medical research", "medical laboratory", "healthcare innovation"],
        "terms": ["medical research", "laboratory", "science", "healthcare", "innovation"],
    },
    {
        "code": "pregnancy_health",
        "label": "孕产健康",
        "keywords": ["孕期", "孕妇", "怀孕", "妈妈孕期"],
        "query": "healthy pregnancy exercise",
        "queries": ["healthy pregnancy exercise", "pregnant woman walking", "pregnancy healthcare"],
        "terms": ["pregnancy", "pregnant", "maternal", "mother", "exercise", "healthcare"],
        "require_title_terms": ["pregnan", "maternal", "mother"],
        "exclude_terms": ["event", "conference", "report", "committee", "launch"],
        "priority": 8,
    },
    {
        "code": "health_misinformation",
        "label": "健康信息辨伪",
        "keywords": ["清淤术", "所谓“高科技手段”", "医疗骗局", "健康谣言"],
        "query": "health misinformation online",
        "queries": ["health misinformation", "medical misinformation online", "fact checking laptop", "online fact checking"],
        "terms": ["misinformation", "medical", "health", "fact check", "fact checking", "online", "laptop"],
        "require_title_terms": ["misinformation", "fact check", "fact-check", "online"],
        "priority": 8,
    },
    {
        "code": "holiday_health",
        "label": "节假日健康",
        "keywords": ["五一假期", "假期健康", "健康提示"],
        "query": "people walking park outdoors",
        "queries": ["people walking park", "spring park outdoors", "family park picnic"],
        "terms": ["family", "outdoors", "walking", "park", "picnic", "spring"],
        "require_title_terms": ["family", "outdoor", "walking", "park", "picnic", "spring"],
        "exclude_terms": ["grand canyon", "campground", "parking"],
        "priority": 5,
    },
    {
        "code": "spring_festival_health",
        "label": "春节健康",
        "keywords": ["春节健康", "春节"],
        "query": "lunar new year family",
        "queries": ["lunar new year family", "Chinese new year family", "new year healthy meal"],
        "terms": ["lunar new year", "Chinese new year", "new year", "family", "healthy meal"],
        "require_title_terms": ["new year", "lunar", "family"],
        "priority": 8,
    },
    {
        "code": "altitude_health",
        "label": "高原健康",
        "keywords": ["高原", "高原病", "心肺脑疾病"],
        "query": "high altitude health mountain",
        "queries": ["high altitude health", "mountain altitude medicine", "high altitude breathing"],
        "terms": ["high altitude", "altitude", "mountain", "breathing", "health"],
        "require_title_terms": ["high altitude", "mountain", "altitude sickness"],
        "exclude_terms": ["low altitude", "plane", "aircraft", "museum"],
        "priority": 5,
    },
    {
        "code": "child_welfare",
        "label": "儿童关爱",
        "keywords": ["儿童友好", "困境儿童", "儿童关爱", "关爱服务"],
        "query": "children community support",
        "queries": ["children playground community", "children library community", "children family care", "children playing park", "child welfare family"],
        "terms": ["children", "child", "family", "community", "support", "playing"],
        "require_title_terms": ["children", "child", "family"],
        "exclude_terms": ["vintage", "operation", "sacrifice", "refugee", "conflict", "flood", "disaster", "war"],
        "priority": 5,
    },
    {
        "code": "swimming_health",
        "label": "游泳健康",
        "keywords": ["游泳", "碧波"],
        "query": "swimming pool health",
        "queries": ["swimming pool exercise", "swimming safety", "healthy swimming"],
        "terms": ["swimming", "swimmer", "pool", "exercise", "water safety"],
        "priority": 5,
    },
    {
        "code": "women_health",
        "label": "女性健康",
        "keywords": ["妇女健康", "女性健康", "女性肌肉", "肌肉，女性"],
        "query": "women health fitness",
        "queries": ["women health fitness", "women strength exercise", "women healthcare"],
        "terms": ["women", "woman", "female", "fitness", "strength", "health"],
        "priority": 5,
    },
    {
        "code": "school_health",
        "label": "学校健康",
        "keywords": ["健康学校", "新学期", "健康第一", "冬奥故事", "校长"],
        "query": "healthy students school activity",
        "queries": ["healthy students school", "students physical activity", "children school playground", "school health"],
        "terms": ["school", "student", "students", "children", "activity", "playground", "health"],
        "require_title_terms": ["school", "student", "children", "playground"],
        "exclude_terms": ["building", "construction", "architecture", "stem night", "scientist", "engineer"],
        "priority": 5,
    },
    {
        "code": "traditional_medicine",
        "label": "中医养生",
        "keywords": ["中医", "养生", "立夏"],
        "query": "traditional Chinese medicine herbs",
        "queries": ["traditional Chinese medicine", "Chinese medicine herbs", "acupuncture wellness"],
        "terms": ["Chinese medicine", "traditional medicine", "herbs", "acupuncture", "wellness"],
        "require_title_terms": ["medicine", "herb", "acupuncture"],
        "exclude_terms": ["phone", "cell phone", "makeup", "school"],
        "priority": 5,
    },
    {
        "code": "movement_exercise",
        "label": "科学运动",
        "keywords": ["4个动作", "四个动作", "每小时动一动"],
        "query": "simple home exercise movement",
        "queries": ["home exercise movement", "stretching exercise", "office exercise break"],
        "terms": ["exercise", "stretching", "movement", "fitness", "workout"],
        "require_title_terms": ["exercise", "stretch", "fitness", "workout"],
        "priority": 5,
    },
    {
        "code": "eye_care",
        "label": "眼健康",
        "keywords": ["用眼", "20-20-20", "视力", "眼健康"],
        "query": "eye health screen break",
        "queries": ["eye health screen", "computer eye strain", "eye examination"],
        "terms": ["eye", "eyes", "vision", "screen", "computer", "examination"],
        "priority": 5,
    },
    {
        "code": "sedentary_health",
        "label": "久坐防护",
        "keywords": ["久坐", "每小时动一动"],
        "query": "office stretching break",
        "queries": ["office stretching break", "desk exercise", "workplace physical activity"],
        "terms": ["office", "desk", "stretching", "exercise", "workplace", "activity"],
        "require_title_terms": ["office", "desk", "stretch", "exercise", "workplace"],
        "exclude_terms": ["emergency", "preparedness", "drill"],
        "priority": 6,
    },
    {
        "code": "sleep_health",
        "label": "睡眠健康",
        "keywords": ["健康作息", "起床时间", "补觉", "睡眠健康"],
        "query": "healthy sleep bedroom morning",
        "queries": ["healthy sleep", "sleep routine", "morning wake up"],
        "terms": ["sleep", "sleeping", "bedroom", "morning", "wake", "rest"],
        "require_title_terms": ["sleep", "bedroom", "morning", "wake", "rest"],
        "priority": 5,
    },
    {
        "code": "radiation_safety",
        "label": "核与辐射防护",
        "keywords": ["核和辐射", "核事故", "辐射防护"],
        "query": "radiation safety protection",
        "queries": ["radiation safety", "radiation protection", "nuclear emergency safety"],
        "terms": ["radiation", "nuclear", "safety", "protection", "emergency"],
        "exclude_terms": ["bomb", "weapon", "missile"],
        "priority": 6,
    },
    {
        "code": "home_disinfection",
        "label": "家庭清洁消毒",
        "keywords": ["家庭消毒", "家庭环境以清洁为主", "冬季呼吸道疾病"],
        "query": "home surface cleaning hygiene",
        "queries": ["home surface cleaning", "disinfecting home", "household cleaning hygiene", "cleaning kitchen surface"],
        "terms": ["home", "household", "cleaning", "disinfecting", "hygiene", "surface", "kitchen"],
        "require_title_terms": ["clean", "disinfect", "hygiene"],
        "exclude_terms": ["flood", "disaster", "woman's work", "advertisement"],
        "priority": 8,
    },
    {
        "code": "disinfection",
        "label": "环境消毒",
        "keywords": ["消毒", "个人去污", "洪涝灾区", "地震灾区"],
        "query": "public health disinfection cleaning",
        "queries": ["public health disinfection", "home surface cleaning hygiene", "flood cleanup sanitation", "flood cleaning", "disaster cleanup hygiene", "cleaning disinfecting"],
        "terms": ["disinfection", "disinfecting", "cleaning", "hygiene", "cleanup", "sanitation", "flood"],
        "require_title_terms": ["disinfect", "clean", "hygiene", "sanitation", "flood"],
        "exclude_terms": ["woman's work", "advertisement"],
        "priority": 6,
    },
    {
        "code": "air_quality",
        "label": "空气污染防护",
        "keywords": ["雾霾", "沙尘", "空气污染", "空气质量"],
        "query": "air pollution health mask",
        "queries": ["air pollution health", "smog mask", "dust storm health", "air quality city"],
        "terms": ["air pollution", "smog", "air quality", "dust", "mask", "pollution"],
        "priority": 6,
    },
    {
        "code": "pollen_allergy",
        "label": "花粉过敏防护",
        "keywords": ["柳絮", "花粉", "飞絮", "春季过敏"],
        "query": "pollen allergy spring",
        "queries": ["pollen allergy", "spring allergy", "hay fever pollen", "airborne pollen"],
        "terms": ["pollen", "allergy", "allergies", "hay fever", "spring", "flower"],
        "priority": 7,
    },
    {
        "code": "plague_prevention",
        "label": "鼠疫防治",
        "keywords": ["鼠疫"],
        "query": "plague bacteria public health",
        "queries": ["plague bacteria", "plague prevention public health", "yersinia pestis laboratory"],
        "terms": ["plague", "yersinia", "bacteria", "public health", "laboratory"],
        "priority": 7,
    },
    {
        "code": "asthma",
        "label": "哮喘防治",
        "keywords": ["哮喘"],
        "query": "asthma inhaler health",
        "queries": ["asthma inhaler", "asthma breathing", "asthma patient"],
        "terms": ["asthma", "inhaler", "breathing", "lungs", "respiratory"],
        "priority": 7,
    },
    {
        "code": "back_pain",
        "label": "腰背健康",
        "keywords": ["腰痛", "腰背痛"],
        "query": "lower back pain health",
        "queries": ["lower back pain", "physiotherapy back pain", "lower back stretching", "spine healthcare"],
        "terms": ["back pain", "lower back", "spine", "back", "exercise"],
        "require_title_terms": ["back pain", "lower back", "spine"],
        "exclude_terms": ["march", "event"],
        "priority": 7,
    },
    {
        "code": "gastritis",
        "label": "胃部健康",
        "keywords": ["胃炎"],
        "query": "gastritis stomach health",
        "queries": ["gastritis stomach", "stomach pain health", "digestive health"],
        "terms": ["gastritis", "stomach", "digestive", "abdomen", "health"],
        "priority": 7,
    },
    {
        "code": "typhoid",
        "label": "伤寒防治",
        "keywords": ["伤寒"],
        "query": "typhoid fever bacteria",
        "queries": ["typhoid fever", "salmonella typhi", "typhoid prevention"],
        "terms": ["typhoid", "salmonella", "bacteria", "fever", "prevention"],
        "priority": 7,
    },
    {
        "code": "heat_safety",
        "label": "防暑健康",
        "keywords": ["防暑", "高温", "中暑", "正午暴晒"],
        "query": "summer heat hydration shade",
        "queries": ["summer heat hydration", "hot weather drinking water", "heatwave hydration", "summer shade water"],
        "terms": ["summer", "heat", "hydration", "water", "hot weather", "shade"],
        "require_title_terms": ["summer", "heat", "hydration", "hot weather", "drinking water"],
        "exclude_terms": ["shades of", "color", "paint"],
        "priority": 6,
    },
    {
        "code": "public_health",
        "label": "公共卫生",
        "keywords": ["公共卫生", "卫生健康", "疾病预防", "健康促进", "健康中国", "健康素养", "疾控"],
        "query": "public health",
        "terms": ["public health", "community health", "prevention", "healthcare", "health"],
    },
]

NEGATIVE_IMAGE_TERMS = {
    "logo", "icon", "clipart", "template", "wallpaper", "poster", "flyer",
    "screenshot", "book cover", "map", "flag", "stamp", "portrait",
    "meeting", "conference", "workshop", "delegation", "mission", "opens", "plan",
}

GLOBAL_EXCLUDE_IMAGE_TERMS = {
    "animal", "bear", "wildlife", "zoo", "president", "fashion show",
    "coca cola", "diet coke", "advertisement",
    "military", "weapon", "combat",
}

SPECIFIC_IMAGE_TERMS = {
    "ebola": ["埃博拉"],
    "malaria": ["疟疾"],
    "polio": ["脊髓灰质炎", "小儿麻痹"],
    "tuberculosis": ["肺结核", "结核病"],
    "syphilis": ["梅毒"],
    "hiv": ["艾滋病", "HIV"],
    "aids": ["艾滋病", "AIDS"],
    "coronavirus": ["冠状病毒", "新冠"],
    "sars-cov-2": ["冠状病毒", "新冠"],
    "covid": ["冠状病毒", "新冠"],
    "lung cancer": ["肺癌", "肺恶性肿瘤", "肺结节"],
    "prostate cancer": ["前列腺癌"],
    "liver cancer": ["肝癌", "肝脏肿瘤"],
    "colorectal": ["结直肠癌", "肠癌"],
    "colon cancer": ["结直肠癌", "肠癌"],
    "cervical cancer": ["宫颈癌"],
    "oral cancer": ["口腔癌"],
    "esophageal cancer": ["食管癌", "食道癌"],
    "oesophageal cancer": ["食管癌", "食道癌"],
    "breast cancer": ["乳腺癌"],
    "stomach cancer": ["胃癌"],
    "gastric cancer": ["胃癌"],
}


def clean_text(value: str | None) -> str:
    text = html.unescape(value or "")
    text = re.sub(r"<[^>]+>", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def detect_topic(article: dict[str, Any], minimum_score: int) -> tuple[dict[str, Any] | None, int, list[str]]:
    title = clean_text(article.get("title"))
    summary = clean_text(article.get("summary"))
    content = clean_text(article.get("content"))[:1800]
    ranked = []
    for topic in TOPICS:
        matched = []
        headline_matches = 0
        title_weight = 0
        score = 0
        for keyword in topic["keywords"]:
            if keyword in title:
                score += 5
                headline_matches += 1
                title_weight += len(keyword)
                matched.append(keyword)
            elif keyword in summary:
                score += 2
                headline_matches += 1
                matched.append(keyword)
            elif keyword in content:
                score += 1
                matched.append(keyword)
        ranked.append((score, headline_matches, title_weight, topic, list(dict.fromkeys(matched))))
    title_ranked = [row for row in ranked if row[2] > 0]
    if title_ranked:
        score, headline_matches, title_weight, topic, matched = max(
            title_ranked,
            key=lambda row: (row[3].get("priority", 0), row[2], -len(row[3]["keywords"]), row[1]),
        )
    else:
        score, headline_matches, title_weight, topic, matched = max(ranked, key=lambda row: row[0])
    if score < minimum_score or headline_matches == 0:
        return None, score, matched
    return topic, score, matched


def search_openverse(session: requests.Session, topic: dict[str, Any], page_size: int = 20) -> list[dict[str, Any]]:
    results = []
    seen = set()
    last_error: requests.RequestException | None = None
    for query in topic.get("queries", [topic["query"]]):
        try:
            response = session.get(
                OPENVERSE_API,
                params={
                    "q": query,
                    "license": "pdm,cc0,by,by-sa",
                    "aspect_ratio": "wide",
                    "mature": "false",
                    "page_size": page_size,
                },
                timeout=30,
            )
            response.raise_for_status()
        except requests.RequestException as exc:
            last_error = exc
            print(f"WARN Openverse query skipped: {query}: {exc}", flush=True)
            continue
        for candidate in response.json().get("results", []):
            if candidate.get("id") in seen:
                continue
            seen.add(candidate.get("id"))
            results.append(candidate)
    if not results and last_error:
        raise last_error
    return results


def candidate_fingerprint(candidate: dict[str, Any]) -> str:
    title = re.sub(r"[\W\d_]+", "", (candidate.get("title") or "").lower())
    creator = re.sub(r"[\W\d_]+", "", (candidate.get("creator") or "").lower())
    return f"{title}|{creator}"


def image_dhash(image: Image.Image, hash_size: int = 8) -> str:
    gray = ImageOps.grayscale(image).resize((hash_size + 1, hash_size), Image.Resampling.LANCZOS)
    pixels = list(gray.get_flattened_data())
    value = 0
    for row in range(hash_size):
        offset = row * (hash_size + 1)
        for column in range(hash_size):
            value = (value << 1) | int(pixels[offset + column] > pixels[offset + column + 1])
    return f"{value:0{hash_size * hash_size // 4}x}"


def hash_distance(first: str, second: str) -> int:
    return (int(first, 16) ^ int(second, 16)).bit_count()


def wikimedia_thumbnail_url(image_url: str) -> str | None:
    marker = "/wikipedia/commons/"
    if "upload.wikimedia.org" not in image_url or marker not in image_url:
        return None
    prefix, path = image_url.split(marker, 1)
    filename = path.rsplit("/", 1)[-1]
    if not filename or filename.lower().endswith((".svg", ".tif", ".tiff")):
        return None
    return f"{prefix}{marker}thumb/{path}/960px-{filename}"


def candidate_score(
    candidate: dict[str, Any],
    topic: dict[str, Any],
    article: dict[str, Any],
    used_ids: set[str],
    used_fingerprints: set[str],
) -> float:
    if (
        candidate.get("id") in used_ids
        or candidate_fingerprint(candidate) in used_fingerprints
        or candidate.get("mature")
    ):
        return -1000
    license_code = (candidate.get("license") or "").lower()
    if license_code not in ALLOWED_LICENSES:
        return -1000
    image_url = (candidate.get("url") or "").lower().split("?", 1)[0]
    if image_url.endswith(".svg") or (candidate.get("filetype") or "").lower() == "svg":
        return -1000
    width = int(candidate.get("width") or 0)
    height = int(candidate.get("height") or 0)
    if width < 700 or height < 350 or not height:
        return -1000
    ratio = width / height
    if ratio < topic.get("min_ratio", 1.25) or ratio > 2.8:
        return -1000

    title = (candidate.get("title") or "").lower()
    tags = " ".join(str(tag.get("name", "")) for tag in candidate.get("tags", []))[:3000].lower()
    haystack = f"{title} {tags}"
    if any(term in haystack for term in GLOBAL_EXCLUDE_IMAGE_TERMS):
        return -1000
    required_title_terms = topic.get("require_title_terms", [])
    if required_title_terms and not any(term in title for term in required_title_terms):
        return -1000
    if any(term in haystack for term in topic.get("exclude_terms", [])):
        return -1000
    article_text = " ".join(
        clean_text(article.get(field)) for field in ("title", "summary", "content")
    ).lower()
    for image_term, article_terms in SPECIFIC_IMAGE_TERMS.items():
        if image_term in haystack and not any(term.lower() in article_text for term in article_terms):
            return -1000
    term_hits = sum(1 for term in topic["terms"] if term in haystack)
    if term_hits == 0:
        return -1000

    score = term_hits * 5
    score += sum(4 for term in topic["terms"] if term in title)
    score += 3 if license_code in {"pdm", "cc0"} else 1
    score += 3 if 1.45 <= ratio <= 2.1 else 1
    score += 2 if width >= 1200 else 0
    score -= sum(5 for term in NEGATIVE_IMAGE_TERMS if term in haystack)
    if candidate.get("source") == "wikimedia":
        score += 2
    return score


def ranked_candidates(
    candidates: list[dict[str, Any]],
    topic: dict[str, Any],
    article: dict[str, Any],
    used_ids: set[str],
    used_fingerprints: set[str],
) -> list[dict[str, Any]]:
    ranked = [(candidate_score(item, topic, article, used_ids, used_fingerprints), item) for item in candidates]
    return [item for score, item in sorted(ranked, key=lambda row: row[0], reverse=True) if score >= 8]


def download_cover(
    session: requests.Session,
    candidate: dict[str, Any],
    destination: Path,
    used_image_hashes: set[str] | None = None,
) -> str:
    errors = []
    original_url = candidate.get("url") or ""
    urls = list(
        dict.fromkeys(
            filter(
                None,
                [wikimedia_thumbnail_url(original_url), original_url, candidate.get("thumbnail")],
            )
        )
    )
    for image_url in urls:
        try:
            with session.get(image_url, timeout=45, stream=True) as response:
                response.raise_for_status()
                payload = bytearray()
                for chunk in response.iter_content(64 * 1024):
                    payload.extend(chunk)
                    if len(payload) > 20 * 1024 * 1024:
                        raise ValueError("image exceeds 20 MB")
            with Image.open(io.BytesIO(payload)) as source:
                if source.width < 700 or source.height < 350:
                    raise ValueError("downloaded image is too small")
                if source.width * source.height > 40_000_000:
                    raise ValueError("image exceeds 40 megapixels")
                oriented = ImageOps.exif_transpose(source)
                image = oriented.convert("RGB")
                fitted = ImageOps.fit(image, (1200, 675), method=Image.Resampling.LANCZOS)
                perceptual_hash = image_dhash(fitted)
                if used_image_hashes and any(
                    hash_distance(perceptual_hash, existing_hash) <= 6
                    for existing_hash in used_image_hashes
                ):
                    raise ValueError("image is visually too similar to an existing cover")
                destination.parent.mkdir(parents=True, exist_ok=True)
                fitted.save(destination, "WEBP", quality=84, method=6)
                fitted.close()
                image.close()
                if oriented is not source:
                    oriented.close()
            return perceptual_hash
        except (OSError, ValueError, requests.RequestException) as exc:
            errors.append(str(exc))
    raise ValueError("; ".join(errors) or "no downloadable image URL")


def load_manifest() -> dict[str, Any]:
    if not MANIFEST_PATH.exists():
        return {"generatedAt": None, "items": {}}
    try:
        data = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
        data.setdefault("items", {})
        return data
    except (OSError, json.JSONDecodeError):
        return {"generatedAt": None, "items": {}}


def save_manifest(manifest: dict[str, Any]) -> None:
    manifest["generatedAt"] = datetime.now(timezone.utc).isoformat()
    COVER_DIR.mkdir(parents=True, exist_ok=True)
    temporary = MANIFEST_PATH.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(MANIFEST_PATH)


def restore_cover_files(
    session: requests.Session,
    manifest: dict[str, Any],
    article_ids: list[int] | None,
    delay: float,
) -> int:
    selected_ids = set(article_ids or [])
    restored = 0
    existing = 0
    failed: list[str] = []

    for article_id, entry in manifest["items"].items():
        if selected_ids and int(article_id) not in selected_ids:
            continue
        destination = COVER_DIR / f"{article_id}.webp"
        if destination.exists():
            existing += 1
            continue
        openverse_id = entry.get("openverseId")
        if not openverse_id:
            failed.append(article_id)
            print(f"FAIL id={article_id} missing Openverse ID", flush=True)
            continue
        try:
            response = session.get(f"{OPENVERSE_API}{openverse_id}/", timeout=30)
            response.raise_for_status()
            download_cover(session, response.json(), destination)
            restored += 1
            print(f"RESTORED id={article_id} file={destination.name}", flush=True)
        except (OSError, ValueError, requests.RequestException) as exc:
            failed.append(article_id)
            print(f"FAIL id={article_id} restore error: {exc}", flush=True)
        time.sleep(max(delay, 0))

    print(
        f"RESTORE DONE restored={restored} existing={existing} failed={len(failed)}"
        + (f" failed_ids={','.join(failed)}" if failed else ""),
        flush=True,
    )
    return 1 if failed else 0


def fetch_articles(cursor, args: argparse.Namespace) -> list[dict[str, Any]]:
    conditions = ["category_code=%s", "status=1"]
    params: list[Any] = [args.category]
    if args.ids:
        conditions.append(f"id IN ({','.join(['%s'] * len(args.ids))})")
        params.extend(args.ids)
    if not args.overwrite:
        conditions.append("(cover_url IS NULL OR cover_url='')")
    query = f"""
        SELECT id, title, summary, content, cover_url
        FROM cms_content
        WHERE {' AND '.join(conditions)}
        ORDER BY publish_time DESC, id DESC
        LIMIT %s
    """
    params.append(args.limit)
    cursor.execute(query, params)
    columns = [column[0] for column in cursor.description]
    return [dict(zip(columns, row)) for row in cursor.fetchall()]


def attribution_entry(
    article: dict[str, Any],
    topic: dict[str, Any],
    matched: list[str],
    candidate: dict[str, Any],
    perceptual_hash: str | None = None,
) -> dict[str, Any]:
    return {
        "coverUrl": f"/article-covers/{article['id']}.webp",
        "articleTitle": article["title"],
        "topic": topic["label"],
        "matchedKeywords": matched,
        "imageTitle": candidate.get("title") or "",
        "creator": candidate.get("creator") or "",
        "license": (candidate.get("license") or "").upper(),
        "licenseUrl": candidate.get("license_url") or "",
        "sourceUrl": candidate.get("foreign_landing_url") or candidate.get("url") or "",
        "provider": candidate.get("source") or candidate.get("provider") or "Openverse",
        "openverseId": candidate.get("id") or "",
        "perceptualHash": perceptual_hash or "",
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Match CMS articles with relevant open-license cover images")
    parser.add_argument("--apply", action="store_true", help="download files and update cms_content.cover_url")
    parser.add_argument("--overwrite", action="store_true", help="replace existing cover URLs")
    parser.add_argument(
        "--restore-files",
        action="store_true",
        help="restore missing local image files from attribution.json without changing matches",
    )
    parser.add_argument("--category", default="NEWS")
    parser.add_argument("--limit", type=int, default=12)
    parser.add_argument("--ids", type=int, nargs="*")
    parser.add_argument("--min-topic-score", type=int, default=4)
    parser.add_argument("--delay", type=float, default=0.35)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    manifest = load_manifest()
    session = requests.Session()
    session.headers.update({"User-Agent": USER_AGENT, "Accept": "application/json,image/*"})
    if args.restore_files:
        return restore_cover_files(session, manifest, args.ids, args.delay)

    connection = pymysql.connect(**load_db_config(), charset="utf8mb4")
    cursor = connection.cursor()
    used_ids = {
        item.get("openverseId") for item in manifest["items"].values() if item.get("openverseId")
    }
    used_fingerprints = set()
    used_image_hashes: set[str] = set()
    for item in manifest["items"].values():
        if not item.get("imageTitle"):
            continue
        used_fingerprints.add(
            candidate_fingerprint({"title": item.get("imageTitle"), "creator": item.get("creator")})
        )
    for article_id, item in manifest["items"].items():
        perceptual_hash = item.get("perceptualHash")
        if not perceptual_hash:
            destination = COVER_DIR / f"{article_id}.webp"
            try:
                with Image.open(destination) as existing_image:
                    perceptual_hash = image_dhash(existing_image)
                item["perceptualHash"] = perceptual_hash
            except OSError:
                continue
        used_image_hashes.add(perceptual_hash)
    search_cache: dict[str, list[dict[str, Any]]] = {}
    matched_count = 0
    skipped_count = 0

    try:
        articles = fetch_articles(cursor, args)
        print(f"MODE={'APPLY' if args.apply else 'DRY-RUN'} articles={len(articles)} category={args.category}", flush=True)
        for article in articles:
            topic, topic_score, matched = detect_topic(article, args.min_topic_score)
            if not topic:
                print(f"SKIP id={article['id']} no strict topic match: {article['title']}")
                skipped_count += 1
                continue
            try:
                if topic["code"] not in search_cache:
                    search_cache[topic["code"]] = search_openverse(session, topic)
                candidates = search_cache[topic["code"]]
                ranked = ranked_candidates(candidates, topic, article, used_ids, used_fingerprints)
            except requests.RequestException as exc:
                print(f"FAIL id={article['id']} search error: {exc}")
                skipped_count += 1
                continue
            if not ranked:
                print(f"SKIP id={article['id']} no licensed relevant image: {article['title']}")
                skipped_count += 1
                continue

            selected = None
            selected_hash = None
            if args.apply:
                destination = COVER_DIR / f"{article['id']}.webp"
                for candidate in ranked[:5]:
                    try:
                        selected_hash = download_cover(
                            session, candidate, destination, used_image_hashes
                        )
                        selected = candidate
                        break
                    except (OSError, ValueError, requests.RequestException) as exc:
                        print(f"RETRY id={article['id']} image={candidate.get('id')}: {exc}")
                if not selected:
                    skipped_count += 1
                    continue
            else:
                selected = ranked[0]

            entry = attribution_entry(article, topic, matched, selected, selected_hash)
            print(
                f"MATCH id={article['id']} topic={topic['label']} keywords={','.join(matched)} "
                f"image={entry['imageTitle']} license={entry['license']}"
            )
            if args.apply:
                cursor.execute(
                    "UPDATE cms_content SET cover_url=%s, updated_at=NOW() WHERE id=%s",
                    (entry["coverUrl"], article["id"]),
                )
                manifest["items"][str(article["id"])] = entry
                connection.commit()
                save_manifest(manifest)
            used_ids.add(selected.get("id"))
            used_fingerprints.add(candidate_fingerprint(selected))
            if selected_hash:
                used_image_hashes.add(selected_hash)
            matched_count += 1
            time.sleep(max(args.delay, 0))

        if args.apply:
            connection.commit()
        else:
            connection.rollback()
        print(f"DONE matched={matched_count} skipped={skipped_count}")
        return 0
    except Exception:
        connection.rollback()
        raise
    finally:
        cursor.close()
        connection.close()


if __name__ == "__main__":
    raise SystemExit(main())
