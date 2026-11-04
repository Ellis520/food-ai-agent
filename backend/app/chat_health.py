"""Conservative local health guidance for the chat interface."""

import re

from .food_safety import CATALOG

URGENT_MESSAGE = (
    '如果您现在出现呼吸困难、胸痛、意识异常、严重过敏或其他危险症状，'
    '请立即拨打中国大陆 120 或当地急救电话，按调度人员指引处理。不要等在线回复或先去买药。'
)
SYMPTOMS = re.compile(
    r'不(?:太|大|怎么)?舒服|不适|难受|生病|症状|发烧|发热|低烧|低热|头.{0,3}[晕痛疼]|胃.{0,3}[痛疼]|'
    r'肚子.{0,4}[痛疼]|腹痛|腹泻|拉肚子|水样便|呕吐|想吐|恶心|咳嗽|咳个不停|嗓子.{0,3}[疼痛]|喉咙.{0,3}[疼痛]|咽痛|天旋地转|'
    r'流鼻涕|鼻塞|肌肉酸痛|感冒|过敏了|过敏反应|皮疹|心慌|血压|'
    r'手.{0,3}[痛疼麻]|脚.{0,3}[痛疼麻]|腰.{0,3}[痛疼]'
)
HEALTH = re.compile(
    SYMPTOMS.pattern + r'|过敏|药店|药房|买药|用药|吃药|什么药|'
    r'推荐.{0,4}药|退烧药|止痛药|药品|服药|头孢|布洛芬|对乙酰氨基酚|处方药|就医|看医生|找医院|医院在哪'
)
MEDICINE = re.compile(r'什么药|用药|吃药|买药|推荐.{0,4}药|退烧药|止痛药|非处方药')
FOOD_SAFETY_TOPIC = re.compile(r'冷藏|冷冻|保存|剩饭|剩菜|隔夜|过期|保质期|交叉污染|生熟|过敏原|饮酒|喝酒|酒精')
HEADACHE = re.compile(r'头.{0,3}[痛疼]')
DIARRHEA = re.compile(r'腹泻|拉肚子|水样便')
STOMACH = re.compile(r'肚子.{0,4}[痛疼]|腹痛|胃.{0,3}[痛疼]')
RESPIRATORY = re.compile(r'咳嗽|咳个不停|咽痛|喉咙.{0,3}[痛疼]|嗓子.{0,3}[痛疼]|流鼻涕|鼻塞')
DIZZINESS = re.compile(r'头.{0,3}晕|天旋地转')
FEVER = re.compile(r'发热|发烧|低烧|低热')
TEMPERATURE = re.compile(r'体温\s*[：:]?\s*(\d{2}(?:\.\d+)?)|(\d{2}(?:\.\d+)?)\s*(?:℃|度|°C)')
ONSET = re.compile(r'突然|突发|逐渐|慢慢')
TIMING = re.compile(r'刚刚|刚才|今天|昨天|前天|早上|上午|下午|晚上|(?:\d+|[一两二三四五六七八九十半]+)\s*(天|小时|分钟)')
SEVERITY = re.compile(r'轻微|有点|轻度|中度|明显|剧烈|特别疼|特别痛|很疼|很痛|(?:\d+|[一二三四五六七八九十]+)\s*分')
FOLLOW_UP = re.compile(
    r'(?:\d+|[一两二三四五六七八九十]+)\s*(岁|天|小时|分钟|℃|度|分|次)|'
    r'今天|昨天|前天|刚开始|刚才|突然|慢慢|逐渐|轻微|剧烈|单侧|双侧|一侧|两侧|'
    r'怕光|后脑|太阳穴|钝痛|跳痛|不疼|不痛|口干|尿量|喝水|喝不下|便血|黑便|脖子僵硬|颈部僵硬|^没有[。！!]?$'
)
EXTRA_URGENT = (
    '胸口痛', '严重胸闷', '说不出话', '口角歪斜', '抽搐', '吐血',
    '大量便血', '出血不止', '吃药过量', '服药过量', '药物过量', '误服',
    '意识模糊', '叫不醒', '严重过敏', '喘不上气', '呼吸不了', '严重腹痛', '胸口压榨',
)
URGENT_PATTERNS = [re.compile(re.escape(word)) for word in (*CATALOG['urgentKeywords'], *EXTRA_URGENT)]
URGENT_PATTERNS.append(re.compile(
    r'呼吸.{0,4}(?:困难|不畅)|喘不过来气|胸口.{0,3}(?:剧痛|很痛|很疼)|'
    r'(?:一侧|单侧).{0,8}无力|说话含糊|突然.{0,4}看不见'
))
RISK = re.compile(
    r'怀孕|孕妇|哺乳|孩子|儿童|婴儿|宝宝|儿子|女儿|老人|高烧|高热|持续|加重|剧烈|严重|'
    r'肝病|肝功能|肾病|肾功能|脱水|喝酒|饮酒|抗凝|华法林|正在服药|已经吃了药|过敏|'
    r'胸[闷痛]|腹痛|便血|呕吐|呼吸|抽搐'
)


