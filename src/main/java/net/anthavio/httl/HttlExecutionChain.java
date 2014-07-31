package net.anthavio.httl;

import java.io.IOException;
import java.util.List;

/**
 * 
 * @author martin.vanek
 *
 */
public interface HttlExecutionChain {

	public HttlResponse next(HttlRequest request) throws IOException;

	/**
	 * Standard HttpSender backed implementation with List of ExecutionInterceptors
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class SenderExecutionChain implements HttlExecutionChain {

		private final List<HttlExecutionInterceptor> interceptors;

		private final HttlSender sender;

		private int index = 0;

		public SenderExecutionChain(List<HttlExecutionInterceptor> interceptors, HttlSender sender) {
			this.interceptors = interceptors;
			this.sender = sender;
		}

		public HttlResponse next(HttlRequest request) throws IOException {
			if (index < interceptors.size()) {
				return interceptors.get(index++).intercept(request, this);
			} else {
				return sender.doExecute(request); //last
			}

		}

	}

}
