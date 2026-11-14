#!/usr/bin/env python3
# 合并三科题库 -> app/assets/questions.txt
# 规则：
#   1) 校验 8 字段、答案下标、空选项、重复题干
#   2) 解析里没有引用"A项/B项"等字眼的题，随机打乱选项（均衡答案分布，防止"总选A"）
#   3) 解析引用选项字母的题保持原样，避免解析对不上
import glob, os, re, sys, zlib, random, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'app', 'assets', 'questions.txt')

# 已知需要修正的句子（人工兜底）
FIXES = {
    '下列各句中，不存在语病的一项是？':
        'A项没有语病；B项“通过……使……”导致主语残缺，C项主宾搭配不当，D项两面对一面。',
}

LETTER_REF = re.compile(r'(?<![A-Za-z])[ABCD](?![A-Za-z])')


def load(path):
    rows = []
    for ln, line in enumerate(open(path, encoding='utf-8'), 1):
        line = line.strip().lstrip('\ufeff')
        if not line or line.startswith('#'):
            continue
        f = line.split('|')
        if len(f) != 8:
            print(f'  ! {os.path.basename(path)}:{ln} 字段数 {len(f)}，跳过')
            continue
        subj, stem, a, b, c, d, ans, exp = f
        opts = [a, b, c, d]
        if any(not o.strip() for o in opts):
            print(f'  ! {os.path.basename(path)}:{ln} 空选项，跳过')
            continue
        if len(set(opts)) != 4:
            print(f'  ! {os.path.basename(path)}:{ln} 选项重复，跳过')
            continue
        try:
            ai = int(ans.strip())
        except ValueError:
            print(f'  ! {os.path.basename(path)}:{ln} 答案不是数字，跳过')
            continue
        if not 0 <= ai <= 3:
            print(f'  ! {os.path.basename(path)}:{ln} 答案越界，跳过')
            continue
        exp = FIXES.get(stem.strip(), exp)
        rows.append([subj.strip(), stem.strip(), opts, ai, exp.strip()])
    return rows


def main():
    pattern = os.path.join(ROOT, 'questions_src', '*.txt')
    files = sorted(glob.glob(pattern))
    if not files:
        files = sorted(glob.glob(os.path.join(ROOT, 'app', 'assets', 'questions_*.txt')))
    if not files:
        sys.exit('没有找到 questions_*.txt')

    allrows, seen, skipped = [], set(), 0
    for path in files:
        rows = load(path)
        print(f'{os.path.basename(path)}: {len(rows)} 题')
        for r in rows:
            if r[1] in seen:
                skipped += 1
                continue
            seen.add(r[1])
            allrows.append(r)

    shuffled = 0
    for r in allrows:
        if LETTER_REF.search(r[4]):
            continue
        order = [0, 1, 2, 3]
        random.Random(zlib.crc32(r[1].encode('utf-8'))).shuffle(order)
        r[2] = [r[2][i] for i in order]
        r[3] = order.index(r[3])
        shuffled += 1

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8', newline='\n') as fp:
        fp.write('# 格式: 科目|题干|A|B|C|D|正确序号0-3|解析\n')
        for subj, stem, opts, ai, exp in allrows:
            fp.write('|'.join([subj, stem, *opts, str(ai), exp]) + '\n')

    dist = collections.Counter(str(r[3]) for r in allrows)
    subj = collections.Counter(r[0] for r in allrows)
    print(f'\n合计 {len(allrows)} 题（去重跳过 {skipped}），打乱选项 {shuffled} 题')
    print('科目分布:', dict(subj))
    print('答案分布:', {k: dist.get(k, 0) for k in '0123'})
    print('输出:', OUT)


if __name__ == '__main__':
    main()
