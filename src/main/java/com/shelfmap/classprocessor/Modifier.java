package com.shelfmap.classprocessor;

/**
 * Access modifier for a field.
 * @author Tsutomu YANO
 */
public enum Modifier {
    PRIVATE("private"), DEFAULT(""), PROTECTED("protected"), PUBLIC("public");
    
    private String modifier;

    private Modifier(String modifier) {
        this.modifier = modifier;
    }
    
    public String getModifier() { return this.modifier; }
}
