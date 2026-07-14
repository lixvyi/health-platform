const data = require('./public/hotwords_data.json');

// Test top keywords by total frequency
const words = ['疫情', '肺炎', '新冠', '防控', '医疗', '服务', '管理', '保障'];
words.forEach(k => {
    if (data[k]) {
        const total = data[k].reduce((s, d) => s + d.value, 0);
        console.log(k + ' sum:', total.toFixed(4));
    }
});

// Test river chart category aggregation for 2020
const categories = {
    'A传染病防控': ['疫情', '肺炎', '新冠', '防控', '感染', '核酸', '检测', '消毒', '防护', '联防'],
    'B医疗服务': ['医疗', '服务', '诊疗', '临床', '护理', '医务人员', '技术', '指南', '医院']
};

let grand = 0;
for (const [cat, words] of Object.entries(categories)) {
    let s = 0;
    words.forEach(w => {
        if (data[w]) {
            const item = data[w].find(d => d.year === 2020);
            if (item) s += item.value;
        }
    });
    grand += s;
    console.log(cat + ' 2020 sum:', s.toFixed(4), 'pct:', ((s / 5) * 100).toFixed(2)); // approximate
}
console.log('grand total 2020:', grand.toFixed(4));

// All categories from the map
const allCategories = {
    '传染病防控': ['疫情', '肺炎', '新冠', '防控', '感染', '核酸', '检测', '消毒', '防护', '联防'],
    '医疗服务': ['医疗', '服务', '诊疗', '临床', '护理', '医务人员', '技术', '指南', '医院'],
    '公共卫生': ['公共卫生', '爱国卫生', '防治', '食品', '慢性病', '人群', '物质'],
    '药品管理': ['药品', '医药', '用药', '购销', '保障'],
    '机构管理': ['医疗机构', '机构', '基层', '部门', '行政部门', '人员', '专业'],
    '政策法规': ['国家', '办公厅', '意见', '方案', '规范', '标准', '指标体系', '修订', '指导', '解读'],
    '体系建设': ['医疗卫生', '改革', '强化', '完善', '建设', '质量', '发展', '高质量', '应用'],
    '管理机制': ['管理', '机制', '评估', '推进', '行动', '项目', '组织', '专项', '纠风'],
    '重点人群': ['患者', '老年人', '儿童', '照护', '县域', '城市']
};

// Show river data for all years
const years = [2019, 2020, 2021, 2022, 2023, 2024, 2025, 2026];
for (const y of years) {
    let yGrand = 0;
    for (const [cat, words] of Object.entries(allCategories)) {
        let s = 0;
        words.forEach(w => {
            if (data[w]) {
                const item = data[w].find(d => d.year === y);
                if (item) s += item.value;
            }
        });
        yGrand += s;
    }
    console.log('Year ' + y + ' grand total:', yGrand.toFixed(4));
}

// Top 15 keywords by total frequency
const totalFreq = Object.keys(data).map(k => {
    const total = data[k].reduce((s, d) => s + d.value, 0);
    return {word: k, total};
});
totalFreq.sort((a, b) => b.total - a.total);
console.log('\nTop 15 keywords by total frequency:');
totalFreq.slice(0, 15).forEach((item, i) => {
    console.log((i + 1) + '. ' + item.word + ': ' + item.total.toFixed(4));
});
