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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.*;
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

                    generateClassDefinition(writer, packageName, definition, className, typeElement, elementUtils, typeUtils);

                    int shift = 1;
                    shift = generateFields(writer, shift, definition, typeElement, elementUtils, typeUtils);
                    shift = generateConstructors(writer, shift, className, definition, typeElement, elementUtils, typeUtils);
                    shift = generatePropertyAccessors(writer, shift, definition, typeElement, elementUtils, typeUtils);
                    shift = generateEquals(writer, shift, definition, className);
                    shift = generateHashCode(writer, shift, definition, className);
                    shift = generateToString(writer, shift, definition, className);

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

    private void generateClassDefinition(Writer writer, String packageName, InterfaceDefinition definition, String className, Element element, Elements elementUtils, Types typeUtils) throws IOException {
        String generationTime = String.format("%1$tFT%1$tH:%1$tM:%1$tS.%1$tL%1$tz", new Date()); //%1$tY%1$tm%1$td-%1$tH%1$tk%1$tS-%1$tN%1$tz

        writer.append("package ").append(packageName).append(";\n\n");
        writer.append("@javax.annotation.Generated(value = \"" + this.getClass().getName() + "\", date = \"" + generationTime + "\")\n");
        writer.append("public ").append(isAbstract(definition) ? "" : "abstract ").append("class ").append(className);

        AnnotationMirror mirror = findAnnotation(element, GenerateClass.class, elementUtils, typeUtils);
        String superClassName = getSuperClassValue(elementUtils, typeUtils, elementUtils.getElementValuesWithDefaults(mirror));
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
            TypeMirror supportClass = elementUtils.getTypeElement(PropertyChangeSupport.class.getName()).asType();
            writer.append(indent(shift)).append("private ").append(supportClass.toString()).append(" propertySupport;\n");
        }

        writer.append("\n");
        return shift;
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
                    if(propertySupport) {
                        writer.append(indent(shift)).append(propertyType).append(" oldValue = this.").append(fieldName).append(";\n");
                    }

                    writer.append(indent(shift)).append("this.").append(fieldName).append(" = ").append(retain(property)).append(";\n");

                    if(isPropertyChangeEventAware(element, elementUtils, typeUtils)) {
                        writer.append(indent(shift)).append("this.propertySupport.firePropertyChange(\"").append(property.getName()).append("\", oldValue, this.").append(fieldName).append(");\n");
                    }

                    writer.append(indent(--shift)).append("}\n\n");
                }
            }
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

    protected int generateDefaultConstructor(Writer writer, int shift, String className, InterfaceDefinition definition) throws IOException {
        writer.append(indent(shift)).append("public ").append(className).append("(").append(") {\n")
              .append(indent(++shift)).append("super();\n")
              .append(indent(--shift)).append("}\n\n");
        return shift;
    }

    protected int generateEquals(Writer writer, int shift, InterfaceDefinition definition, String className) throws IOException {

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

        writer.append(indent(shift)).append("return true;\n");
        writer.append(indent(--shift)).append("}\n\n");
        return shift;
    }

    protected int generateHashCode(Writer writer, int shift, InterfaceDefinition definition, String className) throws IOException {
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

        writer.append(indent(shift)).append("return result;\n");
        writer.append(indent(--shift)).append("}\n\n");

        return shift;
    }

    protected int generateToString(Writer writer, int shift, InterfaceDefinition definition, String className) throws IOException {
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
        boolean isRelative = domain.isPackageNameRelative();

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
