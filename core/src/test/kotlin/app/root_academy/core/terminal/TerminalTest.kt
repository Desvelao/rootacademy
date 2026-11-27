package app.root_academy.core.terminal

import app.root_academy.core.content.*
import kotlin.test.*

class TerminalTest {
    private val flag = "flag{test}"

    private fun mission(vararg objectives: Objective, traceLimit: Int = 100) = Mission(
        id = "t", startHost = "a", startUser = "guest", flagSha256 = Crypto.sha256(flag), traceLimit = traceLimit,
        hosts = listOf(
            Host("10.0.0.1", "a", listOf("ssh"), files = mapOf(
                "/home/guest/a.txt" to VFile("hello\nworld\nSECRET=1", owner = "guest"),
                "/home/guest/.dot/b.txt" to VFile("hidden"),
                "/etc/private" to VFile("nope", mode = "600", owner = "root"),
                "/home/guest/enc.txt" to VFile(Crypto.base64Encode("decoded!")),
            )),
            Host("10.0.0.2", "b", listOf("ssh", "http"), banners = mapOf("http" to "HI"),
                logins = listOf(Login("root", "pw")), files = mapOf("/root/f" to VFile("on b", "600"))),
            Host("10.0.0.3", "c", hidden = true),
        ),
        objectives = objectives.toList().ifEmpty { listOf(Objective("flag", "flag")) },
    )

    private fun runner(m: Mission = mission()) = MissionRunner(m)
    private fun MissionRunner.out(line: String) = submitLine(line).output.lines

    @Test fun tokenizerHandlesQuotesAndPipes() {
        assertEquals(listOf(listOf("echo", "a b"), listOf("grep", "x")), Tokenizer.pipeline("echo 'a b' | grep x"))
    }

    @Test fun helpDescribesEveryCommand() {
        assertEquals(COMMAND_NAMES.toSet(), COMMAND_HELP.keys)
        val lines = runner().out("help")
        for (name in COMMAND_NAMES) assertTrue(lines.any { it.trim().startsWith(name) && it.contains(COMMAND_HELP.getValue(name).summary) }, name)
    }

    @Test fun helpForOneCommandShowsUsageAndExample() {
        val lines = runner().out("help grep")
        assertTrue(lines.first().startsWith("Usage:") && lines.first().contains("grep"), lines.toString())
        assertTrue(lines.any { it.startsWith("Example:") }, lines.toString())
        assertEquals(listOf("help: no help for 'nope'. Type help to list the commands"), runner().out("help nope"))
    }

    @Test fun lsHidesDotFilesUnlessAll() {
        val r = runner()
        r.out("cd ~")
        assertEquals(listOf("a.txt  enc.txt"), r.out("ls"))
        assertEquals(listOf(".dot/  a.txt  enc.txt"), r.out("ls -a"))
    }

    @Test fun catAndPermissions() {
        val r = runner()
        assertEquals(listOf("hello", "world", "SECRET=1"), r.out("cat ~/a.txt"))
        assertEquals(listOf("cat: /etc/private: Permission denied"), r.out("cat /etc/private"))
        assertEquals(listOf("cat: missing: No such file or directory"), r.out("cat missing"))
    }

    @Test fun pipesAndGrep() {
        val r = runner()
        assertEquals(listOf("SECRET=1"), r.out("cat ~/a.txt | grep SECRET"))
        assertEquals(listOf("/home/guest/a.txt:SECRET=1"), r.out("grep -r SECRET /home"))
        assertEquals(listOf("world"), r.out("grep -i WORLD ~/a.txt"))
    }

    @Test fun base64Decode() {
        val r = runner()
        assertEquals(listOf("decoded!"), r.out("cat ~/enc.txt | base64 -d"))
        assertEquals(listOf("aGk="), r.out("base64 hi"))
    }

    @Test fun hashAlgorithms() {
        val r = runner()
        assertEquals(listOf(Crypto.digest("MD5", "abc")), r.out("hash -a md5 abc"))
        assertEquals(listOf(Crypto.sha256("abc")), r.out("hash abc"))
    }

