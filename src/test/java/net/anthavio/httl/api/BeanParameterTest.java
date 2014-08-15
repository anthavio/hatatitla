package net.anthavio.httl.api;

import net.anthavio.httl.HttlRequestException;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender.Parameters;
import net.anthavio.httl.util.MockSenderConfig;

import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class BeanParameterTest {

	@Test
	public void prefixedNames() {
		// When - api with nameless @RestVar
		BeanParamApi api = HttlApiBuilder.with(new MockSenderConfig().build()).build(BeanParamApi.class);
		HttlResponse response1 = api.nothing(new BeanParam("Guido", 5));

		// Then - parameter names should be taken from field names
		Parameters parameters1 = response1.getRequest().getParameters();
		Assertions.assertThat(parameters1.getFirst("name")).isEqualTo("Guido");
		Assertions.assertThat(parameters1.getFirst("number")).isEqualTo("5");

		//When - with prefix
		HttlResponse response2 = api.prefix(new BeanParam("Vaclav", 10));

		//Then - with prefix
		Parameters parameters2 = response2.getRequest().getParameters();
		Assertions.assertThat(parameters2.getFirst("prefix.name")).isEqualTo("Vaclav");
		Assertions.assertThat(parameters2.getFirst("prefix.number")).isEqualTo("10");
	}

	@Test
	public void complexParamNullValuesIllegal() {

		//Given - api with notnull checks
		BeanParamApi api = HttlApiBuilder.with(new MockSenderConfig().build()).build(BeanParamApi.class);

		// When - null parameter
		try {
			api.nothing(null);
			// Then
			Assertions.fail("Expected " + HttlRequestException.class.getName());
		} catch (HttlRequestException rx) {
			Assertions.assertThat(rx.getMessage()).contains("Complex argument on position 1 is null");
		}

		try {
			// When - null field
			api.nothing(new BeanParam(null, 1));
			// Then
			Assertions.fail("Expected " + HttlRequestException.class.getName());
		} catch (HttlRequestException rx) {
			Assertions.assertThat(rx.getMessage()).startsWith("Complex argument's field 'java.lang.String name' on ");
			Assertions.assertThat(rx.getMessage()).endsWith("is null");
		}
	}

	static interface BeanParamApi {

		@RestCall("GET /")
		public HttlResponse nothing(@RestVar(required = true) BeanParam param);

		@RestCall("GET /")
		public HttlResponse prefix(@RestVar("prefix.") BeanParam param);
	}

	@RestVar
	static class BeanParam {

		@RestVar(required = true)
		private String name;

		@RestVar
		private int number;

		public BeanParam(String name, int number) {
			this.name = name;
			this.number = number;
		}

	}

	@Test
	public void beanParamNamed() {

		// Given - api with named @RestVar
		BeanParamNamedApi api = HttlApiBuilder.with(new MockSenderConfig().build()).build(BeanParamNamedApi.class);

		// When
		HttlResponse response1 = api.nothing(new BeanParamNamed("Guido", 5));

		// Then
		Parameters parameters1 = response1.getRequest().getParameters();
		Assertions.assertThat(parameters1.getFirst("ty-pe.na-me")).isEqualTo("Guido");
		Assertions.assertThat(parameters1.getFirst("ty-pe.num-ber")).isEqualTo("5");

		//When - with prefix
		HttlResponse response2 = api.prefix(new BeanParamNamed("Vaclav", 10));

		//Then - with prefix
		Parameters parameters2 = response2.getRequest().getParameters();
		Assertions.assertThat(parameters2.getFirst("pa-ram.ty-pe.na-me")).isEqualTo("Vaclav");
		Assertions.assertThat(parameters2.getFirst("pa-ram.ty-pe.num-ber")).isEqualTo("10");

	}

	@Test
	public void beanParamLegalNulls() {

		//Given - api without notnull checks
		BeanParamNamedApi api = HttlApiBuilder.with(new MockSenderConfig().build()).build(BeanParamNamedApi.class);

		//When - legal null parameter
		HttlResponse response1 = api.prefix(null);
		//Then - zero params
		Parameters parameters1 = response1.getRequest().getParameters();
		Assertions.assertThat(parameters1.size()).isEqualTo(0);

		//When - legal null field
		HttlResponse response2 = api.prefix(new BeanParamNamed(null, 33));
		//Then - null param is skipped
		Parameters parameters2 = response2.getRequest().getParameters();
		Assertions.assertThat(parameters2.size()).isEqualTo(1);
		Assertions.assertThat(parameters2.getFirst("pa-ram.ty-pe.na-me")).isNull();
		Assertions.assertThat(parameters2.getFirst("pa-ram.ty-pe.num-ber")).isEqualTo("33");

	}

	static interface BeanParamNamedApi {

		@RestCall("GET /")
		public HttlResponse nothing(@RestVar BeanParamNamed param);

		@RestCall("GET /")
		public HttlResponse prefix(@RestVar("pa-ram.") BeanParamNamed param);
	}

	@RestVar("ty-pe.")
	static class BeanParamNamed {

		@RestVar("na-me")
		private String name;

		@RestVar("num-ber")
		private int number;

		public BeanParamNamed(String name, int number) {
			this.name = name;
			this.number = number;
		}

	}
}