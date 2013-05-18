package search.system.peer.leader;

import se.sics.kompics.PortType;
import tman.system.peer.tman.TManSample;

/**
 * Interface of the LeaderElector component.
 * It is able to receive TMan samples and leader requests ; it can answer those and send election/abdication notifications.
 * @author alban
 */
public class LeaderElectionPort extends PortType {{
    negative(TManSample.class);
    negative(LeaderRequest.class);
    positive(LeaderResponse.class);
    positive(LeaderElectionNotify.class);
}}