    @Test fun scanSkipsHiddenAndSelf() {
        val out = runner().out("scan").joinToString("\n")
        assertTrue("b" in out && "10.0.0.3" !in out && "10.0.0.1" !in out)
    }

    @Test fun connectShowsBanner() {
        val r = runner()
        assertEquals(listOf("HI"), r.out("connect b http"))
        assertTrue(r.out("connect b ftp").first().contains("refused"))
    }

    @Test fun sshNeedsPasswordThenSwitchesHost() {
        val r = runner(mission(Objective("in", "connect", "b"), Objective("flag", "flag")))
        assertEquals(listOf("root@b's password:"), r.out("ssh root@b"))
        assertEquals(listOf("Permission denied, please try again."), r.out("wrong"))
        r.out("ssh root@b")
        assertEquals(listOf("Welcome to b."), r.out("pw"))
        assertEquals("root@b:~#", r.submitLine("").prompt)
        assertEquals(listOf("on b"), r.out("cat f"))
        r.out("exit")
        assertTrue(r.submitLine("pwd").prompt.startsWith("guest@a"))
        assertTrue("in" in r.doneObjectives)
    }

    @Test fun flagCompletesMission() {
        val r = runner()
        assertEquals(listOf("Wrong flag."), r.out("submit nope"))
        assertFalse(r.completed)
        r.out("submit $flag")
        assertTrue(r.completed)
    }

    @Test fun traceLimitFailsMission() {
        val r = runner(mission(traceLimit = 20))
        r.out("scan")
        val res = r.submitLine("scan")
        assertTrue(res.failed)
        assertTrue(r.out("ls").first().startsWith("TRACE"))
    }

    @Test fun unknownCommand() {
        assertTrue(runner().out("rm -rf /").first().contains("command not found"))
    }

    @Test fun replayerDetectsIncompleteSolution() {
        val m = mission()
        assertTrue(SolutionReplayer.replay(m, "submit $flag").ok)
        assertFalse(SolutionReplayer.replay(m, "ls").ok)
        assertFalse(SolutionReplayer.replay(m, "submit wrong").ok)
    }

    @Test fun completion() {
        val r = runner()
        assertEquals(listOf("cd", "cat"), Completer.complete(r.session, "c").filter { it.length <= 3 })
        r.out("cd ~")
        assertEquals(listOf("a.txt"), Completer.complete(r.session, "cat a"))
        assertEquals("cat", Completer.commonPrefix(listOf("cat", "catx")))
    }

    @Test fun virtualFsResolve() {
        assertEquals("/a/c", VirtualFs.resolve("/a/b", "/home/x", "../c"))
        assertEquals("/home/x/d", VirtualFs.resolve("/", "/home/x", "~/d"))
        assertEquals("/", VirtualFs.resolve("/", "/h", ".."))
    }

    // ----- trailing newlines, wc, head/tail, grep and find options -----
    private fun withFiles(vararg extra: Pair<String, VFile>, traceLimit: Int = 100): Mission {
        val m = mission(traceLimit = traceLimit)
        return m.copy(hosts = m.hosts.map { if (it.name == "a") it.copy(files = it.files + extra) else it })
    }
    private val nl = "/home/guest/n.txt" to VFile("x y\nz\n", owner = "guest")

    @Test fun trailingNewlineDoesNotAddALine() {
        val r = runner(withFiles(nl))
        assertEquals(listOf("x y", "z"), r.out("cat ~/n.txt"))
        assertEquals(listOf("z"), r.out("tail -n 1 ~/n.txt"))
        assertEquals(listOf("2 3 6 ~/n.txt"), r.out("wc ~/n.txt"))
    }

    @Test fun wcFlagsAndStdin() {
        val r = runner(withFiles(nl))
        assertEquals(listOf("2 ~/n.txt"), r.out("wc -l ~/n.txt"))
        assertEquals(listOf("3 ~/n.txt"), r.out("wc -w ~/n.txt"))
        assertEquals(listOf("2"), r.out("cat ~/n.txt | wc -l"))
        assertEquals(listOf("2 3 ~/n.txt"), r.out("wc -lw ~/n.txt"))
        assertEquals(listOf("wc: invalid option -- 'x'"), r.out("wc -x ~/n.txt"))
        assertTrue(r.out("wc ~/n.txt ~/a.txt").last().endsWith("total"))
    }

