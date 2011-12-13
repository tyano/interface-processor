/*
 * Copyright 2011 Tsutomu YANO.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shelfmap.interfaceprocessor;

import com.shelfmap.interfaceprocessor.annotation.GenerateClass;
import com.shelfmap.interfaceprocessor.impl.BuildingEnvironment;
import com.shelfmap.interfaceprocessor.impl.DefaultInterfaceDefinition;
import com.shelfmap.interfaceprocessor.util.IO;
import com.shelfmap.interfaceprocessor.util.Objects;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.tools.JavaFileObject;

import static com.shelfmap.interfaceprocessor.util.Strings.capitalize;

import java.beans.PropertyChangeSupport;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 *
 * @author Tsutomu YANO
 */
@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedAnnotationTypes({"com.shelfmap.interfaceprocessor.annotation.GenerateClass"})
public class InterfaceProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment re) {
        if(annotations.isEmpty()) return false;

        Elements elementUtils = processingEnv.getElementUtils();
        Types typeUtils = processingEnv.getTypeUtils();

        boolean processed = false;
        Set<? extends Element> elements = re.getElementsAnnotatedWith(GenerateClass.class);
        for (Element element : elements) {
            if(element.getKind() == ElementKind.INTERFACE) {
                TypeElement typeElement = (TypeElement)element;
                GenerateClass generateAnnotation = typeElement.getAnnotation(GenerateClass.class);
                assert generateAnnotation != null;

                if(!generateAnnotation.autoGenerate()) continue;

                InterfaceDefinition definition = new DefaultInterfaceDefinition();
                Environment visitorEnvironment = new BuildingEnvironment(processingEnv, definition);
                PropertyVisitor visitor = new PropertyVisitor();

                //collect information about the current interface into visitorEnviroment
                visitor.visit(typeElement, visitorEnvironment);

                String packageName = resolvePackageName(generateAnnotation, definition);
                String className = resolveImplementationClassName(generateAnnotation, definition);
                String fullClassName = packageName + "." + className;

                Writer writer = null;
                try {
                    Filer filer = processingEnv.getFiler();
                    JavaFileObject javaFile = filer.createSourceFile(fullClassName, typeElement);
                    writer = javaFile.openWriter();
                    
                    AnnotationMirror annotation = findAnnotation(element, GenerateClass.class, elementUtils, typeUtils);
                    generateClassDefinition(writer, packageName, definition, annotation, className, typeElement, elementUtils, typeUtils);

                    boolean isHavingSuperClass = isHavingSuperClass(elementUtils, typeUtils, annotation);
                    
                    int shift = 1;
                    if(generateAnnotation.threadSafe()) {
                        shift = generateAtomicFields(writer, shift, definition, typeElement, elementUtils, typeUtils);
                        shift = generateAtomicConstructors(writer, shift, className, definition, typeElement, elementUtils, typeUtils);
                        shift = generateAtomicPropertyAccessors(writer, shift, definition, typeElement, elementUtils, typeUtils);
                        shift = generateAtomicHashCode(writer, shift, definition, className, isHavingSuperClass);
                        shift = generateAtomicEquals(writer, shift, definition, className, isHavingSuperClass);
                    } else {
                        shift = generateFields(writer, shift, definition, typeElement, elementUtils, typeUtils);
                        shift = generateConstructors(writer, shift, className, definition, typeElement, elementUtils, typeUtils);
                        shift = generatePropertyAccessors(writer, shift, definition, typeElement, elementUtils, typeUtils);
                        shift = generateHashCode(writer, shift, definition, className, isHavingSuperClass);
                        shift = generateEquals(writer, shift, definition, className, isHavingSuperClass);
                    }
                    
                    shift = generateToString(writer, shift, definition, className, isHavingSuperClass);

                    if(isPropertyChangeEventAware(typeElement, elementUtils, typeUtils)) {
                        shift = generatePropertyListenerAccessors(writer, shift, definition, typeElement, elementUtils, typeUtils);
                    }

                    writer.append("}");
                    writer.flush();
                } catch (IOException ex) {
                    Logger.getLogger(PropertyVisitor.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    IO.close(writer, this);
                }
                processed = true;
            }
        }
        return processed;
    }
    
    protected String toAtomicAssignableValue(Property property) {
        return toAtomicAssignableValue(property, "");
    }

    protected String toAtomicAssignableValue(Property property, String prefix) {
        switch(property.getType().getKind()) {
        case SHORT:
        case CHAR:
        case BYTE:
            return "(int)" + toVariableName(property, prefix);
        case FLOAT:
            return "Float.floatToIntBits(" + toVariableName(property, prefix) + ")";
        case DOUBLE:
            return "Double.doubleToLongBits(" + toVariableName(property, prefix) + ")";
        default:
            return retain(property);
        }
    }

