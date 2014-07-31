package net.anthavio.httl.api;

import java.lang.reflect.Field;

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

	public void set(T value, String name, HttlRequestBuilder<?> builder);

	public static class ComplexMetaVarSetter implements VarSetter<Object> {

		FieldApiVarMeta[] metaList;

		public ComplexMetaVarSetter(FieldApiVarMeta[] metaList) {
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
				ApiVarMeta metaVar = fieldMeta.meta;
				if (fvalue == null) {
					if (metaVar.killnull) {
						throw new HttlRequestException("Complex argument's field '" + fieldMeta.field.getType().getName() + " "
								+ metaVar.name + "' on " + value.getClass().getName() + " is null");
					} else if (metaVar.nullval != null) {
						fvalue = metaVar.nullval; //risky - different Class...?
					}
				}

				if (metaVar.setter != null) {
					//custom setter works it's own way
					metaVar.setter.set(fvalue, metaVar.name, builder);
				} else {
					//XXX goes directly as param ignoring metaVar.target - allow headers and others too ???
					builder.param(fieldMeta.meta.name, fvalue);
				}
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
