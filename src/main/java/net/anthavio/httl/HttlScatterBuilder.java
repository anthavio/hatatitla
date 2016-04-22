package net.anthavio.httl;

import net.anthavio.httl.HttlSender;
import net.anthavio.httl.HttlTransport;
import net.anthavio.httl.SenderConfigurer;
import net.anthavio.httl.TransportBuilder.BaseTransportBuilder;

/**
 * 
 * @author mvanek
 *
 */
public class HttlScatterBuilder {

    private BaseTransportBuilder<?> transportBuilder;

    private String[] hostNames;

    private int port;

    private int perHostPoolSize;

    public HttlScatterBuilder(BaseTransportBuilder<?> transportBuilder) {
        this.transportBuilder = transportBuilder;
    }

    public HttlScatterSender build() {
        HttlSender[] senders = new HttlSender[hostNames.length];
        for (int i = 0; i < hostNames.length; ++i) {
            if (perHostPoolSize != 0) {
                transportBuilder.setPoolMaximumSize(perHostPoolSize);
            }
            transportBuilder.setUrl(hostNames[i]);
            HttlTransport transport = transportBuilder.build();
            SenderConfigurer config = new SenderConfigurer(transport);
            senders[i] = new HttlSender(config);
        }
        return new HttlScatterSender(senders);
    }
}