def present(pattern, text):
    return any(
        not re.search(r'(?:没有(?:出现|发生)?|并无|并未|未出现|否认|不伴|无)\s*$', text[:match.start()])
        for match in pattern.finditer(text)
    )


def urgent_sign(text, previous=''):
    for clause in re.split(r'[，。！？、；,.!?;\n]', text):
        if any(present(pattern, clause) for pattern in URGENT_PATTERNS):
            return True
    context = previous + '。' + text
    if present(HEADACHE, context):
        severe = re.compile(r'剧烈|特别疼|特别痛|很疼|很痛|最严重|痛得受不了|疼得受不了|爆炸般')
        sudden = re.compile(r'突然|突发|一下子')
        if present(sudden, context) and present(severe, context):
            return True
        if present(re.compile(r'发烧|发热|高热'), context) and present(re.compile(r'脖子僵硬|颈部僵硬|颈项强直'), context):
            return True
    return False


def requested_lookup(text):
    hospital = re.search(r'(找|查|推荐|导航|最近|附近|哪|想去|去).{0,12}(医院|卫生院)|^(医院|卫生院)$', text)
    pharmacy = re.search(r'(找|查|推荐|导航|最近|附近|哪|想去|去).{0,12}(药店|药房)|^(药店|药房)$', text)
    if bool(hospital) == bool(pharmacy):
        return None
    return 'hospital' if hospital else 'pharmacy'


def symptom_assessment(text, previous=''):
    context = previous + '。' + text
    for sample in (text, previous):
        if present(DIARRHEA, sample):
            dry = present(re.compile(r'口干|口渴|尿少|尿量.{0,4}减少'), context)
            assessment = (
                '您提到腹泻伴口干，需要留意水分流失。如果尿量明显减少、喝不下水或症状加重，请及时就医。' if dry else
                '腹泻可见于胃肠道感染或饮食相关因素，目前还不能确定原因。'
            )
            if dry and not re.search(r'尿量|尿少|没尿', context):
                question = '请问您现在能正常喝水吗？尿量比平时少吗？'
            elif not re.search(r'(?:\d+|[一两二三四五六七八九十几]+)\s*次', context):
                question = '请问您今天大概腹泻了几次？'
            elif not re.search(r'发热|发烧|便血|体温', context):
                question = '请问您还有发热或便血吗？'
            else:
                question = '您想继续补充情况，还是先查附近医院？'
            return 'diarrhea', assessment, question
        if present(HEADACHE, sample):
            assessment = '头痛可能与紧张、喝水不足、没按时吃饭等有关，单凭头痛还不能确定原因。'
            if not TIMING.search(context) or not ONSET.search(context):
                question = ('请问您的头痛什么时候开始，是突然出现还是慢慢加重的？' if not TIMING.search(context)
                            else '请问您的头痛是突然出现，还是慢慢加重的？')
            elif not SEVERITY.search(context):
                question = '请问您现在的疼痛大概几分？可以用 0 到 10 分表示。'
            elif not re.search(r'单侧|双侧|一侧|两侧|左边|右边|后脑|太阳穴|前额', context):
                question = '请问您主要是哪个部位疼，有没有恶心、呕吐或怕光？'
            else:
                question = '您想继续说说伴随症状，还是先查附近医院？'
            safety = '如果头痛突然且非常剧烈，或伴一侧无力、说话困难，请立即求助急救。'
            return 'headache', assessment + safety, question
        if present(STOMACH, sample):
            assessment = '腹痛的原因很多，消化不良、胀气和胃肠道感染都是可能方向。痛的位置和伴随症状有助于区分。'
            question = ('请问您主要是上腹、肚脐附近还是下腹痛？疼痛有没有加重？' if not re.search(r'上腹|下腹|肚脐|右下|左下', context)
                        else '请问您还有发热、呕吐或大便异常吗？')
            return 'stomach', assessment, question
        if present(RESPIRATORY, sample):
            assessment = '咳嗽、鼻塞或咽痛常见于上呼吸道感染。只凭这些症状，难以区分普通感冒和流感等情况。'
            if not TIMING.search(context):
                question = '请问您的症状持续多久了？'
            elif not TEMPERATURE.search(context):
                question = '请问您量过体温吗？'
            else:
                question = '请问您的症状有没有明显加重？'
            return 'respiratory', assessment, question
        if present(DIZZINESS, sample):
            assessment = '头晕可能与水分不足、体位变化或内耳问题有关，头晕的感觉和诱因有助于区分。'
            question = ('请问您每次头晕持续多久，有没有耳鸣或听力变化？'
                        if re.search(r'天旋地转|眼前发黑|站起来', context)
                        else '请问您是感觉天旋地转，还是站起来时眼前发黑？')
            return 'dizziness', assessment, question
        if present(FEVER, sample):
            assessment = '发热常见于感染，但体温本身不能说明具体原因，还需要结合持续时间和伴随症状。'
            question = ('请问您的实测体温是多少？' if not TEMPERATURE.search(context)
                        else '请问您还有哪些伴随症状，比如咳嗽、咽痛或腹泻？')
            return 'fever', assessment, question
    return None


