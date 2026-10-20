package com.zifang.z.surl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * README 里每一条定量主张都必须能从源码重算出来 —— 广告出去的话就是承诺。
 *
 * <p>为什么要有这一层：本仓 README 把「3 个 Maven 模块」「端口 8080 五处一致」
 * 「HTTP API 4 个端点」「表名 {@code z_surl_mapping}」「风险表 6 条」「架构四篇」
 * 这些数字写死在正文里，而每加一个模块、每改一次端口、每增一个端点都会漂一次。
 * 没有任何尺子去问源码「现在还是吗」，那这些数字的保质期就是上一次编辑的时刻。</p>
 *
 * <p>口径：</p>
 * <ol>
 *   <li><b>主张抠不到就红</b>：不许「正则没匹配上就跳过」，那正是空跑；</li>
 *   <li><b>数值不等就红</b>，且把<b>重算值</b>打进消息（红了要知道真数是多少）；</li>
 *   <li><b>只数 src/main</b>：测试类不是 README 说的「类」。</li>
 * </ol>
 *
 * <p><b>实现上最要紧的一条</b>：README 侧的数字必须在核对<b>当下</b>从被核对的那份 README 里现解析，
 * 不能先算好再拿��己比。早期版本把两边都算进一个 {@code Facts} 对象，
 * 结果「README 写 8080」和「yml 里是 8080」比的是同一份 Facts 里的两个字段 ——
 * README 改成 8081 尺子照样绿，尺子等于没有。
 * 所以这里 {@link Facts} 只装<b>源码侧</b>的事实，README 侧的值都在
 * {@link #verifyClaims(String, Facts)} 里现抠。</p>
 *
 * <p>尺自己的牙口由 {@link #rulerActuallyCondemnsWrongNumbers()} 用合成 README 证明，
 * 而「不许冤枉健康页」由 {@link #readmeClaimsMatchTheCode()} 本身证明。</p>
 */
class ReadmeClaimsTest {

    /** 仓根：向上找同时有 README.md 与 pom.xml 的目录（测试跑在模块目录，user.dir 不是仓根）。 */
    private static Path repoRoot() {
        Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 5 && p != null; i++, p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("README.md")) && Files.isRegularFile(p.resolve("pom.xml"))) {
                return p;
            }
        }
        throw new IllegalStateException("定位仓根失败：user.dir=" + System.getProperty("user.dir"));
    }

    private static String read(Path f) {
        try {
            return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 剥掉块注释与行注释 —— Javadoc 里出现的 {@code @GetMapping} 字样不是真映射。 */
    private static String stripComments(String src) {
        String s = src.replaceAll("(?s)/\\*.*?\\*/", "");
        return s.replaceAll("(?m)//[^\\n]*", "");
    }

    private static List<String> lines(String readme) {
        return Arrays.asList(readme.split("\n", -1));
    }

    /** README 结构树里的某一行（树形前缀 + 节点名 + 终止条件，防 z-surl-core 误配 z-surl-core-x）。 */
    private static String treeLine(String readme, String node) {
        Pattern p = Pattern.compile("^\\s*(?:├──|└──)[ \\t]*" + Pattern.quote(node) + "(?![\\w-])[^\\n]*$");
        List<String> hits = new ArrayList<>();
        for (String l : lines(readme)) {
            if (p.matcher(l).find()) {
                hits.add(l);
            }
        }
        if (hits.isEmpty()) {
            throw new AssertionError("README 结构树里找不到节点行：" + node);
        }
        if (hits.size() > 1) {
            throw new AssertionError("README 结构树里节点定位不唯一（命中 " + hits.size() + " 行）：" + node);
        }
        return hits.get(0);
    }

    /** 唯一匹配的行（基本信息表那种一行一主张的写法）；找不到或不唯一都红。 */
    private static String uniqueLine(String readme, Pattern anchor, String label) {
        List<String> hits = new ArrayList<>();
        for (String l : lines(readme)) {
            if (anchor.matcher(l).find()) {
                hits.add(l);
            }
        }
        if (hits.isEmpty()) {
            throw new AssertionError("README 里找不到主张行：" + label + "（定位 " + anchor.pattern() + "）");
        }
        if (hits.size() > 1) {
            throw new AssertionError("README 里主张行定位不唯一（命中 " + hits.size() + " 行）：" + label);
        }
        return hits.get(0);
    }

    /** 根 pom 的 <modules> 里的模块名（先剥 XML 注释，否则会被注释掉的 module 骗到）。 */
    private static Set<String> pomModules(Path root) {
        String raw = read(root.resolve("pom.xml"));
        String noComment = raw.replaceAll("(?s)<!--.*?-->", "");
        Matcher m = Pattern.compile("<module>([^<]+)</module>").matcher(noComment);
        Set<String> names = new LinkedHashSet<>();
        while (m.find()) {
            names.add(m.group(1).trim());
        }
        if (names.isEmpty()) {
            throw new IllegalStateException("根 pom 里没有 <module>");
        }
        return names;
    }

    /** README 结构树顶层列出的 z-surl-* 模块名（`│   ├──` 那种嵌套的不算）。 */
    private static Set<String> readmeTreeModules(String readme) {
        Pattern p = Pattern.compile("^├── (z-surl-[\\w-]+)/");
        Set<String> names = new LinkedHashSet<>();
        for (String l : lines(readme)) {
            Matcher m = p.matcher(l);
            if (m.find()) {
                names.add(m.group(1));
            }
        }
        return names;
    }

    private static long countFiles(Path dir, String suffix) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("目录不存在：" + dir);
        }
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .filter(p -> !p.toString().contains("/target/"))
                    .count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 某个模块 src/main 下的类名集合（不含文件名后缀）。 */
    private static Set<String> classNames(Path root, String moduleRelPath) {
        Path d = root.resolve(moduleRelPath).resolve("src/main");
        if (!Files.isDirectory(d)) {
            throw new IllegalStateException("目录不存在：" + moduleRelPath + "/src/main");
        }
        try (Stream<Path> s = Files.walk(d)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(p -> p.getFileName().toString().replace(".java", ""))
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 某个 Controller 的方法级映射路径（类级 @RequestMapping 不算）。 */
    private static List<String> methodMappings(Path controllerFile) {
        String src = stripComments(read(controllerFile));
        List<String> out = new ArrayList<>();
        List<String> ls = Arrays.asList(src.split("\n", -1));
        for (int i = 0; i < ls.size(); i++) {
            Matcher m = Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping(?:\\(\"([^\"]*)\"\\))?").matcher(ls.get(i));
            if (!m.find()) {
                continue;
            }
            // 往后找第一条非注解、非空行：是 class 声明就是类级注解
            String decl = null;
            for (int j = i + 1; j < ls.size(); j++) {
                String t = ls.get(j).trim();
                if (t.isEmpty() || t.startsWith("@")) {
                    continue;
                }
                decl = t;
                break;
            }
            if (decl != null && decl.matches(".*\\b(class|interface|enum)\\s+\\w+.*")) {
                continue;
            }
            out.add(m.group(2) == null ? "" : m.group(2));
        }
        return out;
    }

    /** 剥掉 query string。 */
    private static String pathOnly(String p) {
        int q = p.indexOf('?');
        return q < 0 ? p : p.substring(0, q);
    }

    /**
     * 一次重算出的**源码侧**事实。
     *
     * <p>这里<b>只有</b>源码侧的值。README 侧的数字一律在 {@link #verifyClaims(String, Facts)}
     * 里现抠 —— 混进来就等于尺子对自己的断言做恒等式比较，README 怎么改都是绿的。</p>
     */
    private static final class Facts {
        final Set<String> pomModules;
        final boolean adminSkipDeploy;
        final Set<String> coreClasses;
        final Set<String> starterClasses;
        final List<String> controllerMappings;
        final String tableName;
        final int riskRows;
        final int yamlPort;
        final int dockerExposePort;
        final int k8sContainerPort;
        final int composePort;
        final int archDocFiles;
        final Path root;

        Facts(Path root, Set<String> pomModules, boolean adminSkipDeploy, Set<String> coreClasses,
              Set<String> starterClasses, List<String> controllerMappings, String tableName,
              int riskRows, int yamlPort, int dockerExposePort, int k8sContainerPort,
              int composePort, int archDocFiles) {
            this.root = root;
            this.pomModules = pomModules;
            this.adminSkipDeploy = adminSkipDeploy;
            this.coreClasses = coreClasses;
            this.starterClasses = starterClasses;
            this.controllerMappings = controllerMappings;
            this.tableName = tableName;
            this.riskRows = riskRows;
            this.yamlPort = yamlPort;
            this.dockerExposePort = dockerExposePort;
            this.k8sContainerPort = k8sContainerPort;
            this.composePort = composePort;
            this.archDocFiles = archDocFiles;
        }
    }

    private static Facts recomputed() {
        Path root = repoRoot();

        boolean adminSkip = read(root.resolve("z-surl-admin/pom.xml"))
                .contains("<maven.deploy.skip>true</maven.deploy.skip>");

        String doSrc = read(root.resolve(
                "z-surl-core/src/main/java/com/zifang/z/surl/domain/entity/SurlMappingDO.java"));
        Matcher tm = Pattern.compile("public\\s+static\\s+final\\s+String\\s+TABLE\\s*=\\s*\"([^\"]+)\"")
                .matcher(doSrc);
        if (!tm.find()) {
            throw new IllegalStateException("SurlMappingDO 里找不到 TABLE 常量");
        }

        int riskRows = 0;
        for (String l : lines(read(root.resolve("_doc/001_arch/02-feature.md")))) {
            if (l.matches("^\\|\\s*\\d+\\s*\\|.*")) {
                riskRows++;
            }
        }

        // compose 的端口映射是 "宿主机:容器" —— 取**容器**那一侧（宿主机那侧可被环境变量改）。
        int composePort = portIn(read(root.resolve("deploy/docker-compose.yml")),
                Pattern.compile("\\$\\{ZSURL_HTTP_PORT:-(\\d+)\\}:(\\d+)"), 2);

        return new Facts(root,
                pomModules(root),
                adminSkip,
                classNames(root, "z-surl-core"),
                classNames(root, "z-surl-starter"),
                methodMappings(root.resolve("z-surl-starter/src/main/java/com/zifang/z/surl/web/SurlController.java")),
                tm.group(1),
                riskRows,
                portIn(read(root.resolve("z-surl-admin/src/main/resources/application.yml")),
                        Pattern.compile("(?m)^\\s*port:\\s*(\\d+)\\s*$")),
                portIn(read(root.resolve("deploy/Dockerfile.backend")),
                        Pattern.compile("(?m)^EXPOSE\\s+(\\d+)\\s*$")),
                portIn(read(root.resolve("deploy/k8s/01-statefulset-backend.yaml")),
                        Pattern.compile("(?m)^\\s*containerPort:\\s*(\\d+)\\s*$")),
                composePort,
                (int) countFiles(root.resolve("_doc/001_arch"), ".md"));
    }

    private static int portIn(String text, Pattern p) {
        return portIn(text, p, 1);
    }

    /** 取第 group 组（1 起）作为端口号。 */
    private static int portIn(String text, Pattern p, int group) {
        Matcher m = p.matcher(text);
        if (!m.find()) {
            throw new IllegalStateException("抠不到端口号：" + p.pattern());
        }
        return Integer.parseInt(m.group(group));
    }

    private static int chineseNumeral(String s) {
        switch (s) {
            case "一": return 1;
            case "二": return 2;
            case "三": return 3;
            case "四": return 4;
            case "五": return 5;
            case "六": return 6;
            case "七": return 7;
            case "八": return 8;
            case "九": return 9;
            case "十": return 10;
            default: throw new IllegalArgumentException("认不出的中文数字：" + s);
        }
    }

    /**
     * README 里逐条抠出主张并与源码重算值对账；不一致就抛 AssertionError 并把真数打进去。
     *
     * <p>README 侧的数字全部在这里现抠（{@code readme} 参数），源码侧的事实来自 {@code f}。</p>
     */
    private static void verifyClaims(String readme, Facts f) {
        // 主张 1：结构树列出的 z-surl-* 模块 == 根 pom 的 <modules>
        Set<String> treeModules = readmeTreeModules(readme);
        assertEquals(new TreeSet<>(f.pomModules), new TreeSet<>(treeModules),
                "README 结构树列出的模块与根 pom <modules> 对不上（README 列了 " + treeModules
                        + "，pom 是 " + f.pomModules + "）");

        // 主张 2：z-surl-admin 不上 Central
        String adminLine = treeLine(readme, "z-surl-admin/");
        assertTrue(adminLine.contains("deploy.skip=true"),
                "README 结构树的 z-surl-admin 那一行没写「deploy.skip=true」：" + adminLine);
        assertTrue(f.adminSkipDeploy,
                "README 说 z-surl-admin 不上 Central，但 z-surl-admin/pom.xml 里没有 <maven.deploy.skip>true</maven.deploy.skip>");

        // 主张 3：z-surl-core / z-surl-starter 那一行点名的类都真实存在
        String coreLine = treeLine(readme, "z-surl-core/");
        for (String cls : new String[]{"SurlMappingDO", "SurlMappingMapper", "SurlDbService", "SurlService"}) {
            assertTrue(coreLine.contains(cls),
                    "README 结构树的 z-surl-core 那一行没点名 " + cls + "：" + coreLine);
            assertTrue(f.coreClasses.contains(cls),
                    "README 说 z-surl-core 有 " + cls + "，但 z-surl-core/src/main 下没有这个类");
        }
        String starterLine = treeLine(readme, "z-surl-starter/");
        for (String cls : new String[]{"ZSurlAutoConfiguration", "SurlController"}) {
            assertTrue(starterLine.contains(cls),
                    "README 结构树的 z-surl-starter 那一行没点名 " + cls + "：" + starterLine);
            assertTrue(f.starterClasses.contains(cls),
                    "README 说 z-surl-starter 有 " + cls + "，但 z-surl-starter/src/main 下没有这个类");
        }

        // 主张 4：HTTP API 表与 Controller 的方法级映射一一对上
        List<String> readmeApiRows = new ArrayList<>();
        Pattern row = Pattern.compile("^\\|\\s*(POST|GET|DELETE|PUT|PATCH|HEAD)\\s*\\|\\s*`([^`]+)`\\s*\\|");
        for (String l : lines(readme)) {
            Matcher m = row.matcher(l);
            if (m.find()) {
                readmeApiRows.add(pathOnly(m.group(2)));
            }
        }
        assertEquals(f.controllerMappings.size(), readmeApiRows.size(),
                "HTTP API 表的行数（" + readmeApiRows.size() + "）与 SurlController 的方法级映射数（"
                        + f.controllerMappings.size() + "）对不上");
        for (String p : f.controllerMappings) {
            assertTrue(readmeApiRows.contains(pathOnly(p)),
                    "SurlController 里有映射 " + p + "，但 README 的 HTTP API 表里没有这一行");
        }
        for (String p : readmeApiRows) {
            boolean found = false;
            for (String m : f.controllerMappings) {
                if (pathOnly(m).equals(p)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "README 的 HTTP API 表里有 " + p + "，但 SurlController 里没有这个映射");
        }

        // 主张 5：表名
        assertTrue(readme.contains("`" + f.tableName + "`"),
                "README 里没有点出表名 " + f.tableName + "（源码常量 SurlMappingDO.TABLE 就是它）");

        // 主张 6：风险表条数（README 侧现抠）
        String riskLine = uniqueLine(readme, Pattern.compile("的风险表（"), "风险表条数");
        Matcher rm = Pattern.compile("的风险表（(\\d+) 条").matcher(riskLine);
        if (!rm.find()) {
            throw new AssertionError("README 风险表那一行抠不出条数：" + riskLine);
        }
        assertEquals(f.riskRows, Integer.parseInt(rm.group(1)),
                "风险表条数对不上：README 写 " + rm.group(1) + " 条，_doc/001_arch/02-feature.md 里实数是 "
                        + f.riskRows + " 条");

        // 主张 7：端口五处一致（README 侧现抠，其余四处来自源码）
        String portLine = uniqueLine(readme, Pattern.compile("端口 \\*\\*\\d+\\*\\*"), "端口");
        Matcher pm = Pattern.compile("端口 \\*\\*(\\d+)\\*\\*").matcher(portLine);
        if (!pm.find()) {
            throw new AssertionError("README 端口那一行抠不出端口号：" + portLine);
        }
        int readmePort = Integer.parseInt(pm.group(1));
        assertEquals(f.yamlPort, readmePort, "端口对不上：README 写 " + readmePort + "，application.yml 是 " + f.yamlPort);
        assertEquals(f.dockerExposePort, readmePort, "端口对不上：README 写 " + readmePort + "，Dockerfile EXPOSE 是 " + f.dockerExposePort);
        assertEquals(f.k8sContainerPort, readmePort, "端口对不上：README 写 " + readmePort + "，k8s containerPort 是 " + f.k8sContainerPort);
        assertEquals(f.composePort, readmePort, "端口对不上：README 写 " + readmePort + "，compose 容器端口是 " + f.composePort);

        // 主张 8：架构篇数（README 侧现抠）
        String archLine = treeLine(readme, "001_arch/");
        Matcher am = Pattern.compile("架构([一二三四五六七八九十])篇").matcher(archLine);
        if (!am.find()) {
            throw new AssertionError("README 结构树的 001_arch 那一行抠不出「架构 N 篇」：" + archLine);
        }
        assertEquals(f.archDocFiles, chineseNumeral(am.group(1)),
                "架构篇数对不上：README 写 " + chineseNumeral(am.group(1)) + " 篇，_doc/001_arch 下实数是 "
                        + f.archDocFiles + " 个 .md");

        // 主张 9：单测只点名不计数 —— 点名的两个测试类要真实存在
        for (String cls : new String[]{"SurlServiceMySqlImplTest", "SurlControllerValidationTest"}) {
            assertTrue(readme.contains(cls), "README 的开发段没点名测试类 " + cls);
            assertTrue(countFiles(f.root, cls + ".java") > 0,
                    "README 点名了测试类 " + cls + "，但仓里找不到这个测试文件");
        }
    }

    @Test
    @DisplayName("README 的定量主张与源码重算值逐条对账")
    void readmeClaimsMatchTheCode() {
        String readme = read(repoRoot().resolve("README.md"));
        verifyClaims(readme, recomputed());
    }

    @Test
    @DisplayName("尺子的牙口：把 README 的数改错，判定必须判红")
    void rulerActuallyCondemnsWrongNumbers() {
        String readme = read(repoRoot().resolve("README.md"));
        Facts f = recomputed();

        // 变异 1：端口改错
        String tamperedPort = readme.replaceFirst("端口 \\*\\*\\d+\\*\\*", "端口 **9999**");
        assertNotEquals(readme, tamperedPort, "篡改失败：README 里找不到「端口 **NNNN**」（写法变了？）");
        AssertionError err1 = assertThrows(AssertionError.class, () -> verifyClaims(tamperedPort, f));
        assertTrue(err1.getMessage().contains("端口对不上"),
                "端口改错必须判红并指向端口，实际是：" + err1.getMessage());

        // 变异 2：风险表条数改错
        String tamperedRisk = readme.replaceFirst("的风险表（\\d+ 条", "的风险表（99 条");
        assertNotEquals(readme, tamperedRisk, "篡改失败：README 里找不到「的风险表（N 条」（写法变了？）");
        AssertionError err2 = assertThrows(AssertionError.class, () -> verifyClaims(tamperedRisk, f));
        assertTrue(err2.getMessage().contains("风险表条数对不上"),
                "风险表条数改错必须判红，实际是：" + err2.getMessage());

        // 变异 3：结构树的模块清单少一个
        String tamperedMods = readme.replaceFirst("├── z-surl-admin/", "├── z-surl-admn/");
        assertNotEquals(readme, tamperedMods, "篡改失败：README 里找不到「├── z-surl-admin/」");
        AssertionError err3 = assertThrows(AssertionError.class, () -> verifyClaims(tamperedMods, f));
        assertTrue(err3.getMessage().contains("模块"),
                "模块清单改错必须判红，实际是：" + err3.getMessage());

        // 变异 4：HTTP API 表少一行
        String tamperedApi = readme.replaceFirst("\\| GET \\| `/s/\\{mapKey\\}` \\|[^\\n]*\\n", "");
        assertNotEquals(readme, tamperedApi, "篡改失败：README 里找不到「| GET | `/s/{mapKey}` |」那一行");
        AssertionError err4 = assertThrows(AssertionError.class, () -> verifyClaims(tamperedApi, f));
        assertTrue(err4.getMessage().contains("HTTP API"),
                "HTTP API 表少一行必须判红，实际是：" + err4.getMessage());
    }

    @Test
    @DisplayName("不许冤枉健康页：真实 README 不得被判红")
    void healthyReadmeIsNotCondemned() {
        String readme = read(repoRoot().resolve("README.md"));
        verifyClaims(readme, recomputed());
    }
}