    @Test fun headTailShorthandAndInvalidOptions() {
        val r = runner()
        assertEquals(listOf("hello", "world"), r.out("head -2 ~/a.txt"))
        assertEquals(listOf("hello"), r.out("head -n1 ~/a.txt"))
        assertEquals(listOf("SECRET=1"), r.out("tail -1 ~/a.txt"))
        assertEquals(listOf("head: invalid option -- 'z'"), r.out("head -z ~/a.txt"))
    }

    @Test fun headTailNegativeAndHugeCountsDoNotCrash() {
        val r = runner()
        assertEquals(listOf("hello", "world"), r.out("head -n -1 ~/a.txt"))
        assertEquals(listOf("SECRET=1"), r.out("tail -n -1 ~/a.txt"))
        assertEquals(listOf("SECRET=1"), r.out("tail -n-1 ~/a.txt"))
        assertEquals(listOf("head: invalid number of lines: '99999999999'"), r.out("head -99999999999 ~/a.txt"))
        assertEquals(3, r.out("head -n 5 ~/a.txt").size)
    }

    @Test fun grepOptionsAfterPatternRecursiveDefaultAndBadRegex() {
        val r = runner()
        assertEquals(listOf("SECRET=1"), r.out("grep -e SECRET ~/a.txt"))
        assertEquals(listOf("hello"), r.out("grep HELLO ~/a.txt -i"))
        r.out("cd ~")
        assertEquals(listOf("/home/guest/a.txt:SECRET=1"), r.out("grep -r SECRET").filter { it.contains("a.txt") })
        assertEquals(listOf("hello"), r.out("grep -w hello ~/a.txt"))
        assertEquals(emptyList(), r.out("grep -w hell ~/a.txt"))
        assertEquals(listOf("hello"), r.out("grep -E 'hel+o' ~/a.txt"))
        assertEquals(listOf("grep: Invalid regular expression"), r.out("grep '(' ~/a.txt"))
    }

    @Test fun errorsDoNotFlowThroughPipesAndHeadTailTakeSeveralFiles() {
        val r = runner(withFiles(nl))
        assertEquals(listOf("cat: missing: No such file or directory"), r.out("cat missing | grep x"))
        assertEquals(listOf("==> ~/n.txt <==", "x y", "", "==> ~/a.txt <==", "hello"), r.out("head -1 ~/n.txt ~/a.txt"))
        val tabs = runner(withFiles("/home/guest/t.txt" to VFile("é\tb\n", owner = "guest")))
        assertEquals(listOf("1 2 5 ~/t.txt"), tabs.out("wc ~/t.txt"))
    }

    @Test fun grepInvertCountAndFilesOnly() {
        val r = runner()
        assertEquals(listOf("hello", "SECRET=1"), r.out("grep -v world ~/a.txt"))
        assertEquals(listOf("2"), r.out("grep -c o ~/a.txt"))
        assertEquals(listOf("/home/guest/a.txt"), r.out("grep -rl SECRET /home"))
        assertEquals(listOf("grep: invalid option -- 'q'"), r.out("grep -q x ~/a.txt"))
    }

    @Test fun findNameTypeAndGlobs() {
        val r = runner(withFiles(nl))
        assertEquals(listOf("/home/guest/n.txt"), r.out("find /home -name '?.txt' -type f").filter { it.endsWith("n.txt") })
        assertEquals(listOf("/home/guest/a.txt"), r.out("find /home -iname 'A.TXT'"))
        assertEquals(listOf("/home", "/home/guest", "/home/guest/.dot"), r.out("find /home -type d"))
        assertTrue(r.out("find /home").containsAll(listOf("/home/guest", "/home/guest/a.txt")))
        assertEquals(listOf("find: unknown predicate '-bogus'"), r.out("find /home -bogus"))
    }

