import java.util.*;
import java.text.SimpleDateFormat;

/**
 * BashTerminal.java
 *
 * Interactive CLI that simulates a file system backed by a ternary tree.
 * Commands: help, pwd, ls, tree, mkdir, touch, write, append, cat, stat,
 *           cp, mv, rm, cd, find, save, load, clear, exit
 *
 * Run:  javac Node.java Tree.java BashTerminal.java && java BashTerminal
 */
public class BashTerminal {

    // ── ANSI colour helpers ───────────────────────────────────────────
    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE   = "\u001B[34m";
    private static final String CYAN   = "\u001B[36m";
    private static final String DIM    = "\u001B[2m";

    // ── State ─────────────────────────────────────────────────────────
    private final Tree fileSystem;
    private Node       currentDirectory;
    private final List<String> history = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────
    // Boot
    // ─────────────────────────────────────────────────────────────────

    public BashTerminal() {
        fileSystem       = new Tree("root");
        currentDirectory = fileSystem.getRoot();
        printBanner();
        fileSystem.drawTree();
    }

    // ─────────────────────────────────────────────────────────────────
    // REPL
    // ─────────────────────────────────────────────────────────────────

    public void startTerminal() {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            String prompt = BOLD + GREEN + fileSystem.getAbsolutePath(currentDirectory)
                          + RESET + BOLD + " $ " + RESET;
            System.out.print(prompt);

            if (!scanner.hasNextLine()) break;      // EOF / piped input
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            history.add(line);

            // Split on first whitespace run: command | rest-of-line
            String[] tokens = line.split("\\s+", 2);
            String   cmd    = tokens[0];
            String   args   = tokens.length > 1 ? tokens[1] : "";

            switch (cmd) {

                // ── Navigation ─────────────────────────────────────
                case "cd"     : cd(args);          break;
                case "pwd"    : pwd();             break;

                // ── Listing ────────────────────────────────────────
                case "ls"     : ls(args);          break;
                case "tree"   : cmdTree(args);     break;

                // ── Creation ───────────────────────────────────────
                case "mkdir"  : mkdir(args);       break;
                case "touch"  : touch(args);       break;

                // ── File I/O ───────────────────────────────────────
                case "write"  : write(args);       break;
                case "append" : append(args);      break;
                case "cat"    : cat(args);         break;

                // ── Metadata ───────────────────────────────────────
                case "stat"   : stat(args);        break;

                // ── Copy / Move / Delete ───────────────────────────
                case "cp"     : cp(args);          break;
                case "mv"     : mv(args);          break;
                case "rm"     : rm(args);          break;

                // ── Search ─────────────────────────────────────────
                case "find"   : find(args);        break;

                // ── Persistence ────────────────────────────────────
                case "save"   : save(args);        break;
                case "load"   : load(args);        break;

                // ── History ────────────────────────────────────────
                case "history": printHistory();    break;

                // ── Misc ───────────────────────────────────────────
                case "clear"  : clearScreen();     break;
                case "help"   : displayHelp();     break;
                case "exit"   :
                    System.out.println(DIM + "Goodbye." + RESET);
                    scanner.close();
                    return;

                default:
                    err("Unknown command: '" + cmd + "'. Type 'help' for a list.");
            }
        }
        scanner.close();
    }

    // ─────────────────────────────────────────────────────────────────
    // Commands
    // ─────────────────────────────────────────────────────────────────

    // pwd ─────────────────────────────────────────────────────────────
    private void pwd() {
        System.out.println(fileSystem.getAbsolutePath(currentDirectory));
    }

    // ls ──────────────────────────────────────────────────────────────
    private void ls(String args) {
        Node target = args.isEmpty()
            ? currentDirectory
            : fileSystem.resolvePath(currentDirectory, args);

        if (target == null) { err("ls: no such file or directory: " + args); return; }
        if (!target.isDirectory()) { err("ls: not a directory: " + args); return; }

        if (!target.hasChildren()) {
            System.out.println(DIM + "(empty)" + RESET);
            return;
        }

        target.forEachChild(child -> {
            if (child.isDirectory()) {
                System.out.printf("  %s%-20s%s  %s<dir>%s%n",
                    BOLD + BLUE, child.getName() + "/", RESET, DIM, RESET);
            } else {
                System.out.printf("  %-21s  %s%d B%s%n",
                    CYAN + child.getName() + RESET,
                    DIM, child.getContent().length(), RESET);
            }
        });
    }

    // tree ────────────────────────────────────────────────────────────
    private void cmdTree(String args) {
        if (args.isEmpty()) {
            fileSystem.drawTree();
        } else {
            Node target = fileSystem.resolvePath(currentDirectory, args);
            if (target == null) { err("tree: no such directory: " + args); return; }
            fileSystem.drawSubtree(target);
        }
    }

    // cd ──────────────────────────────────────────────────────────────
    private void cd(String args) {
        if (args.isEmpty()) {
            currentDirectory = fileSystem.getRoot(); // cd with no args → root
            return;
        }
        Node target = fileSystem.resolvePath(currentDirectory, args);
        if (target == null)           { err("cd: no such directory: " + args); return; }
        if (!target.isDirectory())    { err("cd: not a directory: " + args);   return; }
        currentDirectory = target;
    }

    // mkdir ───────────────────────────────────────────────────────────
    private void mkdir(String args) {
        if (args.isEmpty()) { err("Usage: mkdir <name>"); return; }

        String[] parts = args.split("/");
        Node cursor    = currentDirectory;

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            Node existing = cursor.getChildByName(part);
            if (existing != null) {
                if (!existing.isDirectory()) { err("mkdir: '" + part + "' exists as a file"); return; }
                cursor = existing;  // already exists, step into it
            } else {
                Node created = fileSystem.addDirectory(cursor, part);
                if (created == null) { err("mkdir: could not create '" + part + "'"); return; }
                cursor = created;
            }
        }
        ok("Directory created: " + args);
    }

    // touch ───────────────────────────────────────────────────────────
    private void touch(String args) {
        if (args.isEmpty()) { err("Usage: touch <filename>"); return; }

        int lastSlash = args.lastIndexOf('/');
        Node parent   = currentDirectory;
        String fname  = args;

        if (lastSlash >= 0) {
            String parentPath = args.substring(0, lastSlash);
            fname  = args.substring(lastSlash + 1);
            parent = fileSystem.resolvePath(currentDirectory, parentPath);
            if (parent == null || !parent.isDirectory()) {
                err("touch: parent directory not found: " + parentPath); return;
            }
        }

        if (parent.getChildByName(fname) != null) {
            System.out.println(DIM + fname + " already exists." + RESET);
            return;
        }

        Node file = fileSystem.addFile(parent, fname);
        if (file == null) { err("touch: could not create file: " + fname); return; }
        ok("File created: " + fname);
    }

    // write ───────────────────────────────────────────────────────────
    private void write(String args) {
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) { err("Usage: write <filename> <content>"); return; }

        Node file = resolveFile(parts[0]);
        if (file == null) return;

        file.writeContent(parts[1]);
        ok("Written to " + file.getName());
    }

    // append ──────────────────────────────────────────────────────────
    private void append(String args) {
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) { err("Usage: append <filename> <content>"); return; }

        Node file = resolveFile(parts[0]);
        if (file == null) return;

        file.appendContent(parts[1]);
        ok("Appended to " + file.getName());
    }

    // cat ─────────────────────────────────────────────────────────────
    private void cat(String args) {
        if (args.isEmpty()) { err("Usage: cat <filename>"); return; }
        Node file = resolveFile(args);
        if (file == null) return;

        String content = file.getContent();
        if (content.isEmpty()) {
            System.out.println(DIM + "(empty file)" + RESET);
        } else {
            System.out.println(content);
        }
    }

    // stat ────────────────────────────────────────────────────────────
    private void stat(String args) {
        if (args.isEmpty()) { err("Usage: stat <name>"); return; }
        Node target = fileSystem.resolvePath(currentDirectory, args);
        if (target == null) { err("stat: no such file or directory: " + args); return; }
        System.out.println(target.stat());
    }

    // rm ──────────────────────────────────────────────────────────────
    private void rm(String args) {
        if (args.isEmpty()) { err("Usage: rm <name>"); return; }

        boolean recursive = false;
        String  target    = args;

        if (args.startsWith("-r ") || args.startsWith("-r\t")) {
            recursive = true;
            target    = args.substring(3).trim();
        }

        Node node = fileSystem.resolvePath(currentDirectory, target);
        if (node == null) { err("rm: no such file or directory: " + target); return; }
        if (node == fileSystem.getRoot()) { err("rm: cannot remove root"); return; }

        if (node.isDirectory() && node.hasChildren() && !recursive) {
            err("rm: '" + target + "' is a non-empty directory. Use 'rm -r' to remove it.");
            return;
        }

        boolean removed = fileSystem.remove(node);
        if (removed) ok("Removed: " + target);
        else          err("rm: could not remove: " + target);
    }

    // cp ──────────────────────────────────────────────────────────────
    private void cp(String args) {
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) { err("Usage: cp <source> <destination>"); return; }

        Node src = fileSystem.resolvePath(currentDirectory, parts[0]);
        if (src == null)          { err("cp: source not found: " + parts[0]);        return; }
        if (src.isDirectory())    { err("cp: directory copy not supported yet");      return; }

        Node destDir = fileSystem.resolvePath(currentDirectory, parts[1]);
        String newName;

        if (destDir != null && destDir.isDirectory()) {
            newName = src.getName();
        } else {
            // Destination is a new filename in the current dir or a path
            int slash = parts[1].lastIndexOf('/');
            if (slash >= 0) {
                destDir = fileSystem.resolvePath(currentDirectory, parts[1].substring(0, slash));
                newName = parts[1].substring(slash + 1);
            } else {
                destDir = currentDirectory;
                newName = parts[1];
            }
        }

        if (destDir == null || !destDir.isDirectory()) { err("cp: destination directory not found"); return; }
        if (destDir.getChildByName(newName) != null)   { err("cp: '" + newName + "' already exists at destination"); return; }

        Node copy = fileSystem.addFile(destDir, newName);
        copy.writeContent(src.getContent());
        ok("Copied " + src.getName() + " → " + fileSystem.getAbsolutePath(copy));
    }

    // mv ──────────────────────────────────────────────────────────────
    private void mv(String args) {
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) { err("Usage: mv <source> <destination>"); return; }

        Node src = fileSystem.resolvePath(currentDirectory, parts[0]);
        if (src == null) { err("mv: source not found: " + parts[0]); return; }

        Node destDir = fileSystem.resolvePath(currentDirectory, parts[1]);
        String newName;

        if (destDir != null && destDir.isDirectory()) {
            newName = src.getName();
        } else {
            int slash = parts[1].lastIndexOf('/');
            if (slash >= 0) {
                destDir = fileSystem.resolvePath(currentDirectory, parts[1].substring(0, slash));
                newName = parts[1].substring(slash + 1);
            } else {
                destDir = currentDirectory;
                newName = parts[1];
            }
        }

        if (destDir == null || !destDir.isDirectory()) { err("mv: destination directory not found"); return; }
        if (destDir.getChildByName(newName) != null)   { err("mv: '" + newName + "' already exists at destination"); return; }

        Node oldParent = src.getParent();
        if (oldParent == null) { err("mv: cannot move root"); return; }
        oldParent.removeChild(src);

        Node moved;
        if (src.isDirectory()) {
            moved = fileSystem.addDirectory(destDir, newName);
            migrateChildren(src, moved);
        } else {
            moved = fileSystem.addFile(destDir, newName);
            moved.writeContent(src.getContent());
        }

        ok("Moved → " + fileSystem.getAbsolutePath(moved));
    }

    /** Recursively migrates children from {@code src} into {@code dest}. */
    private void migrateChildren(Node src, Node dest) {
        // Collect first so we don't modify while iterating
        List<Node> kids = new ArrayList<>();
        src.forEachChild(kids::add);
        for (Node kid : kids) {
            src.removeChild(kid);
            if (kid.isDirectory()) {
                Node newDir = fileSystem.addDirectory(dest, kid.getName());
                migrateChildren(kid, newDir);
            } else {
                Node newFile = fileSystem.addFile(dest, kid.getName());
                if (newFile != null) newFile.writeContent(kid.getContent());
            }
        }
    }

    // save ────────────────────────────────────────────────────────────
    private void save(String args) {
        if (args.isEmpty()) { err("Usage: save <filename>"); return; }
        try {
            fileSystem.save(args);
            ok("Session saved to " + (args.endsWith(".ttd") ? args : args + ".ttd"));
        } catch (java.io.IOException e) {
            err("save: could not write file — " + e.getMessage());
        }
    }

    // load ────────────────────────────────────────────────────────────
    private void load(String args) {
        if (args.isEmpty()) { err("Usage: load <filename>"); return; }
        try {
            int count = fileSystem.load(args);
            currentDirectory = fileSystem.getRoot(); // reset position to root
            ok("Loaded " + count + " nodes from " + (args.endsWith(".ttd") ? args : args + ".ttd"));
            fileSystem.drawTree();
        } catch (java.io.FileNotFoundException e) {
            err("load: file not found — " + (args.endsWith(".ttd") ? args : args + ".ttd"));
        } catch (java.io.IOException e) {
            err("load: could not read file — " + e.getMessage());
        }
    }

    // find ────────────────────────────────────────────────────────────
    private void find(String args) {
        if (args.isEmpty()) { err("Usage: find <name>"); return; }
        List<String> results = new ArrayList<>();
        findRecursive(fileSystem.getRoot(), args.toLowerCase(), results);
        if (results.isEmpty()) {
            System.out.println(DIM + "No matches found." + RESET);
        } else {
            results.forEach(System.out::println);
        }
    }

    private void findRecursive(Node node, String query, List<String> results) {
        node.forEachChild(child -> {
            if (child.getName().toLowerCase().contains(query)) {
                results.add("  " + fileSystem.getAbsolutePath(child));
            }
            findRecursive(child, query, results);
        });
    }

    // history ─────────────────────────────────────────────────────────
    private void printHistory() {
        for (int i = 0; i < history.size(); i++) {
            System.out.printf("  %s%3d%s  %s%n", DIM, i + 1, RESET, history.get(i));
        }
    }

    // clear ───────────────────────────────────────────────────────────
    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    // help ────────────────────────────────────────────────────────────
    private void displayHelp() {
        System.out.println();
        System.out.println(BOLD + "  Ternary Tree Directory — Commands" + RESET);
        System.out.println(DIM + "  ─────────────────────────────────────────────" + RESET);
        String[][] cmds = {
            {"Navigation", ""},
            {"  cd <path>",      "Change directory (supports .., ~, /abs/path, a/b/c)"},
            {"  pwd",            "Print working directory"},
            {"Listing", ""},
            {"  ls [path]",      "List contents of a directory"},
            {"  tree [path]",    "Draw ternary tree from root or a given node"},
            {"Creation", ""},
            {"  mkdir <path>",   "Create directory (creates intermediate dirs too)"},
            {"  touch <file>",   "Create an empty file"},
            {"File I/O", ""},
            {"  write <f> <c>",  "Overwrite a file's content"},
            {"  append <f> <c>", "Append content to a file"},
            {"  cat <file>",     "Display file content"},
            {"Metadata", ""},
            {"  stat <name>",    "Show file/dir metadata"},
            {"Copy / Move / Delete", ""},
            {"  cp <src> <dst>", "Copy a file to a new location"},
            {"  mv <src> <dst>", "Move or rename a file/directory"},
            {"  rm [-r] <name>", "Remove a file (-r for non-empty directories)"},
            {"Persistence", ""},
            {"  save <filename>", "Save current session to a .ttd file"},
            {"  load <filename>", "Load a session from a .ttd file"},
            {"Search", ""},
            {"  find <query>",   "Find files/dirs by name (case-insensitive)"},
            {"Misc", ""},
            {"  history",        "Show command history"},
            {"  clear",          "Clear the screen"},
            {"  help",           "Show this message"},
            {"  exit",           "Quit the terminal"},
        };
        for (String[] row : cmds) {
            if (row[1].isEmpty()) {
                System.out.println();
                System.out.println(YELLOW + "  " + row[0] + RESET);
            } else {
                System.out.printf("  " + CYAN + "%-20s" + RESET + "  %s%n", row[0], row[1]);
            }
        }
        System.out.println();
    }

    private Node resolveFile(String path) {
        Node node = fileSystem.resolvePath(currentDirectory, path);
        if (node == null)        { err("File not found: " + path);             return null; }
        if (node.isDirectory())  { err("'" + path + "' is a directory");       return null; }
        return node;
    }

    private void ok(String msg) {
        System.out.println(GREEN + "  ✔ " + RESET + msg);
    }

    private void err(String msg) {
        System.out.println(RED + "  ✖ " + msg + RESET);
    }

    private void printBanner() {
        System.out.println(BOLD + BLUE);
        System.out.println("  ████████╗██████╗ ███████╗███████╗███████╗██╗  ██╗███████╗██╗     ██╗");
        System.out.println("     ██╔══╝██╔══██╗██╔════╝██╔════╝██╔════╝██║  ██║██╔════╝██║     ██║");
        System.out.println("     ██║   ██████╔╝█████╗  █████╗  ███████╗███████║█████╗  ██║     ██║");
        System.out.println("     ██║   ██╔══██╗██╔══╝  ██╔══╝  ╚════██║██╔══██║██╔══╝  ██║     ██║");
        System.out.println("     ██║   ██║  ██║███████╗███████╗███████║██║  ██║███████╗███████╗███████╗");
        System.out.println("     ╚═╝   ╚═╝  ╚═╝╚══════╝╚══════╝╚══════╝╚═╝  ╚═╝╚══════╝╚══════╝╚══════╝");
        System.out.println(RESET + CYAN + "  TreeShell v2.0  —  Ternary Tree File System" + RESET);
        System.out.println(DIM + "  Type 'help' for commands." + RESET);
        System.out.println();
    }

    // ─────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        new BashTerminal().startTerminal();
    }
}