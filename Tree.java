/**
 * Tree.java
 *
 * Manages the ternary tree file system. Owns the root node and provides
 * tree-level operations: path resolution, recursive removal, and visual rendering.
 *
 * The ternary structure (left = first child, middle = next sibling,
 * right = prev sibling) is fully encapsulated here — callers use
 * clean path strings and Node references.
 */
public class Tree {

    private final Node root;

    private static final String ANSI_RESET  = "\u001B[0m";
    private static final String ANSI_BOLD   = "\u001B[1m";
    private static final String ANSI_BLUE   = "\u001B[34m";
    private static final String ANSI_CYAN   = "\u001B[36m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_DIM    = "\u001B[2m";

    // ─────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────

    public Tree(String rootName) {
        this.root = new Node(rootName, true);
    }

    public Node getRoot() { return root; }

    // ─────────────────────────────────────────────────────────────────
    // Directory / file creation
    // ─────────────────────────────────────────────────────────────────

    public Node addDirectory(Node parent, String name) {
        if (!parent.isDirectory()) return null;
        if (parent.getChildByName(name) != null) return null; // duplicate guard
        Node dir = new Node(name, true);
        parent.addChild(dir);
        return dir;
    }

    public Node addFile(Node parent, String name) {
        if (!parent.isDirectory()) return null;
        if (parent.getChildByName(name) != null) return null;
        Node file = new Node(name, false);
        parent.addChild(file);
        return file;
    }

    public boolean remove(Node target) {
        Node parent = target.getParent();
        if (parent == null) return false; // can't remove root
        parent.removeChild(target);
        return true;
    }

    // ─────────────────────────────────────────────────────────────────
    // Path resolution
    // ─────────────────────────────────────────────────────────────────

    public Node resolvePath(Node base, String path) {
        if (path == null || path.isEmpty()) return base;

        Node cursor;
        String[] segments;

        if (path.equals("~") || path.equals("/root")) {
            return root;
        }

        if (path.startsWith("/")) {
            // Absolute path from root
            cursor   = root;
            segments = path.substring(1).split("/");
        } else {
            cursor   = base;
            segments = path.split("/");
        }

        for (String seg : segments) {
            if (seg.isEmpty() || seg.equals(".")) continue;

            if (seg.equals("..")) {
                if (cursor.getParent() != null) cursor = cursor.getParent();
                continue;
            }

            if (seg.equals("~")) {
                cursor = root;
                continue;
            }

            Node next = cursor.getChildByName(seg);
            if (next == null) return null;
            cursor = next;
        }
        return cursor;
    }

    public String getAbsolutePath(Node node) {
        if (node == root) return "/root";
        StringBuilder sb = new StringBuilder();
        Node cursor = node;
        while (cursor != null && cursor != root) {
            sb.insert(0, "/" + cursor.getName());
            cursor = cursor.getParent();
        }
        sb.insert(0, "/root");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────
    // Tree visualisation
    // ─────────────────────────────────────────────────────────────────

    public void drawTree() {
        System.out.println();
        System.out.println(ANSI_BOLD + ANSI_BLUE + "/ (root)" + ANSI_RESET);
        drawRecursive(root, "", true);
        System.out.println();
    }

    public void drawSubtree(Node node) {
        System.out.println();
        String label = node.isDirectory()
            ? ANSI_BOLD + ANSI_BLUE + node.getName() + "/" + ANSI_RESET
            : ANSI_CYAN + node.getName() + ANSI_RESET;
        System.out.println(label);
        drawRecursive(node, "", true);
        System.out.println();
    }

    // ── Private helpers ───────────────────────────────────────────────

    private void drawRecursive(Node node, String prefix, boolean isRoot) {
        java.util.List<Node> children = new java.util.ArrayList<>();
        node.forEachChild(children::add);

        for (int i = 0; i < children.size(); i++) {
            Node  child  = children.get(i);
            boolean last = (i == children.size() - 1);

            String connector = last ? "└── " : "├── ";
            String childPrefix = prefix + (last ? "    " : "│   ");

            if (child.isDirectory()) {
                System.out.println(prefix + connector
                    + ANSI_BOLD + ANSI_BLUE + child.getName() + "/" + ANSI_RESET);
            } else {
                String sizeHint = ANSI_DIM + " (" + child.getContent().length() + "B)" + ANSI_RESET;
                System.out.println(prefix + connector
                    + ANSI_CYAN + child.getName() + ANSI_RESET + sizeHint);
            }

            drawRecursive(child, childPrefix, false);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Persistence — save / load
    // ─────────────────────────────────────────────────────────────────

    public void save(String filename) throws java.io.IOException {
        if (!filename.endsWith(".ttd")) filename += ".ttd";

        try (java.io.PrintWriter pw = new java.io.PrintWriter(
                new java.io.FileWriter(filename))) {
            saveRecursive(root, pw);
        }
    }

    private void saveRecursive(Node node, java.io.PrintWriter pw) {
        node.forEachChild(child -> {
            String path = getAbsolutePath(child);
            if (child.isDirectory()) {
                pw.println("DIR\t" + path);
            } else {
                pw.println("FILE\t" + path + "\t" + child.getContent());
            }
            saveRecursive(child, pw);
        });
    }

    public int load(String filename) throws java.io.IOException {
        if (!filename.endsWith(".ttd")) filename += ".ttd";

        java.util.List<Node> toRemove = new java.util.ArrayList<>();
        root.forEachChild(toRemove::add);
        toRemove.forEach(root::removeChild);

        int count = 0;
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.FileReader(filename))) {

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] parts = line.split("\t", 3);
                if (parts.length < 2) continue;

                String type = parts[0];
                String path = parts[1];

                String relative = path.equals("/root") ? ""
                    : path.startsWith("/root/") ? path.substring("/root/".length())
                    : path;

                if (relative.isEmpty()) continue; 

                String[] segments  = relative.split("/");
                Node     cursor    = root;

                int limit = "DIR".equals(type) ? segments.length : segments.length - 1;
                for (int i = 0; i < limit; i++) {
                    Node next = cursor.getChildByName(segments[i]);
                    if (next == null) next = addDirectory(cursor, segments[i]);
                    cursor = next;
                }

                if ("FILE".equals(type)) {
                    String fname   = segments[segments.length - 1];
                    String content = parts.length == 3 ? parts[2] : "";
                    Node   file    = addFile(cursor, fname);
                    if (file != null) file.writeContent(content);
                }

                count++;
            }
        }
        return count;
    }

    // ─────────────────────────────────────────────────────────────────
    // Utility — count nodes
    // ─────────────────────────────────────────────────────────────────

    public int countNodes() {
        return countRecursive(root);
    }

    private int countRecursive(Node node) {
        int[] count = {0};
        node.forEachChild(child -> {
            count[0]++;
            count[0] += countRecursive(child);
        });
        return count[0];
    }
}