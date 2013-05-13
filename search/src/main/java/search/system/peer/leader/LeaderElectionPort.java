package search.system.peer.leader;

import se.sics.kompics.Negative;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.web.Web;
import tman.system.peer.tman.TManSample;

/**
 *
 * @author alban
 */
public class LeaderElectionPort extends PortType {{
    negative(TManSample.class);
    negative(LeaderRequest.class);
    positive(LeaderResponse.class);
}}
