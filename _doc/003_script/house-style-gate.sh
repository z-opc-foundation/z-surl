#!/usr/bin/env bash
# =============================================================================
# z-surl house-style 闸门（007_业务仓到z_boot家族标准对齐.md §8.3 步骤 8）
#
# 判据：单测必须 0 Mockito / 0 DB / 0 @SpringBootTest / 0 硬编码凭证。
#
# 【为什么本仓自带这条脚本，而不是直接用文档里那条 grep】
# 007 文档给的是朴素 grep：
#     grep -rn "DataSource\|JdbcTemplate\|@SpringBootTest" z-{m}-core/src/test/
# 它会**扫到注释和 javadoc 里的字面词**。本仓测试类的 javadoc 为了写清约束，
# 原文就含 "0 DataSource/JdbcTemplate、0 @SpringBootTest" 这几个字符串 ——
# 于是这条 grep 报 FAIL，指向的却是"声明自己没用到"的注释行。
# 照抄文档命令会对一个合规的仓误报，久了这条闸门就没人看了。
#
# 本脚本的差异：**先剥掉注释再匹配**，所以
#   - 注释里提到 DataSource/Mockito → 不报（意图声明，不是依赖）
#   - 代码里真 import / 真调用      → 报（真违规）
# 文档原本的三条判据一条不减，另加第 4 条凭证判据。
#
# 兼容 macOS 自带 bash 3.2（无 mapfile）。
# 用法： bash _doc/003_script/house-style-gate.sh
# 退出码：0 = 全过；1 = 有违规
# =============================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_DIR="$REPO_ROOT/z-surl-core/src/test/java"

fail=0

# 剥掉 /* */ 块注释、// 行注释、javadoc 的 * 行，只留可执行代码
strip_comments() {
    perl -0pe 's{/\*.*?\*/}{ }gs' "$1" | perl -pe 's{//.*$}{}; s{^\s*\*.*}{}'
}

# 在所有测试文件的"代码部分"里找正则，命中则打印 文件:行:内容
code_only_grep() {
    local pattern="$1"
    find "$TEST_DIR" -name '*.java' | while read -r f; do
        strip_comments "$f" | grep -nE "$pattern" | while read -r line; do
            echo "  ${f#$REPO_ROOT/}: $line"
        done
    done
}

echo "=== z-surl house-style 闸门 ==="
echo "test dir: ${TEST_DIR#$REPO_ROOT/}"
echo

if [ ! -d "$TEST_DIR" ]; then
    echo "FAIL: 找不到测试目录 $TEST_DIR"
    exit 1
fi

check() {
    local no="$1" title="$2" pattern="$3" hits
    hits="$(code_only_grep "$pattern")"
    # 注意 ${no} 必须带花括号：紧跟中文全角标点时，bash 按字节解析变量名，
    # 全角冒号 "："(U+FF1A, EF BC 9A) 的字节会被并进变量名 → `no?: unbound variable`。
    if [ -n "$hits" ]; then
        echo "[FAIL] 判据 ${no}：${title}"
        echo "$hits"
        fail=1
    else
        echo "[PASS] 判据 ${no}：${title}"
    fi
}

check 1 "出现 Mockito（house style 禁止）"                 'org\.mockito|Mockito\.'
check 2 "出现 DB 依赖（单测不许连库、不许改系统状态）"      'DataSource|JdbcTemplate|EmbeddedDatabase'
check 3 "出现 Spring 容器启动（单测要 <2s 跑完）"          '@SpringBootTest|@RunWith|SpringExtension'
check 4 "出现硬编码凭证（008 凭证管理规范）"  '(password|passwd|secret)[[:space:]]*[:=][[:space:]]*[\"'"'"']?[A-Za-z0-9!@#%^&*_+.-]{8,}'
# 判据 4 的正则说明：
#   - [\"']? 允许赋值号与值之间夹一个引号（password = "S3cretPassw0rd" 这种最常见写法），
#     漏掉它会让模式只在"不加引号"时才命中，等于对最常见的违规形态失明。
#   - 外层单引号 + '\'' 的转义写法，是 POSIX sh 里把单引号放进正则的惯用法。
#   - 不会误报 ${ZSURL_DB_PASSWORD:}（$ 开头不在值字符类里）。

echo
if [ "$fail" -eq 0 ]; then
    echo "RESULT: 全过（4/4）"
else
    echo "RESULT: 有违规"
fi
exit "$fail"
