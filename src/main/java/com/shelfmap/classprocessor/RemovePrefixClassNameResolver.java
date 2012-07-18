package com.shelfmap.classprocessor;

/**
 * An implementation of ClassNameResolver, which remove a prefix 'I' from the name of a interface and the
 * uses the remaining name as the name of a Class.
 * @author Tsutomu YANO
 */
public class RemovePrefixClassNameResolver extends DefaultClassNameResolver implements ClassNameResolver {

    @Override
    public String classNameFor(String interfaceName) {
        parameterCheck(interfaceName);
        String remains = interfaceName.substring(1);
        return super.classNameFor(remains);
    }

    @Override
    public String abstractClassNameFor(String interfaceName) {
        parameterCheck(interfaceName);
        String remains = interfaceName.substring(1);
        return super.abstractClassNameFor(remains);
    }

    private void parameterCheck(String interfaceName) {
        if(interfaceName == null) throw new IllegalArgumentException("'interfaceName' should not be null.");
        if(interfaceName.isEmpty()) throw new IllegalArgumentException("'interfaceName' should not be an empty string.");
        if(interfaceName.length() < 2) throw new IllegalArgumentException("the number of chars of 'interfaceName' should be longer than 2, and it should begin with 'I'.");
        if(!interfaceName.startsWith("I")) {
            throw new IllegalArgumentException("The first charactor of 'interfaceName' should be 'I'.");
        }    
    }

    @Override
    protected String getClassNamePrefix() { return ""; }

    @Override
    protected String getClassNameSuffix() { return ""; }

    @Override
    protected String getAbstractClassNamePrefix() { return "Abstract"; }

    @Override
    protected String getAbstractClassNameSuffix() { return ""; }
}
