/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.leader;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;


/*
 * Periodic timeout.
 * When it hits, the LeaderElector updates its current leader.
 */
public class UpdateLeaderTimeout extends Timeout {
    UpdateLeaderTimeout(SchedulePeriodicTimeout st) {
        super(st);
    }
    
    UpdateLeaderTimeout(ScheduleTimeout st) {
        super(st);
    }
}