    // ----- scan, tokenizer, pipeline failure -----
    @Test fun scanExactAddressDoesNotListTheWholeSubnet() {
        val r = runner()
        assertTrue(r.out("scan 10.0.0.2").any { it.contains("b ") })
        assertEquals(listOf("Scanning network...", "No hosts found."), r.out("scan 10.0.0.9"))
        assertTrue(r.out("scan 10.0.0.0/24").any { it.contains("10.0.0.2") })
        assertTrue(r.out("scan 10.0.0").any { it.contains("10.0.0.2") })
        assertEquals(listOf("Scanning network...", "No hosts found."), r.out("scan 10.0.00"))
    }

    @Test fun unclosedQuoteIsASyntaxError() {
        val r = runner()
        assertTrue(r.out("echo 'abc").single().startsWith("syntax error"))
        assertEquals(listOf("a|b"), r.out("echo 'a|b'"))
        assertTrue(Tokenizer.isBalanced("echo \"x\" 'y'"))
        assertFalse(Tokenizer.isBalanced("echo \"x"))
    }

    @Test fun traceFailureStopsLaterPipelineStages() {
        val r = runner(mission(traceLimit = 15))
        val step = r.submitLine("scan | submit $flag")
        assertTrue(step.failed)
        assertFalse(step.completed, "the submit stage must not run after the trace limit was hit")
    }

    // ----- other commands -----
    @Test fun historyRot13LsLongAndStrings() {
        val r = runner()
        r.out("pwd"); r.out("whoami")
        assertEquals(listOf("   1  pwd", "   2  whoami", "   3  history"), r.out("history"))
        assertEquals(listOf("uryyb"), r.out("rot13 hello"))
        assertEquals(listOf("-rw-r--r-- guest a.txt"), r.out("ls -l ~").filter { it.endsWith("a.txt") })
        assertEquals(listOf("hello", "world", "SECRET=1"), r.out("strings ~/a.txt").flatMap { it.split("\n") }.filter { it.length >= 4 }.let { listOf("hello", "world", "SECRET=1").filter { w -> it.any { l -> w in l } } })
    }

    @Test fun exitReturnsFromSshAndOwnMachineCannotExit() {
        val r = runner()
        assertTrue(r.out("exit").single().contains("own machine"))
        r.out("ssh root@b"); r.out("pw")
        assertTrue(r.submitLine("whoami").prompt.contains("@b"))
        assertEquals(listOf("Connection closed."), r.out("exit"))
        assertTrue(r.session.prompt().contains("@a"))
    }

    @Test fun readObjectiveAndHiddenHostSsh() {
        val m = mission(Objective("read", "read", "a:/home/guest/a.txt"), Objective("hop", "connect", "c"))
        val m2 = m.copy(hosts = m.hosts.map { if (it.name == "c") it.copy(services = listOf("ssh"), logins = listOf(Login("root", "pw"))) else it })
        val r = runner(m2)
        assertEquals(emptySet(), r.doneObjectives)
        r.out("cat ~/a.txt")
        assertEquals(setOf("read"), r.doneObjectives)
        assertTrue(r.out("scan").none { it.contains(" c ") }, "hidden hosts are not scanned")
        r.out("ssh root@c"); r.out("pw")
        assertTrue(r.completed)
        assertEquals(setOf("read", "hop"), r.doneObjectives)
    }

    @Test fun completerCompletesCommandsAndPaths() {
        val r = runner()
        assertEquals(listOf("cat"), Completer.complete(r.session, "ca"))
        r.out("cd ~")
        assertEquals(listOf("a.txt"), Completer.complete(r.session, "cat a"))
        assertEquals(listOf("/home/guest/"), Completer.complete(r.session, "ls /home/g"))
    }

    // ----- directories, groups -----
    private fun withDirs(user: String = "guest"): MissionRunner {
        val m = mission().copy(startUser = user)
        val a = m.hosts.first { it.name == "a" }.copy(
            files = mapOf(
                "/home/guest/a.txt" to VFile("mine", owner = "guest"),
                "/srv/team/plan.txt" to VFile("team plan", mode = "640", owner = "root", group = "staff"),
                "/vault/secret.txt" to VFile("locked away", mode = "644", owner = "root"),
            ),
            dirs = mapOf(
                "/srv/team" to VDir(mode = "750", owner = "root", group = "staff"),
                "/vault" to VDir(mode = "700", owner = "root"),
                "/empty" to VDir(mode = "755", owner = "guest"),
            ),
            groups = mapOf("dev" to listOf("staff")),
        )
        return runner(m.copy(hosts = listOf(a) + m.hosts.drop(1)))
    }

