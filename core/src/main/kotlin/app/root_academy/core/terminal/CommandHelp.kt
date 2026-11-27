package app.root_academy.core.terminal

/** Usage, a one-line summary and an example for one command, shown by `help` and `help <command>`. */
data class CommandHelp(val usage: String, val summary: String, val example: String)

/** Help text of every command in [COMMAND_NAMES]. Like all terminal output it is English, as in a real shell. */
val COMMAND_HELP: Map<String, CommandHelp> = mapOf(
    "help" to CommandHelp("help [command]", "show this list, or details about one command", "help grep"),
    "ls" to CommandHelp("ls [-a] [-l] [dir]", "list files; -a shows hidden ones, -l shows owners and permissions", "ls -a"),
    "cd" to CommandHelp("cd [dir]", "change directory; cd .. goes up, cd alone goes home", "cd /var/log"),
    "pwd" to CommandHelp("pwd", "print the directory you are in", "pwd"),
    "cat" to CommandHelp("cat <file>", "print the contents of a file", "cat readme.txt"),
    "head" to CommandHelp("head [-n N] <file>", "print the first lines of a file (10 by default)", "head -n 5 app.log"),
    "tail" to CommandHelp("tail [-n N] <file>", "print the last lines of a file (10 by default)", "tail -n 5 app.log"),
    "grep" to CommandHelp("grep [-i] [-v] [-c] [-n] [-r] <word> [file]", "show only the lines that contain a word; -i ignores case, -v inverts, -c counts, -n numbers lines, -r searches folders", "grep password app.log"),
    "find" to CommandHelp("find [dir] [-type f|d] [-name|-iname pattern]", "search for files by name or type", "find / -name \"*.bak\""),
    "wc" to CommandHelp("wc [-l] [-w] [-c] <file>", "count lines, words and characters", "wc -l app.log"),
    "echo" to CommandHelp("echo <text>", "print text", "echo hello"),
    "whoami" to CommandHelp("whoami", "print the user you are logged in as", "whoami"),
    "id" to CommandHelp("id", "print your user id", "id"),
    "uname" to CommandHelp("uname", "print the name and system of this machine", "uname"),
    "base64" to CommandHelp("base64 [-d] <file>", "encode a file as Base64, or decode it with -d. Encoding is not encryption", "base64 -d note.txt"),
    "rot13" to CommandHelp("rot13 <file>", "shift every letter 13 places; running it twice gives the original", "rot13 note.txt"),
    "hash" to CommandHelp("hash [-a md5|sha1|sha256] <file>", "print the fingerprint (hash) of a file or text", "hash -a sha256 note.txt"),
    "strings" to CommandHelp("strings <file>", "print the readable text hidden inside a binary file", "strings program.bin"),
    "history" to CommandHelp("history", "list the commands you have typed", "history"),
    "scan" to CommandHelp("scan [ip]", "look for machines on the network. Loud: raises the trace meter a lot", "scan 10.0.0.5"),
    "connect" to CommandHelp("connect <host> <service>", "open a service on a machine and read its banner", "connect web01 http"),
    "ssh" to CommandHelp("ssh [user@]host", "log in to another machine; it asks for a password", "ssh dev@staging"),
    "exit" to CommandHelp("exit", "log out of the current machine and go back", "exit"),
    "submit" to CommandHelp("submit <flag>", "hand in the flag you found. A flag looks like flag{some_words}", "submit flag{example}"),
    "clear" to CommandHelp("clear", "clear the screen", "clear"),
)
