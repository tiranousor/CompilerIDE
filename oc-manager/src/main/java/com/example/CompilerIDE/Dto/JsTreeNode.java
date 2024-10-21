package com.example.CompilerIDE.Dto;

public class JsTreeNode {
    private String id;
    private String text;
    private boolean children;
    private String type;

    public JsTreeNode(String id, String text, boolean children, String type) {
        this.id = id;
        this.text = text;
        this.children = children;
        this.type = type;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public boolean isChildren() { return children; }
    public void setChildren(boolean children) { this.children = children; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
