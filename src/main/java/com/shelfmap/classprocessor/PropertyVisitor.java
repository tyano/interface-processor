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
package com.shelfmap.classprocessor;

import com.shelfmap.classprocessor.impl.DefaultField;
import com.shelfmap.classprocessor.impl.DefaultProperty;
import static com.shelfmap.classprocessor.util.Strings.*;

import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;


import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner6;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 *
 * @author Tsutomu YANO
 */
public class PropertyVisitor extends ElementScanner6<Void, Environment> {

    private final InterfaceFilter interfaceFilter;

    public PropertyVisitor(InterfaceFilter interfaceFilter, Void defaultValue) {
        super(defaultValue);
        this.interfaceFilter = interfaceFilter;
    }

    public PropertyVisitor(InterfaceFilter interfaceFilter) {
        this.interfaceFilter = interfaceFilter;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public Void visitType(TypeElement element, Environment env) {
        if(element.getKind() == ElementKind.INTERFACE || element.getKind() == ElementKind.CLASS) {
            ClassDefinition definition = env.getClassDefinition();
            
            switch(element.getKind()) {
                case INTERFACE:
                    definition.setElementType(ElementType.INTERFACE);
                    break;
                case CLASS:
                    definition.setElementType(ElementType.CLASS);
                    break;
            }
            
            //if the element have some super-interfaces, visit them at first.
            for (TypeMirror superType : element.getInterfaces()) {
                Element superInterface = env.getProcessingEnvironment().getTypeUtils().asElement(superType);
                env.setLevel(env.getLevel() + 1);
                if(interfaceFilter.canHandle(superType)) {
                    this.visit(superInterface, env);
                }
                env.setLevel(env.getLevel() - 1);
            }

            if(env.getLevel() == 0) {
                String[] splited = splitPackageName(element.getQualifiedName().toString());
                if(splited == null) {
                    throw new IllegalStateException("the qualified name of the element " + element.toString() + " was a null or an empty string.");
                }
                definition.setPackage(splited[0]);
                definition.setClassName(splited[1]);
                definition.addTypeParameters(element.getTypeParameters().toArray(new TypeParameterElement[0]));
            }
        }
        return super.visitType(element, env);
    }

    private String[] splitPackageName(String value) {
        if(value == null) return null;
        if(value.isEmpty()) return null;
        int lastIndexOfDot = value.lastIndexOf('.');
        if(lastIndexOfDot < 0) return new String[]{"", value};
        return new String[]{ value.substring(0, lastIndexOfDot), value.substring(lastIndexOfDot + 1, value.length()) };
    }

    @Override
    public Void visitVariable(VariableElement e, Environment env) {
        Types typeUtil = env.getProcessingEnvironment().getTypeUtils();
        
        ClassDefinition definition = env.getClassDefinition();
        Field field = buildFieldFromVariableElement(e, env);
        
        if(field != null) {
            definition.addFields(typeUtil, field);
        }
        
        return super.visitVariable(e, env);
    }

    @Override
    public Void visitExecutable(ExecutableElement ee, Environment env) {
        //handle only methods.
        if(ee.getKind() != ElementKind.METHOD) return super.visitExecutable(ee, env);

        Types typeUtil = env.getProcessingEnvironment().getTypeUtils();

//        //this visitor handle methods only in interface.
//        Element enclosing = ee.getEnclosingElement();
//        if(enclosing == null || enclosing.getKind() != ElementKind.INTERFACE) return super.visitExecutable(ee, env);
        ClassDefinition definition = env.getClassDefinition();
        Property property = buildPropertyFromExecutableElement(ee, env);

        //if the creation of a property object success, now the ee is a variation of a property (readable or writable)
        if(property != null) {
            Property prev = definition.findProperty(property.getName(), property.getType(), typeUtil);

            //if a property having same name and same type is already added to InterfaceDifinition,
            //we merge their readable and writable attributes into the previously added object.
            if(prev != null) {
                mergeProperty(prev, property);
            } else {
                //new property. simplly add it into InterfaceDefinition.
                definition.addProperties(typeUtil, property);
            }
        } else {
            //found a method which is not a part of a property.
            definition.addMethods(ee);
        }
        return super.visitExecutable(ee, env);
    }

    private void mergeProperty(Property p1, Property p2) {
        if(p2.isReadable()) {
            p1.setReader(p2.getReader());
        }

        if(p2.isWritable()) {
            p1.setWriter(p2.getWriter());
        }
    }
    
    protected Field buildFieldFromVariableElement(VariableElement ve, Environment env) {
        Field field = null;
        if(ve.getKind() == ElementKind.FIELD) {
            ProcessingEnvironment p = env.getProcessingEnvironment();
            Types typeUtil = p.getTypeUtils();
            Elements elementUtil = p.getElementUtils();

            String name = ve.getSimpleName().toString();
            TypeMirror fieldType = ve.asType();
            field = new DefaultField(name, fieldType);

            List<? extends AnnotationMirror> annotations = ve.getAnnotationMirrors();
            for (AnnotationMirror annotation : annotations) {
                TypeElement fieldAnnotationType = elementUtil.getTypeElement(com.shelfmap.classprocessor.annotation.Field.class.getName());
                if(typeUtil.isSameType(fieldAnnotationType.asType(), annotation.getAnnotationType())) {

                    Map<? extends ExecutableElement, ? extends AnnotationValue> valueMap = elementUtil.getElementValuesWithDefaults(annotation);
                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : valueMap.entrySet()) {
                        ExecutableElement key = entry.getKey();
                        AnnotationValue value = entry.getValue();
                        if(key.getSimpleName().toString().equals("retainType")) {
                            //the return value of the method 'retainType' is an enum value RetainType.
                            //but in annotation processor, it is only manageable as a VariableElement, and we can get the name of the enum object from the VariableElement.
                            //we can resolve the correct enum-value from the String value through the RetainType.valueOf(String) method.
                            VariableElement type = (VariableElement) value.getValue();
                            field.setRetainType(type.getSimpleName().toString());
                        } else if(key.getSimpleName().toString().equals("realType")) {
                            //the return value of the method 'realType' is a Class<?> object.
                            //but in annotation-processing time, all Class<?> is expressed as TypeMirror.
                            //TypeMirror object contains all information about the Class<?> in source-code level,
                            //so we can retrieve full-class-name from it when it is needed.
                            TypeMirror type = (TypeMirror) value.getValue();
                            field.setRealType(type);
                        } else if(key.getSimpleName().toString().equals("ignore")) {
                            Boolean ignore = (Boolean) value.getValue();
                            field.setIgnored(ignore.booleanValue());
                        } else if(key.getSimpleName().toString().equals("readOnly")) {
                            Boolean readOnly = (Boolean) value.getValue();
                            field.setReadOnly(readOnly.booleanValue());
                        } else if(key.getSimpleName().toString().equals("methodModifier")) {
                            VariableElement type = (VariableElement) value.getValue();
                            field.setMethodModifier(type.getSimpleName().toString());
                        }
                    }
                }
            }
        }
        return field;
    }

