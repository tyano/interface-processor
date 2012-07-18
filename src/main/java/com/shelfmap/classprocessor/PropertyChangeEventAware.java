package com.shelfmap.classprocessor;

import java.beans.PropertyChangeListener;
import java.util.Collection;

/**
 * A interface which extends this interface is handled as supporting
 * PropertyChangeEvent. the InterfaceProcessor will generate setters which
 * fire PropertyChangeEvent when a value is set to the property, and
 * methods for appending EventListeners.
 *
 * @author Tsutomu YANO
 */
public interface PropertyChangeEventAware {
    void addPropertyChangeListener(PropertyChangeListener... listeners);
    void addPropertyChangeListener(String propertyName, PropertyChangeListener... listeners);
    Collection<PropertyChangeListener> getPropertyChangeListeners();
    Collection<PropertyChangeListener> getPropertyChangeListeners(String propertyName);
    boolean hasListeners(String propertyName);
    void removePropertyChangeListener(PropertyChangeListener... listeners);
    void removePropertyChangeListener(String propertyName, PropertyChangeListener... listeners);
}
