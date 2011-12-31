package com.shelfmap.interfaceprocessor;

/**
 * Access modifier for a field.
 * @author Tsutomu YANO
 */
public enum FieldModifier {
    PRIVATE("private"), DEFAULT(""), PROTECTED("protected");
    
    private String modifier;

    private FieldModifier(String modifier) {
        this.modifier = modifier;
    }
    
    public String getModifier() { return this.modifier; }
}
