package com.shelfmap.interfaceprocessor;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;

/**
 * An subclass of {@link InterfaceProcessor}.
 * <p>
 * This class uses {@link RemoveInterfacePrefixClassNameResolver} as the default implementation of
 * {@link ClassNameResolver}.
 * 
 * @author Tsutomu YANO
 */
@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedAnnotationTypes({"com.shelfmap.interfaceprocessor.annotation.GenerateClass"})
public class RemovePrefixInterfaceProcessor extends InterfaceProcessor {

    public RemovePrefixInterfaceProcessor() {
        super();
    }

    @Override
    protected ClassNameResolver getDefaultImplementationOfClassNameResolver() {
        return new RemovePrefixClassNameResolver();
    }
}
