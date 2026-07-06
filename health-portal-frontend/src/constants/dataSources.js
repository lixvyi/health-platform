/** 合规开放数据源目录（门户展示用，非爬虫采集） */
export const OPEN_DATA_SOURCES = [
  {
    id: 'nbs',
    name: '国家统计数据库',
    org: '国家统计局',
    url: 'https://data.stats.gov.cn',
    openType: '无条件开放',
    access: '官网查询下载（Excel/CSV）',
    usage: '人口、卫生、国民经济等宏观统计指标',
    attribution: '来源：国家统计局',
    agreement: true
  },
  {
    id: 'gov-shuju',
    name: '中国政府网数据',
    org: '中国政府网',
    url: 'http://www.gov.cn/shuju/',
    openType: '无条件开放',
    access: '跳转国家统计局等指标下载',
    usage: 'CPI、GDP 等核心宏观指标入口',
    attribution: '来源：国家统计局',
    agreement: true
  },
  {
    id: 'shanghai',
    name: '上海市公共数据开放平台',
    org: '上海市人民政府',
    url: 'https://data.sh.gov.cn',
    openType: '分类开放（含无条件/有条件）',
    access: '筛选「无条件开放」后直接下载或申请 API',
    usage: '医疗机构名录、气象、药品监管等',
    attribution: '来源：上海市公共数据开放平台',
    agreement: false
  },
  {
    id: 'beijing',
    name: '北京市公共数据开放平台',
    org: '北京市人民政府',
    url: 'https://data.beijing.gov.cn',
    openType: '分类开放（含无条件/有条件）',
    access: '筛选「无条件开放」后直接下载',
    usage: '医疗机构、空气质量、统计年鉴等',
    attribution: '来源：北京市公共数据开放平台',
    agreement: false
  }
]

export const NBS_AGREEMENT_SUMMARY = {
  copyright: [
    '本数据库呈现的任何内容，无论数据、图表、商标、设计、文字和任何其他信息，未经特殊说明，其版权均属国家统计局所有。',
    '转载或引用须以新闻性或资料性公共免费信息为目的，合理、善意引用，并注明「来源：国家统计局」。',
    '不得曲解、修改原意，不得损害本网或他人利益，不得用于违法行为或破坏公共秩序的行为。'
  ],
  disclaimer: [
    '国家统计局不对因使用数据引起的直接或间接损失（含利润、收入或投资损失）承担责任。',
    '不对不可预见因素、互联网传输中断、延迟或数据错误等导致的损失承担责任。',
    '引用第三方内容不代表国家统计局观点，仅供交流与参考。'
  ],
  privacy: [
    '国家统计局可能根据需要收集注册信息以改进服务。',
    '尊重用户隐私，非经许可或法律强制性规定，不向第三方泄露个人资料。'
  ]
}
