import publicHealthImage from '../assets/article-public-health.png'
import statisticsImage from '../assets/article-statistics.png'
import medicalImage from '../assets/article-medical.png'
import pharmacyImage from '../assets/article-pharmacy.png'
import lifestyleImage from '../assets/article-lifestyle.png'
import policyImage from '../assets/article-policy.png'
import researchImage from '../assets/article-research.png'
import defaultImage from '../assets/article-default.png'

const imageRules = [
  { image: publicHealthImage, words: ['传染病', '疫情', '疾控', '公共卫生', '疫苗', '接种', '流感', '感染'] },
  { image: pharmacyImage, words: ['药品', '用药', '医保药品', '药物', '目录', '不良反应', '禁忌'] },
  { image: medicalImage, words: ['医院', '医疗', '诊疗', '医生', '基层', '妇幼', '护理', '康复'] },
  { image: lifestyleImage, words: ['饮食', '运动', '营养', '睡眠', '心理', '慢病', '高血压', '糖尿病', '养老'] },
  { image: statisticsImage, words: ['数据', '统计', '年鉴', 'GDP', 'CPI', 'PMI', '调查', '发布'] },
  { image: policyImage, words: ['政策', '通知', '办法', '规划', '标准', '解读', '公告'] },
  { image: researchImage, words: ['研究', '创新', '平台', '大数据', '科研', '产学研'] }
]

export const articleImageOf = (item = {}) => {
  if (item.coverUrl) return item.coverUrl
  const haystack = [
    item.title,
    item.summary,
    item.sourceName,
    item.author,
    item.categoryCode
  ].filter(Boolean).join(' ')
  return imageRules.find(rule => rule.words.some(word => haystack.includes(word)))?.image || defaultImage
}
