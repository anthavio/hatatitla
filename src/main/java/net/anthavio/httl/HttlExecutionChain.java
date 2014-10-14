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
	 * Standard HttlSender backed implementation with List of HttlExecutionFilters
	 * 
	 * @author martin.vanek
	 *
	 */
	public static class SenderExecutionChain implements HttlExecutionChain {

		private final List<HttlExecutionFilter> fiters;

		private final HttlSender sender;

		private int index = 0;

		public SenderExecutionChain(List<HttlExecutionFilter> fiters, HttlSender sender) {
			this.fiters = fiters;
			this.sender = sender;
		}

		public HttlResponse next(HttlRequest request) throws IOException {
			if (index < fiters.size()) {
				return fiters.get(index++).filter(request, this);
			} else {
				return sender.doExecute(request); //last
			}

		}

	}

}