    protected String fieldToAtomicAssignableValue(Property property, String prefix) {
        switch(property.getType().getKind()) {
        case SHORT:
        case CHAR:
        case BYTE:
            return "(int)" + toAtomicGet(property, prefix);
        case FLOAT:
            return "Float.floatToIntBits(" + toAtomicGet(property, prefix) + ")";
        case DOUBLE:
            return "Double.doubleToLongBits(" + toAtomicGet(property, prefix) + ")";
        default:
            return retain(property);
        }
    }
    
    private String toVariableName(Property property, String prefix) {
        return prefix.isEmpty() 
                ? toSafeName(property.getName()) 
                : prefix + toSafeName(property.getName());
    }
    
    protected String toAtomicGet(Property property, String prefix) {
        switch(property.getType().getKind()) {
        case SHORT:
            return toAtomicGetValue(property, toVariableName(property, prefix)  + ".get()");
        case CHAR:
            return toAtomicGetValue(property, toVariableName(property, prefix) + ".get()");
        case BYTE:
            return toAtomicGetValue(property, toVariableName(property, prefix) + ".get()");
        case FLOAT:
            return toAtomicGetValue(property, toVariableName(property, prefix) + ".get()");
        case DOUBLE:
            return toAtomicGetValue(property, toVariableName(property, prefix) + ".get()");
        default:
            return toAtomicGetValue(property, toVariableName(property, prefix) + ".get()");
        }
    }
    
    protected String toAtomicGetValue(Property property, String value) {
        switch(property.getType().getKind()) {
        case SHORT:
            return "(short)" + value;
        case CHAR:
            return "(char)" + value;
        case BYTE:
            return "(byte)" + value;
        case FLOAT:
            return "Float.intBitsToFloat(" + value + ")";
        case DOUBLE:
            return "Double.longBitsToDouble(" + value + ")";
        default:
            return value;
        }
    }

    private boolean isHavingSuperClass(Elements elementUtils, Types typeUtils, AnnotationMirror annotation) {
        return !getSuperClassValue(elementUtils, typeUtils, elementUtils.getElementValuesWithDefaults(annotation)).isEmpty();
    }

    private boolean isPropertyChangeEventAware(TypeElement element, Elements elementUtil, Types typeUtils) {
        TypeElement propertyAware = elementUtil.getTypeElement(PropertyChangeEventAware.class.getName());
        for (TypeMirror intrface : element.getInterfaces()) {
            if(typeUtils.isSubtype(intrface, propertyAware.asType())) {
                return true;
            }
        }
        return false;
    }

    private boolean isAbstract(InterfaceDefinition definition) {
        return definition.getMethods().isEmpty() && !definition.isHavingIgnoredProperty();
    }

    private void generateClassDefinition(Writer writer, String packageName, InterfaceDefinition definition, AnnotationMirror annotation, String className, Element element, Elements elementUtils, Types typeUtils) throws IOException {
        String generationTime = String.format("%1$tFT%1$tH:%1$tM:%1$tS.%1$tL%1$tz", new Date()); //%1$tY%1$tm%1$td-%1$tH%1$tk%1$tS-%1$tN%1$tz

        writer.append("package ").append(packageName).append(";\n\n");
        writer.append("@javax.annotation.Generated(value = \"" + this.getClass().getName() + "\", date = \"" + generationTime + "\")\n");
        writer.append("public ").append(isAbstract(definition) ? "" : "abstract ").append("class ").append(className);

        String superClassName = getSuperClassValue(elementUtils, typeUtils, elementUtils.getElementValuesWithDefaults(annotation));
        if(!superClassName.isEmpty()) {
            writer.append(" extends ").append(superClassName);
        }

        writer.append(" implements ").append(definition.getPackage() + "." + definition.getInterfaceName())
              .append(" {\n");
    }

