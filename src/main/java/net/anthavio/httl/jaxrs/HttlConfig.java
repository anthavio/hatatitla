package net.anthavio.httl.jaxrs;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.RuntimeType;
import javax.ws.rs.core.Configurable;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Feature;

/**
 * 
 * @author vanek
 *
 */
public class HttlConfig implements Configurable<HttlConfig>, Configuration {

	@Override
	public RuntimeType getRuntimeType() {
		return null;
	}

	@Override
	public Map<String, Object> getProperties() {
		return null;
	}

	@Override
	public Object getProperty(String name) {
		return null;
	}

	@Override
	public Collection<String> getPropertyNames() {
		return null;
	}

	@Override
	public boolean isEnabled(Feature feature) {
		return false;
	}

	@Override
	public boolean isEnabled(Class<? extends Feature> featureClass) {
		return false;
	}

	@Override
	public boolean isRegistered(Object component) {
		return false;
	}

	@Override
	public boolean isRegistered(Class<?> componentClass) {
		return false;
	}

	@Override
	public Map<Class<?>, Integer> getContracts(Class<?> componentClass) {
		return null;
	}

	@Override
	public Set<Class<?>> getClasses() {
		return null;
	}

	@Override
	public Set<Object> getInstances() {
		return null;
	}

	//Configurable interface

	@Override
	public Configuration getConfiguration() {
		return this;
	}

	@Override
	public HttlConfig property(String name, Object value) {
		return this;
	}

	@Override
	public HttlConfig register(Class<?> componentClass) {
		return this;
	}

	@Override
	public HttlConfig register(Class<?> componentClass, int priority) {
		return this;
	}

	@Override
	public HttlConfig register(Class<?> componentClass, Class<?>... contracts) {
		return this;
	}

	@Override
	public HttlConfig register(Class<?> componentClass, Map<Class<?>, Integer> contracts) {
		return this;
	}

	@Override
	public HttlConfig register(Object component) {
		return this;
	}

	@Override
	public HttlConfig register(Object component, int priority) {
		return this;
	}

	@Override
	public HttlConfig register(Object component, Class<?>... contracts) {
		return this;
	}

	@Override
	public HttlConfig register(Object component, Map<Class<?>, Integer> contracts) {
		return this;
	}

}