def guidance(text, previous=''):
    context = previous + '。' + text
    age = re.search(r'(\d{1,3})\s*岁', context)
    adult = bool(age and 18 <= int(age[1]) < 65)
    mild = re.search(r'轻微|有点|低烧|低热', context)
    fever = FEVER.search(context)
    symptom = fever or re.search(r'头痛|头疼|肌肉酸痛', context)
    temperatures = [float(value) for match in TEMPERATURE.findall(context) for value in match if value]
    if any(value >= 39 for value in temperatures) or present(re.compile(r'高烧|高热|黑便|便血|频繁呕吐|明显脱水'), text):
        return 'clinic', '根据您的描述，建议尽快咨询医生或到医院评估。'
    temperature_ok = all(35 <= value < 39 for value in temperatures) and (not fever or temperatures)
    recent = re.search(r'刚开始|今天|昨天|[一两1-2]天|几小时', context)
    if (adult and int(age[1]) < 50 and mild and recent and all(value < 38 for value in temperatures)
            and re.search(r'腹泻|拉肚子', context)
            and not RISK.search(context) and not re.search(
                r'发热|发烧|头晕|乏力|口渴|心慌|频繁|黑便|尿少|糖尿病|心脏病|免疫', context)):
        return 'hydration', (
            '轻度腹泻时要注意补水。可以向药师询问口服补液盐是否适合，并按产品说明冲调。'
            '如果出现便血、明显口渴、尿量减少、频繁呕吐或症状加重，请及时就医。'
        )
    if adult and mild and symptom and recent and temperature_ok and MEDICINE.search(context) and not RISK.search(context):
        return 'otc', (
            '想了解短期发热或轻度疼痛的非处方药时，可以向医生或药师询问单方对乙酰氨基酚是否适合。'
            '请先提供实测体温、过敏史、肝肾疾病、近期饮酒和已用药物等信息，让药师核对说明书及成分。'
            '避免与同成分的感冒药重复使用。症状持续或加重时请就医。'
        )
    question = (
        '请问您的主要症状是什么？是饭后立刻出现，还是过了一段时间才开始？'
        if re.search(r'饭后|吃完饭|用餐后', context)
        else '请问您的主要症状是什么，什么时候开始的？如果量过体温，也可以告诉我实测值。'
    )
    return 'clarify', '我先了解一下您的情况。症状持续或加重时，请及时就医。\n\n' + question