    protected int generateFields(Writer writer, int shift, InterfaceDefinition definition, TypeElement element, Elements elementUtils, Types typeUtils) throws IOException {
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                String typeName = property.getType().toString();
                writer.append(indent(shift)).append("private ").append(typeName).append(" ").append(toSafeName(property.getName())).append(";\n");
            }
        }

        if(isPropertyChangeEventAware(element, elementUtils, typeUtils)) {
            generatePropertySupportField(elementUtils, writer, shift);
        }

        writer.append("\n");
        return shift;
    }
    
    protected int generateAtomicFields(Writer writer, int shift, InterfaceDefinition definition, TypeElement element, Elements elementUtils, Types typeUtils) throws IOException {
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                final String typeName = getAtomicTypeFor(property.getType(), elementUtils);
                writer.append(indent(shift)).append("private ");
                writer.append(typeName)
                      .append(" ").append(toSafeName(property.getName())).append(" = new ").append(typeName).append("();\n");
            }
        }
        
        if(isPropertyChangeEventAware(element, elementUtils, typeUtils)) {
            generatePropertySupportField(elementUtils, writer, shift);
        }

        writer.append("\n");
        return shift;
    }    
    
    protected void generatePropertySupportField(Elements elementUtils, Writer writer, int shift) throws IOException {
        TypeMirror supportClass = elementUtils.getTypeElement(PropertyChangeSupport.class.getName()).asType();
        writer.append(indent(shift)).append("private ").append(supportClass.toString()).append(" propertySupport;\n");
    }
    
    protected final String getAtomicTypeFor(TypeMirror type, Elements elementUtils) {
        switch(type.getKind()) {
            case BOOLEAN:
                return elementUtils.getTypeElement(AtomicBoolean.class.getName()).asType().toString();
            case INT:
            case SHORT:
            case FLOAT:
            case CHAR:
            case BYTE:
                return elementUtils.getTypeElement(AtomicInteger.class.getName()).asType().toString();
            case LONG:
            case DOUBLE:
                return elementUtils.getTypeElement(AtomicLong.class.getName()).asType().toString();
            case DECLARED:
            default:
            {
                final String typeStr = type.toString();
                final String atomicType = "java.util.concurrent.atomic.AtomicReference<" + typeStr + ">";
                return atomicType;
            }
        }
    }
    
    protected int generatePropertyAccessors(Writer writer, int shift, InterfaceDefinition definition, TypeElement element, Elements elementUtils, Types typeUtils) throws IOException {
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                String fieldName = toSafeName(property.getName());
                String propertyType = property.getType().toString();

                if(property.isReadable()) {
                    writer.append(indent(shift)).append("@Override\n");
                    writer.append(indent(shift)).append("public ").append(propertyType).append(isBoolean(property.getType(), typeUtils) ? " is" : " get").append(capitalize(property.getName())).append("() {\n");
                    writer.append(indent(++shift)).append("return ").append(retain(property, "this.")).append(";\n");
                    writer.append(indent(--shift)).append("}\n\n");
                }

                if(property.isWritable()) {
                    boolean propertySupport = isPropertyChangeEventAware(element, elementUtils, typeUtils);
                    writer.append(indent(shift)).append("@Override\n");
                    writer.append(indent(shift)).append("public void set").append(capitalize(property.getName())).append("(").append(propertyType).append(" ").append(fieldName).append(") {\n");

                    shift++;
                    
                    writer.append(indent(shift++)).append("if (this.").append(fieldName).append(" != ").append(fieldName);
                    if(!isPrimitive(property.getType())) {
                        writer.append(" && (").append("this.").append(fieldName).append(" != null && !this.").append(fieldName).append(".equals(").append(fieldName).append("))");
                    }
                    writer.append(") {\n");
                    
                    if(propertySupport) {
                        writer.append(indent(shift)).append(propertyType).append(" oldValue = this.").append(fieldName).append(";\n");
                    }

                    writer.append(indent(shift)).append("this.").append(fieldName).append(" = ").append(retain(property)).append(";\n");

                    if(isPropertyChangeEventAware(element, elementUtils, typeUtils)) {
                        writer.append(indent(shift)).append("this.propertySupport.firePropertyChange(\"").append(property.getName()).append("\", oldValue, this.").append(fieldName).append(");\n");
                    }

                    writer.append(indent(--shift)).append("}\n");
                    writer.append(indent(--shift)).append("}\n\n");
                }
            }
        }
        return shift;
    }
    
    protected int generateAtomicPropertyAccessors(Writer writer, int shift, InterfaceDefinition definition, TypeElement element, Elements elementUtils, Types typeUtils) throws IOException {
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                String fieldName = toSafeName(property.getName());
                String propertyType = property.getType().toString();
                
                if(property.isReadable()) {
                    RetainType type = RetainType.valueOf(property.getRetainType());
                    writer.append(indent(shift)).append("@Override\n");
                    writer.append(indent(shift)).append("public ").append(propertyType).append(isBoolean(property.getType(), typeUtils) ? " is" : " get").append(capitalize(property.getName())).append("() {\n");
                    writer.append(indent(++shift)).append("return ").append(type.codeFor(toAtomicGet(property, "this."), property)).append(";\n");
                    writer.append(indent(--shift)).append("}\n\n");
                }

                if(property.isWritable()) {
                    boolean propertySupport = isPropertyChangeEventAware(element, elementUtils, typeUtils);
                    writer.append(indent(shift)).append("@Override\n");
                    writer.append(indent(shift)).append("public void set").append(capitalize(property.getName())).append("(").append(propertyType).append(" ").append(fieldName).append(") {\n");

                    shift++;
                    
                    writer.append(indent(shift));
                    String valueType = propertyType;
                    switch(property.getType().getKind()) {
                        case SHORT:
                        case CHAR:
                        case BYTE:
                        case FLOAT:
                            valueType = "int";
                            break;
                        case DOUBLE:
                            valueType = "long";
                            break;
                    }
                    
                    writer.append(valueType).append(" currentValue = ").append(toAtomicAssignableValue(property)).append(";\n");
                    writer.append(indent(shift)).append(valueType).append(" oldValue = ").append(toVariableName(property, "this.")).append(".getAndSet(currentValue);\n");

                    if(propertySupport) {
                        writer.append(indent(shift)).append("if (oldValue != ").append(fieldName);
                        if(!isPrimitive(property.getType())) {
                            writer.append(" && (").append("oldValue != null && !oldValue.equals(").append(fieldName).append("))");
                        }
                        writer.append(") {\n");
                        
                        writer.append(indent(++shift)).append("this.propertySupport.firePropertyChange(\"").append(property.getName()).append("\", " + toAtomicGetValue(property, "oldValue") + ", " + toAtomicGetValue(property, "currentValue") + ");\n");
                        writer.append(indent(--shift)).append("}\n");
                    }
                    
                    writer.append(indent(--shift)).append("}\n\n");
                }
            }
        }
        return shift;
    }    

    protected int generateAtomicConstructors(Writer writer, int shift, String className, InterfaceDefinition definition, TypeElement element, Elements elementUtils, Types typeUtils) throws IOException {
        shift = generateAtomicFullArgConstructor(writer, shift, className, definition, element, elementUtils, typeUtils);

        int readablePropertyCount = 0;
        int writablePropertyCount = 0;
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                if(property.isReadable()) readablePropertyCount++;
                if(property.isWritable()) writablePropertyCount++;
            }
        }

        if(readablePropertyCount >= writablePropertyCount) {
            shift = generateAtomicReadOnlyFieldConstructor(writer, shift, className, definition, element, elementUtils, typeUtils);
        }

        return shift;
    }    
    
    protected int generateConstructors(Writer writer, int shift, String className, InterfaceDefinition definition, TypeElement element, Elements elementUtils, Types typeUtils) throws IOException {
        shift = generateFullArgConstructor(writer, shift, className, definition, element, elementUtils, typeUtils);

        int readablePropertyCount = 0;
        int writablePropertyCount = 0;
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                if(property.isReadable()) readablePropertyCount++;
                if(property.isWritable()) writablePropertyCount++;
            }
        }

        if(readablePropertyCount >= writablePropertyCount) {
            shift = generateReadOnlyFieldConstructor(writer, shift, className, definition, element, elementUtils, typeUtils);
        }

        return shift;
    }
    
    private void generatePropertySupport(Writer writer, int shift, Elements elementUtils) throws IOException {
        TypeMirror type = elementUtils.getTypeElement(PropertyChangeSupport.class.getName()).asType();
        writer.append(indent(shift)).append("this.propertySupport = ").append("new ").append(type.toString()).append("(this);\n");
    }

    protected int generateFullArgConstructor(Writer writer, int shift, String className, InterfaceDefinition definition, TypeElement element, Elements elementUtils, Types typeUtils) throws IOException {
        writer.append(indent(shift)).append("public ").append(className).append("(");
        boolean isFirst = true;
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                String type = property.getType().toString();
                if(!isFirst) {
                    writer.append(", ");
                } else {
                    isFirst = false;
                }
                writer.append(type).append(" ").append(toSafeName(property.getName()));
            }
        }
        writer.append(") {\n");
        writer.append(indent(++shift)).append("super();\n");
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
               writer.append(indent(shift)).append("this.").append(toSafeName(property.getName())).append(" = ").append(retain(property)).append(";\n");
            }
        }

        if(isPropertyChangeEventAware(element, elementUtils, typeUtils)) {
            generatePropertySupport(writer, shift, elementUtils);
        }

        writer.append(indent(--shift)).append("}\n\n");
        return shift;
    }
    
    protected int generateAtomicFullArgConstructor(Writer writer, int shift, String className, InterfaceDefinition definition, TypeElement element, Elements elementUtils, Types typeUtils) throws IOException {
        writer.append(indent(shift)).append("public ").append(className).append("(");
        boolean isFirst = true;
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                String type = property.getType().toString();
                if(!isFirst) {
                    writer.append(", ");
                } else {
                    isFirst = false;
                }
                writer.append(type).append(" ").append(toSafeName(property.getName()));
            }
        }
        writer.append(") {\n");
        writer.append(indent(++shift)).append("super();\n");
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                writer.append(indent(shift)).append("this.").append(toSafeName(property.getName())).append(".set(");
                writer.append(toAtomicAssignableValue(property));
                writer.append(");\n");
            }
        }

        if(isPropertyChangeEventAware(element, elementUtils, typeUtils)) {
            generatePropertySupport(writer, shift, elementUtils);
        }

        writer.append(indent(--shift)).append("}\n\n");
        return shift;        
    }
   

    /**
     * Generate source of a constructor having parameters for all read-only properties.<br/>
     * If there is no read-only property, a default no-arg constructor is generated.
     *
     * @param writer source-writer
     * @param shift current count of indents.
     * @param className generated class name
     * @param definition InterfaceDefinition which have all information about an interface for generating.
     * @return last indent-count
     * @throws IOException occurs when this method could not write to a writer.
     */
    protected int generateReadOnlyFieldConstructor(Writer writer, int shift, String className, InterfaceDefinition definition, TypeElement element, Elements elementUtils, Types typeUtils) throws IOException {
        writer.append(indent(shift)).append("public ").append(className).append("(");
        boolean isFirst = true;
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                if(!property.isWritable() && property.isReadable()) {
                    String type = property.getType().toString();
                    if(!isFirst) {
                        writer.append(", ");
                    } else {
                        isFirst = false;
                    }
                    writer.append(type).append(" ").append(toSafeName(property.getName()));
                }
            }
        }
        writer.append(") {\n");
        shift++;
        writer.append(indent(shift)).append("super();\n");
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                if(!property.isWritable() && property.isReadable()) {
                    writer.append(indent(shift)).append("this.").append(toSafeName(property.getName())).append(" = ").append(retain(property)).append(";\n");
                }
            }
        }

        if(isPropertyChangeEventAware(element, elementUtils, typeUtils)) {
            generatePropertySupport(writer, shift, elementUtils);
        }

        writer.append(indent(--shift)).append("}\n\n");
        return shift;
    }
    
    protected int generateAtomicReadOnlyFieldConstructor(Writer writer, int shift, String className, InterfaceDefinition definition, TypeElement element, Elements elementUtils, Types typeUtils) throws IOException {
        writer.append(indent(shift)).append("public ").append(className).append("(");
        boolean isFirst = true;
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                if(!property.isWritable() && property.isReadable()) {
                    String type = property.getType().toString();
                    if(!isFirst) {
                        writer.append(", ");
                    } else {
                        isFirst = false;
                    }
                    writer.append(type).append(" ").append(toSafeName(property.getName()));
                }
            }
        }
        writer.append(") {\n");
        shift++;
        writer.append(indent(shift)).append("super();\n");
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                if(!property.isWritable() && property.isReadable()) {
                    writer.append(indent(shift)).append("this.").append(toSafeName(property.getName())).append(".set(");
                    writer.append(toAtomicAssignableValue(property));
                    writer.append(");\n");                
                }
            }
        }

        if(isPropertyChangeEventAware(element, elementUtils, typeUtils)) {
            generatePropertySupport(writer, shift, elementUtils);
        }

        writer.append(indent(--shift)).append("}\n\n");
        return shift;        
    }
    
    protected int generateDefaultConstructor(Writer writer, int shift, String className, InterfaceDefinition definition) throws IOException {
        writer.append(indent(shift)).append("public ").append(className).append("(").append(") {\n")
              .append(indent(++shift)).append("super();\n")
              .append(indent(--shift)).append("}\n\n");
        return shift;
    }

    
    protected int generateAtomicEquals(Writer writer, int shift, InterfaceDefinition definition, String className, boolean isHavingSuperClass) throws IOException {

        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift)).append("public boolean equals(Object obj) {\n")
              .append(indent(++shift)).append("if (obj == null) {\n")
              .append(indent(++shift)).append("return false;\n")
              .append(indent(--shift)).append("}\n\n")
              .append(indent(shift)).append("if (!(obj instanceof ").append(className).append(")) {\n")
              .append(indent(++shift)).append("return false;\n")
              .append(indent(--shift)).append("}\n\n")
              .append(indent(shift)).append("final ").append(className).append(" other = (").append(className).append(") obj;\n");

        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                final String fieldName = toSafeName(property.getName());
                if(isPrimitive(property.getType())) {
                    switch(property.getType().getKind()) {
                        case FLOAT:
                        case DOUBLE:
                            writer.append(indent(shift)).append("if (").append(toVariableName(property, "this.")).append(".get() != ").append(toVariableName(property, "other.")).append(".get()) {\n")
                                  .append(indent(++shift)).append("return false;\n")
                                  .append(indent(--shift)).append("}\n");
                            break;
                        default:
                            writer.append(indent(shift)).append("if (").append(toAtomicGet(property, "this.")).append(" != ").append(toAtomicGet(property, "other.")).append(") {\n")
                                  .append(indent(++shift)).append("return false;\n")
                                  .append(indent(--shift)).append("}\n");
                    }
                } else {
                    writer.append(indent(shift)).append("if (").append(toAtomicGet(property, "this.")).append(" != ").append(toAtomicGet(property, "other.")).append(" && (").append(toAtomicGet(property, "this.")).append(" == null || !").append(toAtomicGet(property, "this.")).append(".equals(").append(toAtomicGet(property, "other.")).append("))) {\n")
                        .append(indent(++shift)).append("return false;\n")
                        .append(indent(--shift)).append("}\n");
                }
            }
        }
        
        if(isHavingSuperClass) {
            writer.append(indent(shift)).append("if (!super.equals(obj)) {\n")
                  .append(indent(++shift)).append("return false;\n")
                  .append(indent(--shift)).append("}\n");
        }

        writer.append(indent(shift)).append("return true;\n");
        writer.append(indent(--shift)).append("}\n\n");
        return shift;
    }
    
    protected int generateEquals(Writer writer, int shift, InterfaceDefinition definition, String className, boolean isHavingSuperClass) throws IOException {

        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift)).append("public boolean equals(Object obj) {\n")
              .append(indent(++shift)).append("if (obj == null) {\n")
              .append(indent(++shift)).append("return false;\n")
              .append(indent(--shift)).append("}\n\n")
              .append(indent(shift)).append("if (!(obj instanceof ").append(className).append(")) {\n")
              .append(indent(++shift)).append("return false;\n")
              .append(indent(--shift)).append("}\n\n")
              .append(indent(shift)).append("final ").append(className).append(" other = (").append(className).append(") obj;\n");

        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                final String fieldName = toSafeName(property.getName());
                if(isPrimitive(property.getType())) {
                    switch(property.getType().getKind()) {
                        case FLOAT:
                            writer.append(indent(shift)).append("if (Float.floatToIntBits(this.").append(fieldName).append(") != Float.floatToIntBits(other.").append(fieldName).append(")) {\n")
                                .append(indent(++shift)).append("return false;\n")
                                .append(indent(--shift)).append("}\n");
                            break;
                        case DOUBLE:
                            writer.append(indent(shift)).append("if (Double.doubleToLongBits(this.").append(fieldName).append(") != Double.doubleToLongBits(other.").append(fieldName).append(")) {\n")
                                .append(indent(++shift)).append("return false;\n")
                                .append(indent(--shift)).append("}\n");
                            break;
                        default:
                            writer.append(indent(shift)).append("if (this.").append(fieldName).append(" != other.").append(fieldName).append(") {\n")
                                .append(indent(++shift)).append("return false;\n")
                                .append(indent(--shift)).append("}\n");
                    }
                } else {
                    writer.append(indent(shift)).append("if (this.").append(fieldName).append(" != other.").append(fieldName).append(" && (this.").append(fieldName).append(" == null || !this.").append(fieldName).append(".equals(other.").append(fieldName).append("))) {\n")
                        .append(indent(++shift)).append("return false;\n")
                        .append(indent(--shift)).append("}\n");
                }
            }
        }
        
        if(isHavingSuperClass) {
            writer.append(indent(shift)).append("if (!super.equals(obj)) {\n")
                  .append(indent(++shift)).append("return false;\n")
                  .append(indent(--shift)).append("}\n");
        }

        writer.append(indent(shift)).append("return true;\n");
        writer.append(indent(--shift)).append("}\n\n");
        return shift;
    }

    protected int generateHashCode(Writer writer, int shift, InterfaceDefinition definition, String className, boolean isHavingSuperClass) throws IOException {
        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift)).append("public int hashCode() {\n")
              .append(indent(++shift)).append("int result = 17;\n");

        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                final String fieldName = toSafeName(property.getName());
                String expression = null;
                if(isPrimitive(property.getType())) {
                    switch(property.getType().getKind()) {
                        case FLOAT:
                            expression = "Float.floatToIntBits(this." + fieldName + ")";
                            break;
                        case DOUBLE:
                            expression = "(int) (Double.doubleToLongBits(this." + fieldName + ") ^ (Double.doubleToLongBits(this." + fieldName + ") >>> 32))";
                            break;
                        case BOOLEAN:
                            expression = "(this." + fieldName + " ? 1 : 0)";
                            break;
                        case LONG:
                            expression = "(int) (this." + fieldName + " ^ (this." + fieldName + " >>> 32))";
                            break;
                        default:
                            expression = "this." + fieldName;
                    }
                } else {
                    expression = "(this." + fieldName + " != null ? this." + fieldName + ".hashCode() : 0)";
                }
                writer.append(indent(shift)).append("result = 31 * result + ").append(expression).append(";\n");
            }
        }

        if(isHavingSuperClass) {
            writer.append(indent(shift)).append("result = 31 * result + ").append("super.hashCode();\n");
        }

        writer.append(indent(shift)).append("return result;\n");
        writer.append(indent(--shift)).append("}\n\n");

        return shift;
    }
    
    protected int generateAtomicHashCode(Writer writer, int shift, InterfaceDefinition definition, String className, boolean isHavingSuperClass) throws IOException {
        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift)).append("public int hashCode() {\n")
              .append(indent(++shift)).append("int result = 17;\n");

        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                final String fieldName = toSafeName(property.getName());
                String expression = null;
                if(isPrimitive(property.getType())) {
                    switch(property.getType().getKind()) {
                        case FLOAT:
                            expression = toVariableName(property, "this.") + ".get()";
                            break;
                        case DOUBLE:
                            expression = "(int) (" + toVariableName(property, "this.") + ".get() ^ (" + toVariableName(property, "this.") + ".get() >>> 32))";
                            break;
                        case BOOLEAN:
                            expression = "(" + toAtomicGet(property, "this.") + " ? 1 : 0)";
                            break;
                        case LONG:
                            expression = "(int) (" + toAtomicGet(property, "this.") + " ^ (" + toAtomicGet(property, "this.") + " >>> 32))";
                            break;
                        default:
                            expression = toAtomicGet(property, "this.");
                    }
                } else {
                    expression = "(" + toAtomicGet(property, "this.") + " != null ? " + toAtomicGet(property, "this.") + ".hashCode() : 0)";
                }
                writer.append(indent(shift)).append("result = 31 * result + ").append(expression).append(";\n");
            }
        }

        if(isHavingSuperClass) {
            writer.append(indent(shift)).append("result = 31 * result + ").append("super.hashCode();\n");
        }

        writer.append(indent(shift)).append("return result;\n");
        writer.append(indent(--shift)).append("}\n\n");

        return shift;
    }    

    protected int generateToString(Writer writer, int shift, InterfaceDefinition definition, String className, boolean isHavinsSuperClass) throws IOException {
        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift)).append("public String toString() {\n")
              .append(indent(++shift)).append("return \"").append(className).append("{\"\n");

        shift++;
        boolean isFirst = true;
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                final String safeName = toSafeName(property.getName());
                writer.append(indent(shift)).append(" + \"");
                if(!isFirst) writer.append(", ");
                writer.append(safeName).append("=\" + ").append(safeName).append("\n");
                if(isFirst) isFirst = false;
            }
        }
        if(isHavinsSuperClass) {
            writer.append(indent(shift)).append(" + \"");
            if(!isFirst) writer.append(", ");
            writer.append("superClass=\" + ").append("super.toString()\n");
            
        }
        writer.append(indent(shift)).append(" + '}';\n");
        shift--;

        writer.append(indent(--shift)).append("}\n\n");

        return shift;
    }

    protected int generatePropertyListenerAccessors(Writer writer, int shift, InterfaceDefinition definition, TypeElement element, Elements elementUtils, Types typeUtils) throws IOException {
        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift++)).append("public void addPropertyChangeListener(java.beans.PropertyChangeListener... listeners) {\n")
              .append(indent(shift)).append("if(listeners == null) throw new IllegalArgumentException(\"the argument 'listeners' should not be null.\");\n")
              .append(indent(shift)).append("for (java.beans.PropertyChangeListener listener : listeners) {\n")
              .append(indent(++shift)).append("this.propertySupport.addPropertyChangeListener(listener);\n")
              .append(indent(--shift)).append("}\n")
              .append(indent(--shift)).append("}\n\n");

        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift++)).append("public void addPropertyChangeListener(String propertyName, java.beans.PropertyChangeListener... listeners) {\n")
              .append(indent(shift)).append("if(propertyName == null) throw new IllegalArgumentException(\"the argument 'propertyName' should not be null.\");\n")
              .append(indent(shift)).append("if(listeners == null) throw new IllegalArgumentException(\"the argument 'listeners' should not be null.\");\n")
              .append(indent(shift)).append("for (java.beans.PropertyChangeListener listener : listeners) {\n")
              .append(indent(++shift)).append("this.propertySupport.addPropertyChangeListener(propertyName, listener);\n")
              .append(indent(--shift)).append("}\n")
              .append(indent(--shift)).append("}\n\n");

        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift++)).append("public java.util.Collection<java.beans.PropertyChangeListener> getPropertyChangeListeners() {\n")
              .append(indent(shift)).append("return java.util.Arrays.asList(this.propertySupport.getPropertyChangeListeners());\n")
              .append(indent(--shift)).append("}\n\n");

        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift++)).append("public java.util.Collection<java.beans.PropertyChangeListener> getPropertyChangeListeners(String propertyName) {\n")
              .append(indent(shift)).append("if(propertyName == null) throw new IllegalArgumentException(\"the argument 'propertyName' should not be null.\");\n")
              .append(indent(shift)).append("return java.util.Arrays.asList(this.propertySupport.getPropertyChangeListeners(propertyName));\n")
              .append(indent(--shift)).append("}\n\n");

        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift++)).append("public boolean hasListeners(String propertyName) {\n")
              .append(indent(shift)).append("if(propertyName == null) throw new IllegalArgumentException(\"the argument 'propertyName' should not be null.\");\n")
              .append(indent(shift)).append("return this.propertySupport.hasListeners(propertyName);\n")
              .append(indent(--shift)).append("}\n\n");

        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift++)).append("public void removePropertyChangeListener(java.beans.PropertyChangeListener... listeners) {\n")
              .append(indent(shift)).append("if(listeners == null) throw new IllegalArgumentException(\"the argument 'listeners' should not be null.\");\n")
              .append(indent(shift)).append("for (java.beans.PropertyChangeListener listener : listeners) {\n")
              .append(indent(++shift)).append("this.propertySupport.removePropertyChangeListener(listener);\n")
              .append(indent(--shift)).append("}\n")
              .append(indent(--shift)).append("}\n\n");

        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift++)).append("public void removePropertyChangeListener(String propertyName, java.beans.PropertyChangeListener... listeners) {\n")
              .append(indent(shift)).append("if(propertyName == null) throw new IllegalArgumentException(\"the argument 'propertyName' should not be null.\");\n")
              .append(indent(shift)).append("if(listeners == null) throw new IllegalArgumentException(\"the argument 'listeners' should not be null.\");\n")
              .append(indent(shift)).append("for (java.beans.PropertyChangeListener listener : listeners) {\n")
              .append(indent(++shift)).append("this.propertySupport.removePropertyChangeListener(propertyName, listener);\n")
              .append(indent(--shift)).append("}\n")
              .append(indent(--shift)).append("}\n\n");

        return shift;
    }


    protected AnnotationMirror findAnnotation(Element element, Class<?> annotationClass, Elements elementUtils, Types typeUtils) {
        TypeElement propertyAnnotationType = elementUtils.getTypeElement(annotationClass.getName());
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if(typeUtils.isSameType(propertyAnnotationType.asType(), mirror.getAnnotationType())) {
                return mirror;
            }
        }
        return null;
    }

    protected String getSuperClassValue(Elements elementUtils, Types typeUtils, Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {
        AnnotationValue superClassValue = getValueOfAnnotation(elementValues, "superClass");
        assert superClassValue != null;
        TypeMirror superClassMirror = (TypeMirror) superClassValue.getValue();
        TypeMirror voidType = elementUtils.getTypeElement(Void.class.getName()).asType();

        if(typeUtils.isSameType(voidType, superClassMirror)) {
            return "";
        }

        return superClassMirror.toString();
    }


    protected AnnotationValue getValueOfAnnotation(Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues, String keyName) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : elementValues.entrySet()) {
            ExecutableElement key = entry.getKey();
            if(key.getSimpleName().toString().equals(keyName)) {
                return entry.getValue();
            }
        }
        return null;
    }


    protected String retain(Property property) {
        return retain(property, "");
    }

    protected String retain(Property property, String prefix) {
        assert property != null;

        String safeName = prefix + toSafeName(property.getName());
        if(isPrimitive(property.getType())) return safeName;
        
        RetainType type = RetainType.valueOf(property.getRetainType());
        return type.codeFor(safeName, property);
    }
    
    protected String toSafeName(String word) {
        return Objects.isPreserved(word) ? "_" + word : word;
    }
    
    protected final boolean isBoolean(TypeMirror type, Types typeUtils) {
        switch(type.getKind()) {
            case BOOLEAN:
                return true;
            default:
                return typeUtils.isSameType(type, typeUtils.boxedClass(typeUtils.getPrimitiveType(TypeKind.BOOLEAN)).asType());
        }
    }
    
    protected final boolean isPrimitive(TypeMirror type) {
        switch(type.getKind()) {
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case DOUBLE:
            case FLOAT:
            case INT:
            case LONG:
            case SHORT:
            case VOID:
                return true;
            default:
                return false;
        }
    }

    protected final String indent(int indent) {
        if(indent <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < indent; i++) {
            sb.append("    ");
        }
        return sb.toString();
    }

    protected String resolvePackageName(GenerateClass domain, InterfaceDefinition definition) {
        String interfacePackageName = definition.getPackage();
        String packageName = domain.packageName();
        boolean isRelative = domain.packageNameRelative();

        if(packageName.isEmpty()) {
            return interfacePackageName + ".impl";
        } else {
            if(isRelative) {
                return interfacePackageName + "." + packageName;
            } else {
                return packageName;
            }
        }
    }

    protected String resolveImplementationClassName(GenerateClass annotation, InterfaceDefinition definition) {
        String interfaceName = definition.getInterfaceName();
        String className = annotation.className();
        if(className.isEmpty()) {
            if(definition.getMethods().isEmpty() && !definition.isHavingIgnoredProperty()) {
                return getClassNamePrefix() + capitalize(interfaceName) + getClassNameSuffix();
            } else {
                return getAbstractClassNamePrefix() + capitalize(interfaceName) + getAbstractClassNameSuffix();
            }
        } else {
            return className;
        }
    }

    protected String getClassNamePrefix() { return "Default"; }
    protected String getClassNameSuffix() { return ""; }
    protected String getAbstractClassNamePrefix() { return "Abstract"; }
    protected String getAbstractClassNameSuffix() { return getClassNameSuffix(); }
}