    protected Property buildPropertyFromExecutableElement(ExecutableElement ee, Environment env) {
        Property property = null;
        if(ee.getKind() == ElementKind.METHOD) {
            ProcessingEnvironment p = env.getProcessingEnvironment();
            int level = env.getLevel();
            String name = ee.getSimpleName().toString();

            Types typeUtil = p.getTypeUtils();
            Elements elementUtil = p.getElementUtils();

            boolean isDefined = level == 0;

            if(name.startsWith("get") || name.startsWith("set") || name.startsWith("is")) {
                if(name.startsWith("get")) {
                    property = new DefaultProperty(uncapitalize(name.substring(3)), ee.getReturnType(), isDefined, ee, null);
                } else if(name.startsWith("set")) {
                    if(ee.getParameters().size() == 1) {
                        property = new DefaultProperty(uncapitalize(name.substring(3)), ee.getParameters().get(0).asType(), isDefined, null, ee);
                    }
                } else if(name.startsWith("is")) {
                    PrimitiveType bool = typeUtil.getPrimitiveType(TypeKind.BOOLEAN);
                    if(typeUtil.isSameType(ee.getReturnType(), bool) ||
                       typeUtil.isSameType(ee.getReturnType(), typeUtil.boxedClass(bool).asType())) {

                        property =  new DefaultProperty(uncapitalize(name.substring(2)), ee.getReturnType(), isDefined, ee, null);
                    }
                }

                if(property != null) {
                    //if a method have a @Property annotation,
                    //then we record the value of the @Property annotation into a Property object.
                    List<? extends AnnotationMirror> annotations = ee.getAnnotationMirrors();
                    for (AnnotationMirror annotation : annotations) {

                        //is the annotationMirror same with "com.shelfmap.simplequery.annotation.Property" ?
                        TypeElement propertyAnnotationType = elementUtil.getTypeElement(com.shelfmap.classprocessor.annotation.Property.class.getName());
                        if(typeUtil.isSameType(propertyAnnotationType.asType(), annotation.getAnnotationType())) {

                            //check all values through the found annotation
                            //and record the values into a Property instance.
                            //we can not use our loving useful Class<?> object here, because this program run in compile-time (no classloader for loading them!).
                            //so we must record the value as TypeMirror or a simple String object.
                            Map<? extends ExecutableElement, ? extends AnnotationValue> valueMap = elementUtil.getElementValuesWithDefaults(annotation);
                            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : valueMap.entrySet()) {
                                ExecutableElement key = entry.getKey();
                                AnnotationValue value = entry.getValue();
                                if(key.getSimpleName().toString().equals("retainType")) {
                                    //the return value of the method 'retainType' is an enum value RetainType.
                                    //but it is manageable as only a VariableElement, and we can get the name of the enum object from the VariableElement.
                                    //we can resolve the correct enum-value from the String value through the RetainType.valueOf(String) method.
                                    VariableElement type = (VariableElement) value.getValue();
                                    property.setRetainType(type.getSimpleName().toString());
                                } else if(key.getSimpleName().toString().equals("realType")) {
                                    //the return value of the method 'realType' is a Class<?> object.
                                    //but in annotation-processing time, all Class<?> is expressed as TypeMirror.
                                    //TypeMirror object contains all information about the Class<?> in source-code level,
                                    //so we can retrieve full-class-name from it when it is needed.
                                    TypeMirror type = (TypeMirror) value.getValue();
                                    property.setRealType(type);
                                } else if(key.getSimpleName().toString().equals("ignore")) {
                                    Boolean ignore = (Boolean) value.getValue();
                                    property.setIgnored(ignore.booleanValue());
                                }
                            }
                        }
                    }
                }
            }
        }
        return property;
    }


}
