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
import com.shelfmap.interfaceprocessor.impl.DefaultInterfaceFilter;
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

    private static final String INSTANCE_LOCK = "instanceLock";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment re) {
        if(annotations.isEmpty()) return false;

        Elements elementUtils = processingEnv.getElementUtils();
        Types typeUtils = processingEnv.getTypeUtils();

        boolean processed = false;
        Set<? extends Element> elements = re.getElementsAnnotatedWith(GenerateClass.class);
        for (Element element : elements) {
            if(element.getKind() == ElementKind.INTERFACE) {
                TypeElement targetInterface = (TypeElement)element;
                GenerateClass generateAnnotation = targetInterface.getAnnotation(GenerateClass.class);
                assert generateAnnotation != null;

                if(!generateAnnotation.autoGenerate()) continue;

                InterfaceDefinition definition = new DefaultInterfaceDefinition();
                Environment visitorEnvironment = new BuildingEnvironment(processingEnv, definition);
                PropertyVisitor visitor = new PropertyVisitor(createInterfaceFilter());

                //collect information about the current interface into visitorEnviroment
                visitor.visit(targetInterface, visitorEnvironment);

                AnnotationMirror generateClassAnnotation = findAnnotation(element, GenerateClass.class, elementUtils, typeUtils);
                String packageName = resolvePackageName(generateClassAnnotation, definition);
                String className = resolveImplementationClassName(generateAnnotation, definition);
                String fullClassName = packageName + "." + className;

                if(!precheck(definition, generateClassAnnotation, className, element)) {
                    continue;
                }

                Writer writer = null;
                try {
                    Filer filer = processingEnv.getFiler();
                    JavaFileObject javaFile = filer.createSourceFile(fullClassName, targetInterface);
                    writer = javaFile.openWriter();

                    generateClassDefinition(writer, generateClassAnnotation, definition, className, targetInterface);

                    boolean isHavingSuperClass = isHavingSuperClass(generateClassAnnotation);
                    FieldModifier modifier = generateAnnotation.fieldModifier();
                    int shift = 1;
                    shift = generateFields(writer, shift, generateClassAnnotation, definition, targetInterface, modifier);
                    shift = generateConstructors(writer, shift, generateClassAnnotation, definition, className, targetInterface);
                    shift = generatePropertyAccessors(writer, shift, generateClassAnnotation, definition, targetInterface);
                    shift = generateHashCode(writer, shift, generateClassAnnotation, definition, className, isHavingSuperClass, targetInterface);
                    shift = generateEquals(writer, shift, generateClassAnnotation, definition, className, isHavingSuperClass, targetInterface);
                    shift = generateToString(writer, shift, generateClassAnnotation, definition, className, isHavingSuperClass, targetInterface);

                    if(isCloneable(generateClassAnnotation)) {
                        shift = generateClone(writer, shift, definition, className);
                    }

                    if(isPropertyChangeEventAware(targetInterface)) {
                        shift = generatePropertyListenerAccessors(writer, shift, definition, targetInterface);
                    }

                    shift = generateOthers(writer, shift, generateClassAnnotation, definition, className, isHavingSuperClass, targetInterface);

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

    protected InterfaceFilter createInterfaceFilter() {
        Elements elementUtils = processingEnv.getElementUtils();
        Types typeUtils = processingEnv.getTypeUtils();
        return new DefaultInterfaceFilter(elementUtils, typeUtils);
    }

    protected int generateField(Property property, Writer writer, final int shift, FieldModifier modifier) throws IOException {
        String typeName = property.getType().toString();
        writer.append(indent(shift)).append(modifier.getModifier()).append((modifier == FieldModifier.DEFAULT ? "" : " ")).append(typeName).append(" ").append(toSafeName(property.getName())).append(";\n");
        return shift;
    }

    protected int generateGetter(Writer writer, int shift, TypeElement element, AnnotationMirror generateClassAnnotation, Property property) throws IOException {
        Types typeUtils = processingEnv.getTypeUtils();
        String propertyType = property.getType().toString();
        boolean threadSafe = isThreadSafe(generateClassAnnotation);

        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift)).append("public ").append(propertyType).append(isBoolean(property.getType(), typeUtils) ? " is" : " get").append(capitalize(property.getName())).append("() {\n");
        if(threadSafe) {
            writer.append(indent(++shift)).append(INSTANCE_LOCK).append(".readLock().lock();\n")
                  .append(indent(shift)).append("try {\n");
        }
        writer.append(indent(++shift)).append("return ").append(retain(property, "this.")).append(";\n");
        if(threadSafe) {
            writer.append(indent(--shift)).append("} finally {\n")
                  .append(indent(++shift)).append(INSTANCE_LOCK).append(".readLock().unlock();\n")
                  .append(indent(--shift)).append("}\n");
        }
        writer.append(indent(--shift)).append("}\n\n");
        return shift;
    }

    protected int generateSetter(Writer writer, int shift, TypeElement element, AnnotationMirror generateClassAnnotation, Property property) throws IOException {
        boolean threadSafe = isThreadSafe(generateClassAnnotation);
        String fieldName = toSafeName(property.getName());
        String propertyType = property.getType().toString();

        boolean propertySupport = isPropertyChangeEventAware(element);
        writer.append(indent(shift)).append("@Override\n");
        writer.append(indent(shift++)).append("public void set").append(capitalize(property.getName())).append("(").append(propertyType).append(" ").append(fieldName).append(") {\n");
        boolean isPrimitive = isPrimitive(property.getType());
        String valueType = property.getType().toString();
        if(propertySupport) {
            writer.append(indent(shift)).append(valueType).append(" newValue").append(isPrimitive ? "" : " = null").append(";\n");
            writer.append(indent(shift)).append(valueType).append(" oldValue").append(isPrimitive ? "" : " = null").append(";\n");
            writer.append(indent(shift)).append("boolean isPropertyChanged = false;\n");
        }
        if(threadSafe) {
            writer.append(indent(shift)).append(INSTANCE_LOCK).append(".writeLock().lock();\n")
                  .append(indent(shift++)).append("try {\n");
        }
        if(propertySupport) {
            writer.append(indent(shift)).append("newValue = ").append(retain(property)).append(";\n");
            writer.append(indent(shift)).append("oldValue = this.").append(fieldName).append(";\n");
            writer.append(indent(shift)).append("this.").append(fieldName).append(" = newValue;\n");
        } else {
            writer.append(indent(shift)).append("this.").append(fieldName).append(" = ").append(retain(property)).append(";\n");
        }
        if(propertySupport) {
            writer.append(indent(shift)).append("isPropertyChanged = (oldValue != newValue");
            if(!isPrimitive(property.getType())) {
                writer.append(" && (").append("oldValue != null && !oldValue.equals(newValue))");
            }
            writer.append(");\n");
        }
        if(threadSafe) {
            writer.append(indent(--shift)).append("} finally {\n")
                .append(indent(++shift)).append(INSTANCE_LOCK).append(".writeLock().unlock();\n")
                .append(indent(--shift)).append("}\n");
        }
        //generate code for firing PropertyChangeEvent
        if(propertySupport) {
            writer.append(indent(shift)).append("if (isPropertyChanged) {\n")
                  .append(indent(++shift)).append("this.propertySupport.firePropertyChange(\"").append(property.getName()).append("\", oldValue, newValue);\n")
                  .append(indent(--shift)).append("}\n");
        }
        writer.append(indent(--shift)).append("}\n\n");
        return shift;
    }

    protected final boolean isPackageNameRelative(AnnotationMirror annotation) {
        Elements elementUtils = processingEnv.getElementUtils();
        Map<? extends ExecutableElement, ? extends AnnotationValue> annotationValueMap = elementUtils.getElementValuesWithDefaults(annotation);
        AnnotationValue packageNameRelativeValue = getValueOfAnnotation(annotationValueMap, "packageNameRelative");
        return ((Boolean)packageNameRelativeValue.getValue()).booleanValue();
    }

    protected final String getPackageName(AnnotationMirror annotation) {
        Elements elementUtils = processingEnv.getElementUtils();
        Map<? extends ExecutableElement, ? extends AnnotationValue> annotationValueMap = elementUtils.getElementValuesWithDefaults(annotation);
        AnnotationValue packageNameValue = getValueOfAnnotation(annotationValueMap, "packageName");
        return (String)packageNameValue.getValue();
    }

    protected final boolean isThreadSafe(AnnotationMirror annotation) {
        Elements elementUtils = processingEnv.getElementUtils();
        Map<? extends ExecutableElement, ? extends AnnotationValue> annotationValueMap = elementUtils.getElementValuesWithDefaults(annotation);
        AnnotationValue threadSafeValue = getValueOfAnnotation(annotationValueMap, "threadSafe");
        return ((Boolean)threadSafeValue.getValue()).booleanValue();
    }

    protected final boolean isCloneable(AnnotationMirror annotation) {
        Elements elementUtils = processingEnv.getElementUtils();
        Map<? extends ExecutableElement, ? extends AnnotationValue> annotationValueMap = elementUtils.getElementValuesWithDefaults(annotation);
        AnnotationValue cloneableValue = getValueOfAnnotation(annotationValueMap, "cloneable");
        return ((Boolean)cloneableValue.getValue()).booleanValue();
    }

    protected final boolean isSerializable(AnnotationMirror annotation) {
        Elements elementUtils = processingEnv.getElementUtils();
        Map<? extends ExecutableElement, ? extends AnnotationValue> annotationValueMap = elementUtils.getElementValuesWithDefaults(annotation);
        AnnotationValue serializableValue = getValueOfAnnotation(annotationValueMap, "serializable");
        return ((Boolean)serializableValue.getValue()).booleanValue();
    }

    protected final boolean isHavingSuperClass(AnnotationMirror annotation) {
        Elements elementUtils = processingEnv.getElementUtils();
        return !getSuperClassValue(elementUtils.getElementValuesWithDefaults(annotation)).isEmpty();
    }

    protected final boolean isPropertyChangeEventAware(TypeElement element) {
        Elements elementUtils = processingEnv.getElementUtils();
        Types typeUtils = processingEnv.getTypeUtils();
        TypeElement propertyAware = elementUtils.getTypeElement(PropertyChangeEventAware.class.getName());
        for (TypeMirror intrface : element.getInterfaces()) {
            if(typeUtils.isSubtype(intrface, propertyAware.asType())) {
                return true;
            }
        }
        return false;
    }

    protected final boolean isAbstract(InterfaceDefinition definition) {
        return definition.getMethods().isEmpty() && !definition.isHavingIgnoredProperty();
    }

    protected boolean precheck(InterfaceDefinition definition, AnnotationMirror annotation, String className, Element element) {
        return true;
    }

    protected void generateClassDefinition(Writer writer, AnnotationMirror annotation, InterfaceDefinition definition, String className, Element element) throws IOException {
        Elements elementUtils = processingEnv.getElementUtils();

        String packageName = resolvePackageName(annotation, definition);
        String generationTime = String.format("%1$tFT%1$tH:%1$tM:%1$tS.%1$tL%1$tz", new Date()); //%1$tY%1$tm%1$td-%1$tH%1$tk%1$tS-%1$tN%1$tz

        writer.append("package ").append(packageName).append(";\n\n");
        writer.append("@javax.annotation.Generated(value = \"" + this.getClass().getName() + "\", date = \"" + generationTime + "\")\n");
        writer.append("public ").append(isAbstract(definition) ? "" : "abstract ").append("class ").append(className);

        Map<? extends ExecutableElement, ? extends AnnotationValue> annotationValueMap = elementUtils.getElementValuesWithDefaults(annotation);
        String superClassName = getSuperClassValue(annotationValueMap);
        if(!superClassName.isEmpty()) {
            writer.append(" extends ").append(superClassName);
        }

        writer.append(" implements ").append(definition.getPackage() + "." + definition.getInterfaceName());

        boolean isSerializable = isSerializable(annotation);
        if(isSerializable) {
            writer.append(", java.io.Serializable");
        }
        writer.append(" {\n");

        if(isSerializable) {
            AnnotationValue serialVersionValue = getValueOfAnnotation(annotationValueMap, "serialVersion");
            Long version = (Long)serialVersionValue.getValue();
            writer.append(indent(1)).append("private static final long serialVersionUID = ").append(version.toString()).append("L;\n");
        }
    }

    protected int generateFields(Writer writer, int shift, AnnotationMirror annotation, InterfaceDefinition definition, TypeElement targetInterface, FieldModifier modifier) throws IOException {
        Elements elementUtils = processingEnv.getElementUtils();

        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                shift = generateField(property, writer, shift, modifier);
            }
        }

        if(isPropertyChangeEventAware(targetInterface)) {
            writer.append("\n");
            generatePropertySupportField(elementUtils, writer, shift);
        }


        if(isThreadSafe(annotation)) {
            writer.append("\n");
            writer.append(indent(shift)).append("protected final java.util.concurrent.locks.ReadWriteLock ").append(INSTANCE_LOCK).append(" = new java.util.concurrent.locks.ReentrantReadWriteLock();\n");
        }

        writer.append("\n");
        return shift;
    }

    protected final RetainType getRetainTypeOf(Property property) {
        return RetainType.valueOf(property.getRetainType());
    }

    protected void generatePropertySupportField(Elements elementUtils, Writer writer, int shift) throws IOException {
        TypeMirror supportClass = elementUtils.getTypeElement(PropertyChangeSupport.class.getName()).asType();
        writer.append(indent(shift)).append("protected final ").append(supportClass.toString()).append(" propertySupport;\n");
    }

    protected int generatePropertyAccessors(Writer writer, int shift, AnnotationMirror annotation, InterfaceDefinition definition, TypeElement targetInterface) throws IOException {
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                if(property.isReadable()) {
                    shift = generateGetter(writer, shift, targetInterface, annotation, property);
                }

                if(property.isWritable()) {
                    shift = generateSetter(writer, shift, targetInterface, annotation, property);
                }
            }
        }
        return shift;
    }

    protected int generateConstructors(Writer writer, int shift, AnnotationMirror generateClassAnnotation, InterfaceDefinition definition, String className, TypeElement targetInterface) throws IOException {
        shift = generateFullArgConstructor(writer, shift, className, definition, targetInterface);

        int propertyCount = 0;
        int readablePropertyCount = 0;
        int writablePropertyCount = 0;
        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                if(property.isReadable()) readablePropertyCount++;
                if(property.isWritable()) writablePropertyCount++;
                propertyCount++;
            }
        }

        int readOnlyPropertyCount = readablePropertyCount - writablePropertyCount;
        if(readOnlyPropertyCount != propertyCount) {
            shift = generateReadOnlyFieldConstructor(writer, shift, className, definition, targetInterface);
        }

        return shift;
    }

    private int generatePropertySupport(Writer writer, int shift, Elements elementUtils) throws IOException {
        TypeMirror type = elementUtils.getTypeElement(PropertyChangeSupport.class.getName()).asType();
        writer.append(indent(shift)).append("this.propertySupport = ").append("new ").append(type.toString()).append("(this);\n");
        return shift;
    }

    protected int generatePropertyFieldInitializer(Writer writer, int shift, TypeElement element, Property property) throws IOException {
        writer.append(indent(shift)).append("this.").append(toSafeName(property.getName())).append(" = ").append(retain(property)).append(";\n");
        return shift;
    }

    protected int generateFullArgConstructor(Writer writer, int shift, String className, InterfaceDefinition definition, TypeElement element) throws IOException {
        Elements elementUtils = processingEnv.getElementUtils();

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
                shift = generatePropertyFieldInitializer(writer, shift, element, property);
            }
        }

        if(isPropertyChangeEventAware(element)) {
            shift = generatePropertySupport(writer, shift, elementUtils);
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
    protected int generateReadOnlyFieldConstructor(Writer writer, int shift, String className, InterfaceDefinition definition, TypeElement element) throws IOException {
        Elements elementUtils = processingEnv.getElementUtils();

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
                    shift = generatePropertyFieldInitializer(writer, shift, element, property);
                }
            }
        }

        if(isPropertyChangeEventAware(element)) {
            shift = generatePropertySupport(writer, shift, elementUtils);
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

    protected int generateEquals(Writer writer, int shift, AnnotationMirror annotation, InterfaceDefinition definition, String className, boolean isHavingSuperClass, TypeElement targetInterface) throws IOException {
        boolean threadSafe = isThreadSafe(annotation);

        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift)).append("public boolean equals(Object obj) {\n")
              .append(indent(++shift)).append("if (obj == null) {\n")
              .append(indent(++shift)).append("return false;\n")
              .append(indent(--shift)).append("}\n\n")
              .append(indent(shift)).append("if (!(obj instanceof ").append(className).append(")) {\n")
              .append(indent(++shift)).append("return false;\n")
              .append(indent(--shift)).append("}\n\n")
              .append(indent(shift)).append("final ").append(className).append(" other = (").append(className).append(") obj;\n");

        if(threadSafe) {
            writer.append(indent(shift)).append(INSTANCE_LOCK).append(".readLock().lock();\n")
                  .append(indent(shift++)).append("try {\n");
        }

        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                shift = generateEqualForOneProperty(property, writer, shift);
            }
        }

        if(isHavingSuperClass) {
            writer.append(indent(shift)).append("if (!super.equals(obj)) {\n")
                  .append(indent(++shift)).append("return false;\n")
                  .append(indent(--shift)).append("}\n");
        }

        if(threadSafe) {
            writer.append(indent(--shift)).append("} finally {\n")
                  .append(indent(++shift)).append(INSTANCE_LOCK).append(".readLock().unlock();\n")
                  .append(indent(--shift)).append("}\n");
        }

        writer.append(indent(shift)).append("return true;\n");
        writer.append(indent(--shift)).append("}\n\n");
        return shift;
    }

    protected int generateEqualForOneProperty(Property property, Writer writer, int shift) throws IOException {
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
        return shift;
    }

    protected int generateHashCode(Writer writer, int shift, AnnotationMirror annotation, InterfaceDefinition definition, String className, boolean isHavingSuperClass, TypeElement targetInterface) throws IOException {
        boolean threadSafe = isThreadSafe(annotation);
        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift)).append("public int hashCode() {\n")
              .append(indent(++shift)).append("int result = 17;\n");

        if(threadSafe) {
            writer.append(indent(shift)).append(INSTANCE_LOCK).append(".readLock().lock();\n")
                  .append(indent(shift++)).append("try {\n");
        }

        for (Property property : definition.getProperties()) {
            if(!property.isIgnored()) {
                generateHashCodeForOneProperty(property, writer, shift);
            }
        }

        if(isHavingSuperClass) {
            writer.append(indent(shift)).append("result = 31 * result + ").append("super.hashCode();\n");
        }

        if(threadSafe) {
            writer.append(indent(--shift)).append("} finally {\n")
                  .append(indent(++shift)).append(INSTANCE_LOCK).append(".readLock().unlock();\n")
                  .append(indent(--shift)).append("}\n");
        }

        writer.append(indent(shift)).append("return result;\n");
        writer.append(indent(--shift)).append("}\n\n");

        return shift;
    }

    protected void generateHashCodeForOneProperty(Property property, Writer writer, int shift) throws IOException {
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

    protected int generateToString(Writer writer, int shift, AnnotationMirror annotation, InterfaceDefinition definition, String className, boolean isHavinsSuperClass, TypeElement targetInterface) throws IOException {
        boolean threadSafe = isThreadSafe(annotation);

        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift++)).append("public String toString() {\n");

        if(threadSafe) {
            writer.append(indent(shift)).append(INSTANCE_LOCK).append(".readLock().lock();\n")
                  .append(indent(shift++)).append("try {\n");
        }

        writer.append(indent(shift)).append("return \"").append(className).append("{\"\n");

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

        if(threadSafe) {
            writer.append(indent(--shift)).append("} finally {\n")
                  .append(indent(++shift)).append(INSTANCE_LOCK).append(".readLock().unlock();\n")
                  .append(indent(--shift)).append("}\n");
        }

        writer.append(indent(--shift)).append("}\n\n");

        return shift;
    }

    protected int generateClone(Writer writer, int shift, InterfaceDefinition definition, String className) throws IOException {
        writer.append(indent(shift)).append("@Override\n")
              .append(indent(shift)).append("public ").append(className).append(" clone() {\n")
              .append(indent(++shift)).append("try {\n")
              .append(indent(++shift)).append("return (").append(className).append(") super.clone();\n")
              .append(indent(--shift)).append("} catch(CloneNotSupportedException ex) {\n")
              .append(indent(++shift)).append("throw new IllegalStateException(ex);\n")
              .append(indent(--shift)).append("}\n")
              .append(indent(--shift)).append("}\n\n");
        return shift;
    }

    protected int generatePropertyListenerAccessors(Writer writer, int shift, InterfaceDefinition definition, TypeElement targetInterface) throws IOException {
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

    /**
     * users can override this method for generate other methods.
     * @param writer A writer for outputting code.
     * @param shift A current number of indents.
     * @param annotation An AnnotationMirror of @GenerateClass.
     * @param definition An InterfaceDefinition object which PropertyVisitor created.
     * @param className The className of the currently generated class.
     * @param isHavingSuperClass True if the generated class have a super class.
     * @param targetInterface The TypeElement of the interface which currently is processed by this processor.
     * @return a number of indents.
     * @throws IOException if writer could not write to buffer for any reason.
     */
    protected int generateOthers(Writer writer, int shift, AnnotationMirror annotation, InterfaceDefinition definition, String className, boolean isHavingSuperClass, TypeElement targetInterface) throws IOException {
        return shift;
    }

    protected final AnnotationMirror findAnnotation(Element element, Class<?> annotationClass, Elements elementUtils, Types typeUtils) {
        TypeElement propertyAnnotationType = elementUtils.getTypeElement(annotationClass.getName());
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if(typeUtils.isSameType(propertyAnnotationType.asType(), mirror.getAnnotationType())) {
                return mirror;
            }
        }
        return null;
    }

    protected final String getSuperClassValue(Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {
        Elements elementUtils = processingEnv.getElementUtils();
        Types typeUtils = processingEnv.getTypeUtils();
        AnnotationValue superClassValue = getValueOfAnnotation(elementValues, "superClass");
        assert superClassValue != null;
        TypeMirror superClassMirror = (TypeMirror) superClassValue.getValue();
        TypeMirror voidType = elementUtils.getTypeElement(Void.class.getName()).asType();

        if(typeUtils.isSameType(voidType, superClassMirror)) {
            return "";
        }

        return superClassMirror.toString();
    }


    protected final AnnotationValue getValueOfAnnotation(Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues, String keyName) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : elementValues.entrySet()) {
            ExecutableElement key = entry.getKey();
            if(key.getSimpleName().toString().equals(keyName)) {
                return entry.getValue();
            }
        }
        return null;
    }


    protected final String retain(Property property) {
        return retain(property, "");
    }

    protected final String retain(Property property, String prefix) {
        assert property != null;

        String safeName = prefix + toSafeName(property.getName());
        if(isPrimitive(property.getType())) return safeName;

        RetainType type = RetainType.valueOf(property.getRetainType());
        return type.codeFor(safeName, property);
    }

    protected final String toSafeName(String word) {
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

    protected String resolvePackageName(AnnotationMirror generateClassAnnotation, InterfaceDefinition definition) {
        String interfacePackageName = definition.getPackage();
        String packageName = getPackageName(generateClassAnnotation);
        boolean isRelative = isPackageNameRelative(generateClassAnnotation);

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