    @Test fun directoryPermissionsBlockTraversalAndListing() {
        val r = withDirs()
        assertEquals(listOf("cd: /vault: Permission denied"), r.out("cd /vault"))
        assertEquals(listOf("cat: /vault/secret.txt: Permission denied"), r.out("cat /vault/secret.txt"))
        assertEquals(listOf("ls: cannot open directory '/vault': Permission denied"), r.out("ls /vault"))
        assertTrue(r.out("ls -l /").contains("drwx------ root  vault/"))
        assertTrue(r.out("ls -l /").any { it.startsWith("drwxr-xr-x guest empty") })
        assertEquals(emptyList(), r.out("ls /empty"), "empty directories can exist")
    }

    @Test fun findSkipsDeniedDirectoriesAndCompletionHidesThem() {
        val r = withDirs()
        val out = r.out("find / -name '*'")
        assertTrue("/vault/secret.txt" !in out)
        assertTrue("/vault" in out)
        assertTrue("find: '/vault': Permission denied" in out)
        assertTrue("/srv/team/plan.txt" !in out)
        assertEquals(emptyList(), Completer.complete(r.session, "cat /vault/"))
        assertEquals(emptyList(), Completer.complete(r.session, "cat /srv/team/"))
        assertTrue(r.out("find /home -name '*.txt'").contains("/home/guest/a.txt"))
    }

    @Test fun completionHidesDotfilesUnlessPrefixed() {
        val m = mission()
        val r = runner(m.copy(hosts = m.hosts.map { if (it.name == "a") it.copy(files = it.files + ("/home/guest/.hidden" to VFile("x", owner = "guest"))) else it }))
        r.out("cd ~")
        assertEquals(listOf("a.txt"), Completer.complete(r.session, "cat a"))
        assertTrue(Completer.complete(r.session, "cat ").none { it.startsWith(".") })
        assertEquals(listOf(".dot/", ".hidden"), Completer.complete(r.session, "cat ."))
    }

    @Test fun replayerIgnoresIndentedComments() {
        val m = mission(Objective("read", "read", "a:/home/guest/a.txt"))
        assertFalse(SolutionReplayer.replay(m, "  # cat ~/a.txt\n").ok, "an indented comment must not run")
        assertTrue(SolutionReplayer.replay(m, "  # note\ncat ~/a.txt").ok)
    }

    @Test fun failedSshAndWrongFlagAddTrace() {
        val r = runner()
        r.out("ssh root@b"); r.out("bad")
        assertEquals(2 + 8, r.session.trace)
        r.out("submit nope")
        assertEquals(2 + 8 + 10, r.session.trace)
    }

    @Test fun cdAndLsErrors() {
        val r = runner()
        assertTrue(r.out("cd ~/a.txt").first().endsWith("Not a directory"))
        assertTrue(r.out("cd /nowhere").first().endsWith("No such file or directory"))
        assertTrue(r.out("ls /nowhere").first().contains("No such file or directory"))
        assertEquals(listOf("a.txt"), r.out("ls ~/a.txt"))
    }

    @Test fun textHelperPrefersAReadableFileOverTheLiteral() {
        val r = runner(withFiles("/home/guest/hello" to VFile("aGk=", owner = "guest")))
        r.out("cd ~")
        assertEquals(listOf("hi"), r.out("base64 -d hello"))
        assertEquals(listOf(Crypto.sha256("other")), r.out("hash other"))
    }

    @Test fun recursiveGrepAddsTraceNoise() {
        val r = runner()
        r.out("grep -r hello ~")
        assertEquals(5, r.session.trace)
    }

    @Test fun groupMembershipGrantsGroupBits() {
        assertEquals(listOf("cat: /srv/team/plan.txt: Permission denied"), withDirs("guest").out("cat /srv/team/plan.txt"))
        assertEquals(listOf("team plan"), withDirs("dev").out("cat /srv/team/plan.txt"))
        assertEquals(listOf("locked away"), withDirs("root").out("cat /vault/secret.txt"))
    }
}
