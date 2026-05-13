/**
 * Node.java
 *
 * Represents a single node in a ternary tree file system.
 *
 * Ternary tree structure:
 *   - left:   first child (eldest child of this directory)
 *   - middle: next sibling (peer at the same level)
 *   - right:  previous sibling (peer at the same level, reverse link)
 *
 * This mirrors the classic "left-child right-sibling" pattern extended
 * to a ternary form: left = first child, middle = next sibling, right = prev sibling.
 * It supports arbitrary numbers of children while staying a true ternary tree.
 */
public class Node {

    // ── Core identity ─────────────────────────────────────────────────
    private String  name;
    private boolean isDirectory;
    private String  content;       // Only meaningful for file nodes
    private long    createdAt;     // Epoch ms — for metadata display
    private long    modifiedAt;

    // ── Ternary tree pointers ─────────────────────────────────────────
    private Node left;    // First child
    private Node middle;  // Next sibling
    private Node right;   // Prev sibling
    private Node parent;

    // ─────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────

    public Node(String name, boolean isDirectory) {
        this.name        = name;
        this.isDirectory = isDirectory;
        this.content     = "";
        this.createdAt   = System.currentTimeMillis();
        this.modifiedAt  = this.createdAt;
    }

    // ─────────────────────────────────────────────────────────────────
    // Child management  (ternary left-child / right-sibling with prev)
    // ─────────────────────────────────────────────────────────────────

    public void addChild(Node child) {
        if (!isDirectory) return;

        child.parent = this;

        if (left == null) {
            left = child;
        } else {
            Node cursor = left;
            while (cursor.middle != null) {
                cursor = cursor.middle;
            }
            cursor.middle = child;   
            child.right   = cursor;  
        }
        modifiedAt = System.currentTimeMillis();
    }

    public void removeChild(Node child) {
        if (!isDirectory || left == null) return;

        if (left == child) {
            left = child.middle;
            if (left != null) left.right = null;
        } else {
            Node prev = child.right;
            Node next = child.middle;
            if (prev != null) prev.middle = next;
            if (next != null) next.right  = prev;
        }

        child.parent = null;
        child.middle = null;
        child.right  = null;
        modifiedAt   = System.currentTimeMillis();
    }

    public Node getChildByName(String name) {
        Node cursor = left;
        while (cursor != null) {
            if (cursor.name.equals(name)) return cursor;
            cursor = cursor.middle;
        }
        return null;
    }

    public boolean hasChildren() {
        return left != null;
    }

    public void forEachChild(java.util.function.Consumer<Node> visitor) {
        Node cursor = left;
        while (cursor != null) {
            visitor.accept(cursor);
            cursor = cursor.middle;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // File content
    // ─────────────────────────────────────────────────────────────────

    public void writeContent(String text) {
        if (!isDirectory) {
            this.content   = text;
            this.modifiedAt = System.currentTimeMillis();
        }
    }

    public void appendContent(String text) {
        if (!isDirectory) {
            this.content   = this.content + text;
            this.modifiedAt = System.currentTimeMillis();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Accessors
    // ─────────────────────────────────────────────────────────────────

    public String  getName()       { return name; }
    public boolean isDirectory()   { return isDirectory; }
    public String  getContent()    { return content; }
    public Node    getParent()     { return parent; }
    public Node    getLeft()       { return left; }    // first child
    public Node    getMiddle()     { return middle; }  // next sibling
    public Node    getRight()      { return right; }   // prev sibling
    public long    getCreatedAt()  { return createdAt; }
    public long    getModifiedAt() { return modifiedAt; }

    public String stat() {
        java.text.SimpleDateFormat sdf =
            new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return String.format(
            "  Name    : %s%n  Type    : %s%n  Created : %s%n  Modified: %s%s",
            name,
            isDirectory ? "Directory" : "File",
            sdf.format(new java.util.Date(createdAt)),
            sdf.format(new java.util.Date(modifiedAt)),
            isDirectory ? "" : String.format("%n  Size    : %d bytes", content.length())
        );
    }
}