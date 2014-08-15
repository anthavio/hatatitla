package net.anthavio.httl.api;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.anthavio.httl.HttlRequestBuilders.HttlRequestBuilder;
import net.anthavio.httl.HttlRequestException;
import net.anthavio.httl.api.HttlApiBuilder.ApiVarMeta;

/**
 * Optional 'setter' field of @RestVar annotation must implement this interface.
 * 
 * Usage example:
 * 
 * @RestCall("GET /paging")
 * String paging(@RestVar(value = "page", setter = PageableSetter.class) Pageable pager);
 * 		
 * @author martin.vanek
 *
 */
public interface VarSetter<T> {

	/**
	 * Pass value into builder as you want...
	 */
	public void set(T value, String name, HttlRequestBuilder<?> builder);

	public static final MapVarSetter MapVarSetter = new MapVarSetter();

	/**
	 * Built-in Map API parameter VarSetter
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class MapVarSetter implements VarSetter<Map<Object, Object>> {

		@Override
		public void set(Map<Object, Object> value, String name, HttlRequestBuilder<?> builder) {
			if (value != null) {
				Set<Entry<Object, Object>> entrySet = value.entrySet();
				for (Entry<Object, Object> entry : entrySet) {
					if (entry.getKey() != null && entry.getValue() != null) {
						if (name != null) {
							name = name + entry.getKey();
						} else {
							name = String.valueOf(entry.getKey());
						}
						Object valuex = entry.getValue();
						if (valuex instanceof Collection) {
							builder.param(name, (Collection) valuex);
						} else if (valuex.getClass().isArray()) {
							if (valuex instanceof Object[]) {
								builder.param(name, (Object[]) valuex); //Array of String[] ???	
							} else {
								//primitive array of some sort
								int length = Array.getLength(valuex);
								for (int i = 0; i < length; ++i) {
									builder.param(name, Array.get(valuex, i));
								}
							}

						} else if (valuex instanceof Date) {
							builder.param(name, (Date) valuex);
						} else {
							builder.param(name, valuex);
						}
					}
				}
			}
		}
	}

	/**
	 * 
	 * For Custom Beans API parameter - copy bean fields into request
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class BeanMetaVarSetter implements VarSetter<Object> {

		Class<?> beanClass;

		FieldApiVarMeta[] metaList;

		public BeanMetaVarSetter(Class<?> beanClass, FieldApiVarMeta[] metaList) {
			this.beanClass = beanClass;
			this.metaList = metaList;
			for (FieldApiVarMeta fieldMeta : metaList) {
				if (!fieldMeta.field.isAccessible()) {
					fieldMeta.field.setAccessible(true);
				}
			}
			/*
			metaMap = new HashMap<String, FieldApiVarMeta>();
			for (FieldApiVarMeta meta : metaList) {
				metaMap.put(meta.meta.name, meta);
			}
			*/
		}

		@Override
		public void set(Object value, String name, HttlRequestBuilder<?> builder) {
			if (value == null) {
				return; // skip as killnull check is already done so this null is legal
			}
			for (FieldApiVarMeta fieldMeta : metaList) {
				Object fvalue;
				try {
					fvalue = fieldMeta.field.get(value);
				} catch (Exception x) {
					throw new HttlApiException("Cannot get " + fieldMeta.field + " value ", x);
				}
				setField(fvalue, fieldMeta, builder);

			}
		}

		protected void setField(Object fieldValue, FieldApiVarMeta fieldMeta, HttlRequestBuilder<?> builder) {
			ApiVarMeta metaVar = fieldMeta.meta;
			if (fieldValue == null) {
				if (metaVar.killnull) {
					throw new HttlRequestException("Complex argument's field '" + fieldMeta.field.getType().getName() + " "
							+ metaVar.name + "' on " + beanClass.getName() + " is null");
				} else if (metaVar.nullval != null) {
					fieldValue = metaVar.nullval; //risky - different Class...?
				}
			}

			if (metaVar.setter != null) {
				//custom setter works it's own way
				metaVar.setter.set(fieldValue, metaVar.name, builder);
			} else {
				//set as query parameter ignoring metaVar.target
				//It would be messy to try set headers or 
				builder.param(fieldMeta.meta.name, fieldValue);
			}
		}
	}

	public static class FieldApiVarMeta {

		protected Field field;

		protected ApiVarMeta meta;

		public FieldApiVarMeta(Field field, ApiVarMeta meta) {
			this.field = field;
			this.meta = meta;
		}

	}
}
